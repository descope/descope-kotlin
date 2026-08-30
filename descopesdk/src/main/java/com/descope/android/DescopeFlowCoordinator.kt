package com.descope.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.descope.Descope
import com.descope.android.CoordinatorState.Failed
import com.descope.android.CoordinatorState.Finished
import com.descope.android.CoordinatorState.Initial
import com.descope.android.CoordinatorState.Ready
import com.descope.android.CoordinatorState.Started
import com.descope.android.DescopeFlowHook.Event
import com.descope.android.NavigationStrategy.DoNothing
import com.descope.android.NavigationStrategy.Inline
import com.descope.android.NavigationStrategy.OpenBrowser
import com.descope.internal.http.JwtServerResponse
import com.descope.internal.http.REFRESH_COOKIE_NAME
import com.descope.internal.http.SESSION_COOKIE_NAME
import com.descope.internal.others.activityHelper
import com.descope.internal.others.debug
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.internal.others.isUnsafeEnabled
import com.descope.internal.others.jsonFromString
import com.descope.internal.others.stringOrEmptyAsNull
import com.descope.internal.others.toJsonObject
import com.descope.internal.others.with
import com.descope.internal.routes.convert
import com.descope.internal.routes.getPackageOrigin
import com.descope.internal.routes.isWebAuthnSupported
import com.descope.internal.routes.nativeAuthorization
import com.descope.internal.routes.performAssertion
import com.descope.internal.routes.performRegister
import com.descope.sdk.DescopeLogger
import com.descope.sdk.DescopeSdk
import com.descope.session.DescopeSession
import com.descope.session.Token
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
import com.descope.types.DescopeUser
import com.descope.types.RevokeType
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
import androidx.core.net.toUri

@SuppressLint("SetJavaScriptEnabled")
class DescopeFlowCoordinator(val webView: WebView) {

    internal var listener: CoordinatorListener? = null
    internal var state: CoordinatorState = Initial
    internal var isWidgetMode: Boolean = false

    private val bridge = FlowBridge(webView)
    private var flow: DescopeFlow? = null
    private val components = mutableMapOf<String, FlowBridgeAttributes>()
    private var pendingDeepLink: Pair<String, String>? = null // (wcKey, deepLinkType)
    private var refreshUserInFlight = false
    private var refreshUserPending = false
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
        override fun onRegister(wcKey: String, attributes: FlowBridgeAttributes) = handleRegister(wcKey, attributes)
        override fun onUnregister(wcKey: String) = handleUnregister(wcKey)
        override fun onReady(wcKey: String, tag: String) = handleReady(wcKey, tag)
        override fun onRequest(wcKey: String, request: FlowBridgeRequest) = handleRequest(wcKey, request)
        override fun onNavigation(uri: Uri) = handleNavigation(uri)
        override fun onSuccess(wcKey: String, data: String?, url: String) = handleFinish(wcKey, data, url)
        override fun onError(wcKey: String, error: DescopeException) = handleError(wcKey, error)
        override fun onWidgetEvent(name: String, detail: String) = handleWidgetEvent(name, detail)
    }

    init {
        bridge.listener = bridgeListener
    }

    // Public API

    fun runJavaScript(code: String) = bridge.runJavaScript(code)

    fun addStyles(css: String) = bridge.addStyles(css)

    // Internal API

    internal fun startFlow(flow: DescopeFlow) {
        this.flow = flow
        bridge.flow = flow
        bridge.logger = logger
        bridge.nativeOptions = buildNativeOptions(flow)
        bridge.clientInputs = buildClientInputs(flow)
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

        // magic links don't go through the bridge as a request, so detect them from the URL itself.
        // Otherwise, prefer the pending deep-link's wcKey (the component that issued the WebAuth
        // request); fall back to the most-recently registered component for legacy / async cases.
        val isMagicLink = deepLink.queryParameterNames.contains("t")
        val wcKey: String
        val type: String
        if (isMagicLink) {
            wcKey = components.keys.lastOrNull() ?: run {
                logger.error("Received magic link deep link but no component is registered")
                return false
            }
            type = "magicLink"
        } else {
            val pending = pendingDeepLink
            if (pending != null) {
                wcKey = pending.first
                type = pending.second
            } else {
                wcKey = components.keys.lastOrNull() ?: run {
                    logger.error("Received deep link but no component is registered")
                    return false
                }
                type = "oauthWeb"
            }
        }
        pendingDeepLink = null
        sendResponse(wcKey, FlowBridgeResponse.DeepLink(type = type, url = deepLink.toString()))
        return true
    }

    // Bridge Events

    private fun handleNavigation(uri: Uri): Boolean {
        return when (listener?.onNavigation(uri) ?: OpenBrowser) {
            Inline -> false
            DoNothing -> true
            OpenBrowser -> {
                try {
                    when (uri.scheme) {
                        "mailto", "tel" -> sendViewIntent(webView.context, uri)
                        else -> launchCustomTab(webView.context, uri, flow?.presentation?.createCustomTabsIntent(webView.context))
                    }
                } catch (e: DescopeException) {
                    logger.error("Failed to open URL in browser", e)
                }
                true
            }
        }
    }

    private fun handleRegister(wcKey: String, attributes: FlowBridgeAttributes) {
        components[wcKey] = attributes
    }

    private fun handleUnregister(wcKey: String) {
        components.remove(wcKey)
        if (pendingDeepLink?.first == wcKey) {
            pendingDeepLink = null
        }
    }

    private fun handleFinish(wcKey: String, data: String?, url: String) {
        if (isWidgetMode) {
            // Widget flows are meant to update the user, so we try to refresh the user details
            refreshUser()
            return
        }
        val jwtServerResponse = parseJwtServerResponse(wcKey, data, url)
        if (jwtServerResponse != null) {
            try {
                val authResponse = jwtServerResponse.convert()
                logger.debug("Flow received an authentication response", data)
                handleSuccess(authResponse)
                return
            } catch (_: Exception) {
                logger.debug("Flow received a partial authentication response")
            }
        }
        val session = currentSession
        if (session != null) {
            handleSuccess(AuthenticationResponse(sessionToken = session.sessionToken, refreshToken = session.refreshToken, user = session.user, isFirstAuthentication = false, flowOutput = jwtServerResponse?.flowOutput ?: emptyMap()))
            return
        }
        handleError(wcKey, DescopeException.flowFailed.with(message = "No valid authentication tokens found"))
    }

    private fun parseJwtServerResponse(wcKey: String, data: String?, url: String): JwtServerResponse? {
        if (data == null) return null
        return try {
            val jwtServerResponse = JwtServerResponse.fromJson(data, emptyList())
            // take tokens from cookies if missing
            val respCookieString = CookieManager.getInstance().getCookie("https://${jwtServerResponse.cookieDomain}${jwtServerResponse.cookiePath}")
            val urlCookieString = CookieManager.getInstance().getCookie(url)
            val refreshCookieName = jwtServerResponse.cookieName ?: components[wcKey]?.refreshCookieName ?: REFRESH_COOKIE_NAME
            jwtServerResponse.sessionJwt = jwtServerResponse.sessionJwt ?: findJwtInCookies(jwtServerResponse.sessionCookieName ?: SESSION_COOKIE_NAME, respCookieString, urlCookieString)
            jwtServerResponse.refreshJwt = jwtServerResponse.refreshJwt ?: findJwtInCookies(refreshCookieName, respCookieString, urlCookieString)
            return jwtServerResponse
        } catch (_: Exception) {
            null
        }
    }

    // Configuration

    // Frozen at startFlow time and baked into the JS setup script — every wc
    // that registers reads from the same config.
    private fun buildNativeOptions(flow: DescopeFlow): String {
        val useCustomSchemeFallback = shouldUseCustomSchemeUrl(context)
        val origin = try {
            if (isWebAuthnSupported) getPackageOrigin(context) else ""
        } catch (_: Exception) {
            ""
        }
        return JSONObject().apply {
            put("platform", "android")
            put("bridgeVersion", 1)
            put("oauthProvider", flow.oauthNativeProvider?.name ?: "")
            put("oauthRedirect", pickRedirectUrl(flow.oauthRedirect, flow.oauthRedirectCustomScheme, useCustomSchemeFallback))
            put("ssoRedirect", pickRedirectUrl(flow.ssoRedirect, flow.ssoRedirectCustomScheme, useCustomSchemeFallback))
            put("externalAuthRedirect", pickRedirectUrl(flow.externalAuthRedirect, flow.externalAuthRedirectCustomScheme, useCustomSchemeFallback))
            put("magicLinkRedirect", flow.magicLinkRedirect ?: "")
            put("origin", origin)
        }.toString()
    }

    private fun buildClientInputs(flow: DescopeFlow): String =
        if (flow.clientInputs.isNotEmpty()) flow.clientInputs.toJsonObject().toString() else ""

    // Hooks

    private fun executeHooks(event: Event) {
        val hooks = mutableListOf<DescopeFlowHook>().apply {
            addAll(DescopeFlowHook.defaults)
            addAll(flow?.hooks ?: emptyList())
        }
        hooks.filter { it.events.contains(event) }
            .forEach { it.execute(event, this) }
    }

    // State

    private fun handleStarted() {
        if (state == Started) return
        state = Started
        executeHooks(Event.Started)
    }

    private fun handleLoaded() {
        if (state != Started) return
        // Seed the session into localStorage so any wc / widget shell that mounts
        // afterwards can authenticate immediately. Empty JWT is a JS-side no-op.
        bridge.setRefreshJwt(currentSession?.refreshJwt ?: "")
        executeHooks(Event.Loaded)
    }

    private fun handleReady(wcKey: String, tag: String) {
        // in widget mode, widget-root drives ready (see handleWidgetEvent)
        if (isWidgetMode) return
        if (ensureState(Started)) return
        logger.info("Flow is ready ($tag)", wcKey)
        startTimer()
        state = Ready
        executeHooks(Event.Ready)
        listener?.onReady()
    }

    private fun handleError(wcKey: String, e: DescopeException) {
        // in widget mode, per-component errors are sub-flow modal concerns, not terminal.
        // Empty wcKey = top-level (network error, HTTP error, abort) — always surfaces.
        if (isWidgetMode && wcKey.isNotEmpty()) return
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
        // in widget mode, per-component success means a sub-flow modal completed, not the widget
        if (isWidgetMode) return
        if (ensureState(Started, Ready)) return
        val res = if (logger.isUnsafeEnabled) authResponse else null
        logger.info("Flow finished successfully", res)
        stopTimer()
        state = Finished
        listener?.onSuccess(authResponse)
    }

    private fun ensureState(vararg allowedStates: CoordinatorState): Boolean {
        if (allowedStates.contains(state)) {
            return false
        }
        logger.error("Unexpected flow state: ${state.name}", allowedStates)
        return true
    }

    private fun sendResponse(wcKey: String, response: FlowBridgeResponse) {
        if (ensureState(Started, Ready)) return
        bridge.postResponse(wcKey, response)
    }

    // Widget Events

    private fun handleWidgetEvent(name: String, detail: String) {
        when (name) {
            "ready" -> handleWidgetReady()
            "error" -> handleWidgetError(detail)
            "widget-logout" -> handleWidgetLogout()
            else -> logger.info("Unknown widget event", name)
        }
    }

    private fun handleWidgetReady() {
        if (ensureState(Started)) return
        logger.info("Widget is ready")
        startTimer()
        state = Ready
        executeHooks(Event.Ready)
        listener?.onReady()
    }

    private fun handleWidgetError(detail: String) {
        if (ensureState(Started, Ready, Failed)) return
        if (state == Failed) return
        val exception = parseWidgetError(detail)
        logger.error("Widget failed with [${exception.code}] error", exception)
        stopTimer()
        state = Failed
        listener?.onError(exception)
    }

    private fun handleWidgetLogout() {
        if (ensureState(Started, Ready)) return
        val sdk = sdk ?: return
        val refreshJwt = currentSession?.refreshJwt
        val scope = webView.findViewTreeLifecycleOwner()?.lifecycleScope ?: CoroutineScope(Job())
        scope.launch(Dispatchers.IO) {
            if (!refreshJwt.isNullOrEmpty()) {
                try {
                    sdk.auth.revokeSessions(RevokeType.CurrentSession, refreshJwt)
                } catch (e: DescopeException) {
                    logger.error("Widget logout revoke failed", e)
                }
            }
            sdk.sessionManager.clearSession()
            handler.post {
                stopTimer()
                bridge.clearRefreshJwt()
                listener?.onLogout()
            }
        }
    }

    private fun refreshUser() {
        if (ensureState(Started, Ready)) return
        // protect against concurrent user updates
        if (refreshUserInFlight) {
            refreshUserPending = true
            return
        }
        val sdk = sdk ?: return
        val refreshJwt = currentSession?.refreshJwt ?: return
        refreshUserInFlight = true
        val scope = webView.findViewTreeLifecycleOwner()?.lifecycleScope ?: CoroutineScope(Job())
        scope.launch(Dispatchers.IO) {
            val user = try {
                sdk.auth.me(refreshJwt)
            } catch (e: Exception) {
                logger.error("Failed to fetch user details in user profile widget, continuing without", e)
                null
            }
            handler.post {
                refreshUserInFlight = false
                if (user != null && listener?.onUserUpdated(user) != true) {
                    sdk.sessionManager.updateUser(user)
                }
                if (refreshUserPending) {
                    refreshUserPending = false
                    refreshUser()
                }
            }
        }
    }

    private fun parseWidgetError(detail: String): DescopeException {
        val json = jsonFromString(detail)
        return when {
            detail.isEmpty() -> DescopeException.flowFailed.with(message = "Widget failed")
            json != null -> {
                // widget dispatches errors with a `{ code, description, message }` detail
                val code = json.stringOrEmptyAsNull("code")
                val description = json.stringOrEmptyAsNull("description") ?: "Widget failed"
                val message = json.stringOrEmptyAsNull("message")
                if (code != null) {
                    DescopeException(code = code, desc = description, message = message)
                } else {
                    DescopeException.flowFailed.with(message = message ?: description)
                }
            }
            else -> DescopeException.flowFailed.with(message = detail)
        }
    }

    // Native Operations

    private fun handleRequest(wcKey: String, request: FlowBridgeRequest) {
        val scope = webView.findViewTreeLifecycleOwner()?.lifecycleScope ?: CoroutineScope(Job())
        scope.launch(Dispatchers.Main) {
            when (request) {
                is FlowBridgeRequest.OAuthNative -> handleOAuthNative(wcKey, request)
                is FlowBridgeRequest.WebAuth -> handleWebAuth(wcKey, request)
                is FlowBridgeRequest.WebAuthnCreate -> handleWebAuthnCreate(wcKey, request)
                is FlowBridgeRequest.WebAuthnGet -> handleWebAuthnGet(wcKey, request)
            }
        }
    }

    private suspend fun handleOAuthNative(wcKey: String, request: FlowBridgeRequest.OAuthNative) {
        logger.info("Launching system UI for native oauth")
        try {
            val resp = nativeAuthorization(webView.context, request.start)
            sendResponse(wcKey, FlowBridgeResponse.OAuthNative(resp.stateId, resp.identityToken))
        } catch (e: DescopeException) {
            if (e == DescopeException.oauthNativeCancelled) {
                logger.info("OAuth native canceled")
                sendResponse(wcKey, FlowBridgeResponse.Failure("OAuthNativeCancelled"))
                return
            }
            logger.error("OAuth native failed", e)
            sendResponse(wcKey, FlowBridgeResponse.Failure("OAuthNativeFailed"))
        }
    }

    private fun handleWebAuth(wcKey: String, request: FlowBridgeRequest.WebAuth) {
        pendingDeepLink = wcKey to request.variant
        logger.info("Launching custom tab for ${request.variant}")
        try {
            launchCustomTab(webView.context, request.startUrl, flow?.presentation?.createCustomTabsIntent(webView.context)) {
                // Custom tab dismissed by the user before completing the auth flow.
                pendingDeepLink = null
                sendResponse(wcKey, FlowBridgeResponse.Failure("WebAuthCancelled"))
            }
        } catch (e: DescopeException) {
            logger.error("Failed to launch custom tab", e)
            pendingDeepLink = null
            sendResponse(wcKey, FlowBridgeResponse.Failure("CustomTabFailure"))
        }
    }

    private suspend fun handleWebAuthnCreate(wcKey: String, request: FlowBridgeRequest.WebAuthnCreate) {
        logger.info("Attempting to create new a passkey")
        try {
            val res = performRegister(webView.context, request.options)
            sendResponse(wcKey, FlowBridgeResponse.WebAuthn(type = "webauthnCreate", transactionId = request.transactionId, response = res))
        } catch (e: DescopeException) {
            if (e == DescopeException.passkeyCancelled) {
                logger.info("Passkeys canceled")
                sendResponse(wcKey, FlowBridgeResponse.Failure("PasskeyCancelled"))
                return
            }
            val failure = when (e) {
                DescopeException.passkeyFailed -> {
                    logger.error("Passkeys failed", e)
                    "PasskeyFailed"
                }
                DescopeException.passkeyNoPasskeys -> {
                    logger.error("No passkeys are available", e)
                    "PasskeyNoPasskeys"
                }
                else -> {
                    logger.error("Native execution failed", e)
                    "NativeFailed"
                }
            }
            sendResponse(wcKey, FlowBridgeResponse.Failure(failure))
        }
    }

    private suspend fun handleWebAuthnGet(wcKey: String, request: FlowBridgeRequest.WebAuthnGet) {
        logger.info("Attempting to use an existing passkey")
        try {
            val res = performAssertion(webView.context, request.options)
            sendResponse(wcKey, FlowBridgeResponse.WebAuthn(type = "webauthnGet", transactionId = request.transactionId, response = res))
        } catch (e: DescopeException) {
            if (e == DescopeException.passkeyCancelled) {
                logger.info("Passkeys canceled")
                sendResponse(wcKey, FlowBridgeResponse.Failure("PasskeyCancelled"))
                return
            }
            val failure = when (e) {
                DescopeException.passkeyFailed -> {
                    logger.error("Passkeys failed", e)
                    "PasskeyFailed"
                }
                DescopeException.passkeyNoPasskeys -> {
                    logger.error("No passkeys are available", e)
                    "PasskeyNoPasskeys"
                }
                else -> {
                    logger.error("Native execution failed", e)
                    "NativeFailed"
                }
            }
            sendResponse(wcKey, FlowBridgeResponse.Failure(failure))
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
            // Session-level write — every consumer on the page reads the same key.
            bridge.setRefreshJwt(currentSession?.refreshJwt ?: "")
        }
    }
}

// internal lifecycle states; each View translates to its public State enum at the property boundary
internal enum class CoordinatorState {
    Initial, Started, Ready, Failed, Finished,
}

// internal listener; each View installs an adapter that translates to its public Listener
internal interface CoordinatorListener {
    fun onReady() {}
    fun onSuccess(response: AuthenticationResponse) {}
    fun onError(exception: DescopeException) {}
    fun onLogout() {}
    fun onUserUpdated(user: DescopeUser): Boolean = false
    fun onNavigation(uri: Uri): NavigationStrategy = NavigationStrategy.OpenBrowser
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

// Default Browser

private fun shouldUseCustomSchemeUrl(context: Context): Boolean {
    val browserIntent = Intent("android.intent.action.VIEW", "http://".toUri())
    val resolveInfo = context.packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
    val label = resolveInfo?.loadLabel(context.packageManager).toString()
    return when (label.lowercase()) {
        "opera",  "opera mini",  "duckduckgo",  "mi browser" -> true
        else -> false
    }
}

private fun pickRedirectUrl(main: String?, fallback: String?, useFallback: Boolean): String {
    var url = main
    if (useFallback && fallback != null) url = fallback
    return url ?: ""
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
