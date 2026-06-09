package com.descope.android.flow

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.descope.Descope
import com.descope.android.bridge.BridgeRequest
import com.descope.android.bridge.BridgeResponse
import com.descope.android.bridge.Coordinator
import com.descope.android.bridge.DescopeBridgeHook.Event
import com.descope.android.bridge.WebViewCoordinator
import com.descope.android.flow.DescopeFlowView.NavigationStrategy.DoNothing
import com.descope.android.flow.DescopeFlowView.NavigationStrategy.Inline
import com.descope.android.flow.DescopeFlowView.NavigationStrategy.OpenBrowser
import com.descope.android.flow.DescopeFlowView.State.Failed
import com.descope.android.flow.DescopeFlowView.State.Finished
import com.descope.android.flow.DescopeFlowView.State.Initial
import com.descope.android.flow.DescopeFlowView.State.Ready
import com.descope.android.flow.DescopeFlowView.State.Started
import com.descope.internal.http.JwtServerResponse
import com.descope.internal.http.REFRESH_COOKIE_NAME
import com.descope.internal.http.SESSION_COOKIE_NAME
import com.descope.internal.others.activityHelper
import com.descope.internal.others.debug
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.internal.others.isUnsafeEnabled
import com.descope.internal.others.toJsonObject
import com.descope.internal.others.with
import com.descope.internal.routes.convert
import com.descope.internal.routes.getPackageOrigin
import com.descope.internal.routes.isWebAuthnSupported
import com.descope.sdk.DescopeLogger
import com.descope.sdk.DescopeSdk
import com.descope.session.DescopeSession
import com.descope.session.Token
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpCookie
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.timer

internal class DescopeFlowCoordinator(override val webView: WebView) : Coordinator {

    internal var listener: DescopeFlowView.Listener? = null
    internal var state: DescopeFlowView.State = Initial

    private val bridge = FlowBridge(webView)
    private val common = WebViewCoordinator(webView)
    private var flow: DescopeFlow? = null
    private var pendingDeepLinkType: String? = null
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val sdk: DescopeSdk?
        get() = flow?.sdk ?: if (Descope.isInitialized) Descope.sdk else null
    private val logger: DescopeLogger?
        get() = sdk?.client?.config?.logger
    private val context: Context
        get() = webView.context
    private val currentSession: DescopeSession?
        get() = if (flow?.sessionProvider != null) flow?.sessionProvider?.invoke() else sdk?.sessionManager?.session?.takeIf { !it.refreshToken.isExpired }

    private val bridgeListener = object : FlowBridge.Listener {
        override fun onLoaded() = handleLoaded()
        override fun onFound() = initialize()
        override fun onReady(tag: String) = handleReady(tag)
        override fun onRequest(request: BridgeRequest) = handleRequest(request)
        override fun onNavigation(uri: Uri) = handleNavigation(uri)
        override fun onSuccess(data: String?, url: String) = handleFinish(data, url)
        override fun onError(error: DescopeException) = handleError(error)
    }

    init {
        bridge.listener = bridgeListener
    }

    // Public API

    override fun runJavaScript(code: String) = bridge.runJavaScript(code)

    override fun addStyles(css: String) = bridge.addStyles(css)

    // Internal API

    internal fun startFlow(flow: DescopeFlow) {
        this.flow = flow
        bridge.flow = flow
        bridge.logger = logger
        common.logger = logger
        common.customTabsIntent = flow.presentation?.createCustomTabsIntent(context)
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
            else -> pendingDeepLinkType ?: "oauthWeb"
        }
        pendingDeepLinkType = null
        sendResponse(BridgeResponse.DeepLink(type = type, url = deepLink.toString()))
        return true
    }

    // Bridge Events

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

    private fun handleFinish(data: String?, url: String) {
        if (data != null) {
            handleAuthentication(data, url)
            return
        }
        val session = currentSession
        if (session != null) {
            handleSuccess(AuthenticationResponse(sessionToken = session.sessionToken, refreshToken = session.refreshToken, user = session.user, isFirstAuthentication = false))
            return
        }
        handleError(DescopeException.flowFailed.with(message = "No valid authentication tokens found"))
    }

    // Initialize

    private fun initialize() {
        val flow = flow ?: return

        val useCustomSchemeFallback = common.shouldUseCustomSchemeUrl()

        val origin = try {
            if (isWebAuthnSupported) getPackageOrigin(context) else ""
        } catch (_: Exception) {
            ""
        }

        val refreshJwt = currentSession?.refreshJwt ?: ""
        val oauthProvider = flow.oauthNativeProvider?.name ?: ""
        val oauthRedirect = common.pickRedirectUrl(flow.oauthRedirect, flow.oauthRedirectCustomScheme, useCustomSchemeFallback)
        val ssoRedirect = common.pickRedirectUrl(flow.ssoRedirect, flow.ssoRedirectCustomScheme, useCustomSchemeFallback)
        val externalAuthRedirect = common.pickRedirectUrl(flow.externalAuthRedirect, flow.externalAuthRedirectCustomScheme, useCustomSchemeFallback)
        val magicLinkRedirect = flow.magicLinkRedirect ?: ""

        val nativeOptions = JSONObject().apply {
            put("platform", "android")
            put("bridgeVersion", 1)
            put("oauthProvider", oauthProvider)
            put("oauthRedirect", oauthRedirect)
            put("ssoRedirect", ssoRedirect)
            put("externalAuthRedirect", externalAuthRedirect)
            put("magicLinkRedirect", magicLinkRedirect)
            put("origin", origin)
        }

        var clientInputs = ""
        if (flow.clientInputs.isNotEmpty()) {
            clientInputs = flow.clientInputs.toJsonObject().toString()
        }

        bridge.initialize(nativeOptions.toString(), refreshJwt, clientInputs)
    }

    // Hooks

    private fun executeHooks(event: Event) {
        common.executeHooks(this, event, flow?.hooks ?: emptyList())
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

    private fun handleReady(tag: String) {
        if (ensureState(Started)) return
        logger.info("Flow is ready ($tag)")
        startTimer()
        state = Ready
        executeHooks(Event.Ready)
        listener?.onReady()
    }

    private fun handleError(e: DescopeException) {
        if (ensureState(Initial, Started, Ready, Failed)) return

        // we allow multiple failure events and swallow them here instead of showing a warning above,
        // and ensure it only reports a single failure
        if (state == Failed) return

        logger.error("Flow failed with [${e.code}] error", e)
        stopTimer()
        state = Failed
        listener?.onError(e)
    }

    private fun handleSuccess(authResponse: AuthenticationResponse) {
        if (ensureState(Started, Ready)) return
        val res = if (logger.isUnsafeEnabled) authResponse else null
        logger.info("Flow finished successfully", res)
        stopTimer()
        state = Finished
        listener?.onSuccess(authResponse)
    }

    private fun handleAuthentication(data: String, url: String) {
        try {
            val jwtServerResponse = JwtServerResponse.fromJson(data, emptyList())
            // take tokens from cookies if missing
            val respCookieString = CookieManager.getInstance().getCookie("https://${jwtServerResponse.cookieDomain}${jwtServerResponse.cookiePath}")
            val urlCookieString = CookieManager.getInstance().getCookie(url)
            jwtServerResponse.sessionJwt = jwtServerResponse.sessionJwt ?: findJwtInCookies(jwtServerResponse.sessionCookieName ?: SESSION_COOKIE_NAME, respCookieString, urlCookieString)
            jwtServerResponse.refreshJwt = jwtServerResponse.refreshJwt ?: findJwtInCookies(jwtServerResponse.cookieName ?: bridge.attributes.refreshCookieName ?: REFRESH_COOKIE_NAME, respCookieString, urlCookieString)
            val authResponse = jwtServerResponse.convert()
            logger.debug("Flow received an authentication response", data)
            handleSuccess(authResponse)
        } catch (e: DescopeException) {
            logger.error("Unexpected error handling authentication response", e, data, url)
            handleError(DescopeException.flowFailed.with(message = "No valid authentication tokens found"))
        }
    }

    private fun ensureState(vararg allowedStates: DescopeFlowView.State): Boolean {
        if (allowedStates.contains(state)) {
            return false
        }
        logger.error("Unexpected flow state: ${state.name}", allowedStates)
        return true
    }

    private fun sendResponse(response: BridgeResponse) {
        if (ensureState(Started, Ready)) return // we get here in started state if the flow has no screens
        bridge.postResponse(response)
    }

    // Native Operations

    private fun handleRequest(request: BridgeRequest) {
        if (request is BridgeRequest.WebAuth) {
            pendingDeepLinkType = request.variant
        }
        val scope = webView.findViewTreeLifecycleOwner()?.lifecycleScope ?: CoroutineScope(Job())
        scope.launch(Dispatchers.Main) {
            val response = common.handleNativeAction(request)
            response?.let { sendResponse(it) }
        }
    }

    // Timer

    private val periodicUpdateFrequency: Long = 1000L // 1 second
    private var timer: Timer? = null

    private fun startTimer() {
        stopTimer()

        val ref = WeakReference(this)
        val action = createTimerAction(ref)
        timer = timer(name = "DescopeFlowCoordinator", period = periodicUpdateFrequency, action = action)
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    internal fun periodicRefreshJwtUpdate() {
        handler.post {
            val refreshJwt = currentSession?.refreshJwt ?: ""
            bridge.updateRefreshJwt(refreshJwt)
        }
    }
}

// Cookies

internal fun findJwtInCookies(name: String, vararg cookieStrings: String?): String? {
    val cookies = mutableListOf<HttpCookie>()
    cookieStrings.forEach { cookieString ->
        // split and aggregate all cookies
        cookieString?.split("; ")?.forEach {
            try {
                cookies.addAll(HttpCookie.parse(it))
            } catch (_: Exception) {
            }
        }
    }

    return cookies.filter { it.name == name } // filter according cookie name
        .mapNotNull { httpCookie -> // parse token
            try {
                Token(httpCookie.value)
            } catch (_: Exception) {
                null
            }
        }
        .maxByOrNull { it.issuedAt }?.jwt // take latest
}

private fun createTimerAction(ref: WeakReference<DescopeFlowCoordinator>): (TimerTask.() -> Unit) {
    return {
        val coordinator = ref.get()
        if (coordinator == null) {
            cancel()
        } else {
            coordinator.periodicRefreshJwtUpdate()
        }
    }
}

private fun createResumeClosure(ref: WeakReference<DescopeFlowCoordinator>): (Uri) -> Boolean {
    return { uri -> ref.get()?.resumeFromDeepLink(uri) ?: false }
}
