package com.descope.android.widget

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.descope.Descope
import com.descope.android.bridge.BridgeRequest
import com.descope.android.bridge.BridgeResponse
import com.descope.android.bridge.Coordinator
import com.descope.android.bridge.DescopeBridgeHook.Event
import com.descope.android.bridge.WebViewCoordinator
import com.descope.android.widget.DescopeUserProfileWidgetView.NavigationStrategy.DoNothing
import com.descope.android.widget.DescopeUserProfileWidgetView.NavigationStrategy.Inline
import com.descope.android.widget.DescopeUserProfileWidgetView.NavigationStrategy.OpenBrowser
import com.descope.android.widget.DescopeUserProfileWidgetView.State.Failed
import com.descope.android.widget.DescopeUserProfileWidgetView.State.Initial
import com.descope.android.widget.DescopeUserProfileWidgetView.State.Ready
import com.descope.android.widget.DescopeUserProfileWidgetView.State.Started
import com.descope.internal.others.activityHelper
import com.descope.internal.others.debug
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.internal.others.toJsonObject
import com.descope.internal.others.with
import com.descope.internal.routes.getPackageOrigin
import com.descope.internal.routes.isWebAuthnSupported
import com.descope.sdk.DescopeLogger
import com.descope.sdk.DescopeSdk
import com.descope.session.DescopeSession
import com.descope.types.DescopeException
import com.descope.types.RevokeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.timer

internal class DescopeUserProfileWidgetCoordinator(override val webView: WebView) : Coordinator {

    internal var listener: DescopeUserProfileWidgetView.Listener? = null
    internal var state: DescopeUserProfileWidgetView.State = Initial

    private val bridge = WidgetBridge(webView)
    private val common = WebViewCoordinator(webView)
    private var widget: DescopeUserProfileWidget? = null
    // (variant, handle) — at most one component awaiting a deep-link response at a time
    private var pendingDeepLink: Pair<String, String>? = null
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val sdk: DescopeSdk?
        get() = widget?.sdk ?: if (Descope.isInitialized) Descope.sdk else null
    private val logger: DescopeLogger?
        get() = sdk?.client?.config?.logger
    private val context: Context
        get() = webView.context
    private val currentSession: DescopeSession?
        get() = if (widget?.sessionProvider != null) widget?.sessionProvider?.invoke()
        else sdk?.sessionManager?.session?.takeIf { !it.refreshToken.isExpired }

    private val bridgeListener = object : WidgetBridge.Listener {
        override fun onLoaded() = handleLoaded()
        override fun onRegisterWidget() = handleRegisterWidget()
        override fun onRegisterComponent(handle: String, meta: JSONObject) = handleRegisterComponent(handle, meta)
        override fun onUnregisterComponent(handle: String) = handleUnregisterComponent(handle)
        override fun onWidgetReady() = handleWidgetReady()
        override fun onWidgetError(error: DescopeException) = handleError(error)
        override fun onRequestLogout() = handleRequestLogout()
        override fun onRequest(request: BridgeRequest) = handleRequest(request)
        override fun onNavigation(uri: Uri): Boolean = handleNavigation(uri)
        override fun onComponentError(handle: String?, error: DescopeException) = handleComponentError(handle, error)
    }

    init {
        bridge.listener = bridgeListener
    }

    // Public API

    override fun runJavaScript(code: String) = bridge.runJavaScript(code)

    override fun addStyles(css: String) = bridge.addStyles(css)

    // Internal API

    internal fun startWidget(widget: DescopeUserProfileWidget) {
        this.widget = widget
        bridge.widget = widget
        bridge.logger = logger
        common.logger = logger
        common.customTabsIntent = widget.presentation?.createCustomTabsIntent(context)
        if (currentSession == null) {
            logger.error("Cannot start widget without an authenticated session")
            state = Failed
            handler.post {
                listener?.onError(DescopeException.widgetAuthenticationRequired)
            }
            return
        }
        sdk?.resume = createResumeClosure(WeakReference(this))
        handleStarted()
        bridge.start()
    }

    internal fun resumeFromDeepLink(deepLink: Uri): Boolean {
        if (state != Started && state != Ready) {
            logger.debug("Ignoring resume URL", state)
            return false
        }
        activityHelper.closeCustomTab(webView.context)
        // magic links don't go through the bridge as a request, so detect them from the URL itself
        val type = when {
            deepLink.queryParameterNames.contains("t") -> "magicLink"
            else -> pendingDeepLink?.first ?: "oauthWeb"
        }
        // magic links aren't pre-tracked (JS doesn't emit a bridge event for them); route to the most
        // recently registered component, which under UPW's one-modal-at-a-time usage is the one waiting.
        val handle = when (type) {
            "magicLink" -> bridge.componentAttributes.keys.lastOrNull()
            else -> pendingDeepLink?.takeIf { it.first == type }?.second
        }
        pendingDeepLink = null
        if (handle == null) {
            logger.error("Received deep link but no component is waiting", type)
            return false
        }
        sendResponse(handle, BridgeResponse.DeepLink(type = type, url = deepLink.toString()))
        return true
    }

    // Bridge Events

    private fun handleRegisterWidget() {
        val refreshJwt = currentSession?.refreshJwt
        if (refreshJwt.isNullOrEmpty()) {
            logger.error("Cannot release widget without an active session")
            handleError(DescopeException.widgetAuthenticationRequired)
            return
        }
        bridge.seedAndReleaseWidget(refreshJwt)
    }

    private fun handleNavigation(uri: Uri): Boolean {
        return when (listener?.onNavigation(uri) ?: OpenBrowser) {
            Inline -> false
            DoNothing -> true
            OpenBrowser -> {
                common.openInBrowser(uri)
                true
            }
        }
    }

    // Per-component lifecycle

    private fun handleRegisterComponent(handle: String, meta: JSONObject) {
        val widget = widget ?: return
        logger.info("Initializing widget component", handle, meta)

        val useCustomSchemeFallback = common.shouldUseCustomSchemeUrl()

        val origin = try {
            if (isWebAuthnSupported) getPackageOrigin(context) else ""
        } catch (_: Exception) {
            ""
        }

        val refreshJwt = currentSession?.refreshJwt ?: ""
        val oauthProvider = widget.oauthNativeProvider?.name ?: ""
        val oauthRedirect = common.pickRedirectUrl(widget.oauthRedirect, widget.oauthRedirectCustomScheme, useCustomSchemeFallback)
        val ssoRedirect = common.pickRedirectUrl(widget.ssoRedirect, widget.ssoRedirectCustomScheme, useCustomSchemeFallback)
        val externalAuthRedirect = common.pickRedirectUrl(widget.externalAuthRedirect, widget.externalAuthRedirectCustomScheme, useCustomSchemeFallback)
        val magicLinkRedirect = widget.magicLinkRedirect ?: ""

        val nativeOptions = JSONObject().apply {
            put("platform", "android")
            put("bridgeVersion", WIDGET_BRIDGE_VERSION)
            put("oauthProvider", oauthProvider)
            put("oauthRedirect", oauthRedirect)
            put("ssoRedirect", ssoRedirect)
            put("externalAuthRedirect", externalAuthRedirect)
            put("magicLinkRedirect", magicLinkRedirect)
            put("origin", origin)
        }

        var clientInputs = ""
        if (widget.clientInputs.isNotEmpty()) {
            clientInputs = widget.clientInputs.toJsonObject().toString()
        }

        bridge.initializeComponent(handle, nativeOptions.toString(), refreshJwt, clientInputs)
    }

    private fun handleUnregisterComponent(handle: String) {
        logger.info("Unregistered widget component", handle)
        if (pendingDeepLink?.second == handle) pendingDeepLink = null
    }

    private fun handleComponentError(handle: String?, error: DescopeException) {
        // sub-flow errors are not terminal for the widget itself, just log them
        logger.info("Widget sub-flow reported error", handle, error)
    }

    // Hooks

    private fun executeHooks(event: Event) {
        common.executeHooks(this, event, widget?.hooks ?: emptyList())
    }

    // State

    private fun handleStarted() {
        if (state == Started) return
        state = Started
        executeHooks(Event.Started)
    }

    private fun handleLoaded() {
        if (state != Started) return
        executeHooks(Event.Loaded)
    }

    private fun handleWidgetReady() {
        if (ensureState(Started)) return
        logger.info("Widget is ready")
        startTimer()
        state = Ready
        executeHooks(Event.Ready)
        listener?.onReady()
    }

    private fun handleError(e: DescopeException) {
        if (ensureState(Initial, Started, Ready, Failed)) return
        if (state == Failed) return

        logger.error("Widget failed with [${e.code}] error", e)
        stopTimer()
        state = Failed
        listener?.onError(e)
    }

    private fun ensureState(vararg allowedStates: DescopeUserProfileWidgetView.State): Boolean {
        if (allowedStates.contains(state)) {
            return false
        }
        logger.error("Unexpected widget state: ${state.name}", allowedStates)
        return true
    }

    // Native Operations

    private fun handleRequest(request: BridgeRequest) {
        if (request is BridgeRequest.WebAuth) {
            request.handle?.let { pendingDeepLink = request.variant to it }
        }
        val scope = webView.findViewTreeLifecycleOwner()?.lifecycleScope ?: CoroutineScope(Job())
        scope.launch(Dispatchers.Main) {
            val response = common.handleNativeAction(request)
            if (request is BridgeRequest.WebAuth && response is BridgeResponse.Failure) {
                pendingDeepLink = null
            }
            response?.let { resp ->
                request.handle?.let { sendResponse(it, resp) }
            }
        }
    }

    private fun sendResponse(handle: String, response: BridgeResponse) {
        if (ensureState(Started, Ready)) return
        bridge.postResponse(handle, response)
    }

    // Logout

    private fun handleRequestLogout() {
        val sdk = sdk
        if (sdk == null) {
            logger.error("Cannot perform logout without an SDK instance")
            bridge.dispatchLogoutFailed(DescopeException.widgetSetup.with(message = "Descope SDK is not initialized"))
            return
        }
        val refreshJwt = currentSession?.refreshJwt
        if (refreshJwt.isNullOrEmpty()) {
            logger.error("Cannot perform logout without an active session")
            bridge.dispatchLogoutFailed(DescopeException.widgetAuthenticationRequired)
            listener?.onError(DescopeException.widgetAuthenticationRequired)
            return
        }

        val scope = webView.findViewTreeLifecycleOwner()?.lifecycleScope ?: CoroutineScope(Job())
        scope.launch(Dispatchers.Main) {
            try {
                sdk.auth.revokeSessions(RevokeType.CurrentSession, refreshJwt)
                sdk.sessionManager.clearSession()
                bridge.dispatchLogoutComplete()
                listener?.onLogout()
            } catch (e: DescopeException) {
                logger.error("Logout request failed", e)
                bridge.dispatchLogoutFailed(e)
                listener?.onError(e)
            }
        }
    }

    // Timer

    private val periodicUpdateFrequency: Long = 1000L // 1 second
    private var timer: Timer? = null

    private fun startTimer() {
        stopTimer()
        val ref = WeakReference(this)
        timer = timer(name = "DescopeUserProfileWidgetCoordinator", period = periodicUpdateFrequency, action = createTimerAction(ref))
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    internal fun periodicRefreshJwtUpdate() {
        handler.post {
            val refreshJwt = currentSession?.refreshJwt ?: ""
            bridge.seedWidgetRootSession(refreshJwt)
            bridge.componentAttributes.keys.toList().forEach { handle ->
                bridge.updateRefreshJwt(handle, refreshJwt)
            }
        }
    }
}

private fun createTimerAction(ref: WeakReference<DescopeUserProfileWidgetCoordinator>): (TimerTask.() -> Unit) {
    return {
        val coordinator = ref.get()
        if (coordinator == null) {
            cancel()
        } else {
            coordinator.periodicRefreshJwtUpdate()
        }
    }
}

private fun createResumeClosure(ref: WeakReference<DescopeUserProfileWidgetCoordinator>): (Uri) -> Boolean {
    return { uri -> ref.get()?.resumeFromDeepLink(uri) ?: false }
}
