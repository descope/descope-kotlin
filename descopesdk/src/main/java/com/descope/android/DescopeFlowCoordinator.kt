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
import com.descope.android.DescopeFlowHook.Event
import com.descope.android.DescopeFlowView.NavigationStrategy.DoNothing
import com.descope.android.DescopeFlowView.NavigationStrategy.Inline
import com.descope.android.DescopeFlowView.NavigationStrategy.OpenBrowser
import com.descope.android.DescopeFlowView.State.Failed
import com.descope.android.DescopeFlowView.State.Finished
import com.descope.android.DescopeFlowView.State.Initial
import com.descope.android.DescopeFlowView.State.Ready
import com.descope.android.DescopeFlowView.State.Started
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
import com.descope.internal.routes.nativeAuthorization
import com.descope.internal.routes.performAssertion
import com.descope.internal.routes.performRegister
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

@SuppressLint("SetJavaScriptEnabled")
class DescopeFlowCoordinator(val webView: WebView) {

    internal var listener: DescopeFlowView.Listener? = null
    internal var state: DescopeFlowView.State = Initial

    private val bridge = FlowBridge(webView)
    private var flow: DescopeFlow? = null
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
        override fun onRequest(request: FlowBridgeRequest) = handleRequest(request)
        override fun onNavigation(uri: Uri) = handleNavigation(uri)
        override fun onSuccess(data: String?, url: String) = handleFinish(data, url)
        override fun onError(error: DescopeException) = handleError(error)
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
        bridge.logger = logger
        handleStarted()
        bridge.start(flow.url)
    }

    internal fun resumeFromDeepLink(deepLink: Uri) {
        if (flow == null) {
            logger.error("resumeFromDeepLink cannot be called before startFlow")
            return
        }
        activityHelper.closeCustomTab(webView.context)
        val type = if (deepLink.queryParameterNames.contains("t")) "magicLink" else "oauthWeb"
        val response = if (type == "magicLink") {
            FlowBridgeResponse.MagicLink(url = deepLink.toString())
        } else {
            FlowBridgeResponse.WebAuth(type = type, url = deepLink.toString())
        }
        sendResponse(response)
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

        val useCustomSchemeFallback = shouldUseCustomSchemeUrl(context)

        val origin = try {
            if (isWebAuthnSupported) getPackageOrigin(context) else ""
        } catch (_: Exception) {
            ""
        }

        val refreshJwt = currentSession?.refreshJwt ?: ""
        val oauthProvider = flow.oauthNativeProvider?.name ?: ""
        val oauthRedirect = pickRedirectUrl(flow.oauthRedirect, flow.oauthRedirectCustomScheme, useCustomSchemeFallback)
        val ssoRedirect = pickRedirectUrl(flow.ssoRedirect, flow.ssoRedirectCustomScheme, useCustomSchemeFallback)
        val magicLinkRedirect = flow.magicLinkRedirect ?: ""

        val nativeOptions = JSONObject().apply {
            put("platform", "android")
            put("bridgeVersion", 1)
            put("oauthProvider", oauthProvider)
            put("oauthRedirect", oauthRedirect)
            put("ssoRedirect", ssoRedirect)
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

        handler.post {
            logger.error("Flow failed with [${e.code}] error", e)
            stopTimer()
            state = Failed
            listener?.onError(e)
        }
    }

    private fun handleSuccess(authResponse: AuthenticationResponse) {
        if (ensureState(Started, Ready)) return
        handler.post {
            val res = if (logger.isUnsafeEnabled) authResponse else null
            logger.info("Flow finished successfully", res)
            stopTimer()
            state = Finished
            listener?.onSuccess(authResponse)
        }
    }

    private fun handleAuthentication(data: String, url: String) {
        try {
            val jwtServerResponse = JwtServerResponse.fromJson(data, emptyList())
            // take tokens from cookies if missing
            val respCookieString = CookieManager.getInstance().getCookie("https://${jwtServerResponse.cookieDomain}${jwtServerResponse.cookiePath}")
            val urlCookieString = CookieManager.getInstance().getCookie(url)
            jwtServerResponse.sessionJwt = jwtServerResponse.sessionJwt ?: findJwtInCookies(SESSION_COOKIE_NAME, respCookieString, urlCookieString)
            val refreshCookieName = bridge.attributes.refreshCookieName ?: REFRESH_COOKIE_NAME
            jwtServerResponse.refreshJwt = jwtServerResponse.refreshJwt ?: findJwtInCookies(refreshCookieName, respCookieString, urlCookieString)
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

    private fun sendResponse(response: FlowBridgeResponse) {
        if (ensureState(Started, Ready)) return // we get here in started state if the flow has no screens
        bridge.postResponse(response)
    }

    // Native Operations

    private fun handleRequest(request: FlowBridgeRequest) {
        val scope = webView.findViewTreeLifecycleOwner()?.lifecycleScope ?: CoroutineScope(Job())
        scope.launch(Dispatchers.Main) {
            when (request) {
                is FlowBridgeRequest.OAuthNative -> handleOAuthNative(request)
                is FlowBridgeRequest.OAuthWeb -> handleOAuthWeb(request)
                is FlowBridgeRequest.Sso -> handleSso(request)
                is FlowBridgeRequest.WebAuthnCreate -> handleWebAuthnCreate(request)
                is FlowBridgeRequest.WebAuthnGet -> handleWebAuthnGet(request)
            }
        }
    }

    private suspend fun handleOAuthNative(request: FlowBridgeRequest.OAuthNative) {
        logger.info("Launching system UI for native oauth")
        try {
            val resp = nativeAuthorization(webView.context, request.start)
            sendResponse(FlowBridgeResponse.OAuthNative(resp.stateId, resp.identityToken))
        } catch (e: DescopeException) {
            if (e == DescopeException.oauthNativeCancelled) {
                logger.info("OAuth native canceled")
                return
            }
            logger.error("OAuth native failed", e)
            sendResponse(FlowBridgeResponse.Failure("OAuthNativeFailed"))
        }
    }

    private fun handleOAuthWeb(request: FlowBridgeRequest.OAuthWeb) {
        logger.info("Launching custom tab for web-based oauth")
        try {
            launchCustomTab(webView.context, request.startUrl, flow?.presentation?.createCustomTabsIntent(webView.context))
        } catch (e: DescopeException) {
            logger.error("Failed to launch custom tab", e)
            sendResponse(FlowBridgeResponse.Failure("CustomTabFailure"))
        }
    }

    private fun handleSso(request: FlowBridgeRequest.Sso) {
        logger.info("Launching custom tab for sso")
        try {
            launchCustomTab(webView.context, request.startUrl, flow?.presentation?.createCustomTabsIntent(webView.context))
        } catch (e: DescopeException) {
            logger.error("Failed to launch custom tab", e)
            sendResponse(FlowBridgeResponse.Failure("CustomTabFailure"))
        }
    }

    private suspend fun handleWebAuthnCreate(request: FlowBridgeRequest.WebAuthnCreate) {
        logger.info("Attempting to create new a passkey")
        try {
            val res = performRegister(webView.context, request.options)
            sendResponse(FlowBridgeResponse.WebAuthn(type = "webauthnCreate", transactionId = request.transactionId, response = res))
        } catch (e: DescopeException) {
            if (e == DescopeException.passkeyCancelled) {
                logger.info("Passkeys canceled")
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
            sendResponse(FlowBridgeResponse.Failure(failure))
        }
    }

    private suspend fun handleWebAuthnGet(request: FlowBridgeRequest.WebAuthnGet) {
        logger.info("Attempting to use an existing passkey")
        try {
            val res = performAssertion(webView.context, request.options)
            sendResponse(FlowBridgeResponse.WebAuthn(type = "webauthnGet", transactionId = request.transactionId, response = res))
        } catch (e: DescopeException) {
            if (e == DescopeException.passkeyCancelled) {
                logger.info("Passkeys canceled")
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
            sendResponse(FlowBridgeResponse.Failure(failure))
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

// Default Browser

private fun shouldUseCustomSchemeUrl(context: Context): Boolean {
    val browserIntent = Intent("android.intent.action.VIEW", Uri.parse("http://"))
    val resolveInfo = context.packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
    return when (resolveInfo?.loadLabel(context.packageManager).toString().lowercase()) {
        "opera",
        "opera mini",
        "duckduckgo",
        "mi browser",
            -> true

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
