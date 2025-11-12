package com.descope.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import com.descope.internal.http.failureFromResponseCode
import com.descope.internal.others.activityHelper
import com.descope.internal.others.debug
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.internal.others.isUnsafeEnabled
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.net.HttpCookie
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.timer

private const val retryWindow = 10 * 1000L
private const val retryInterval = 1250L

@SuppressLint("SetJavaScriptEnabled")
class DescopeFlowCoordinator(val webView: WebView) {

    internal var listener: DescopeFlowView.Listener? = null
    internal var state: DescopeFlowView.State = Initial

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
    private var currentFlowUrl: Uri? = null
    private var alreadySetUp = false
    private var startedAt: Long = 0L
    private var attempts: Int = 0
    private var loadFailure: Boolean = false
    private var refreshCookieName = REFRESH_COOKIE_NAME

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true

        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onFound(data: String) {
                logger.info("Received found event")
                val attributes = JSONObject(data)
                handler.post {
                    handleFound(attributes)
                }
            }
            
            @JavascriptInterface
            fun onReady(tag: String) {
                handler.post {
                    handleReady(tag)
                }
            }

            @JavascriptInterface
            fun onSuccess(data: String?, url: String) {
                // in case we got an authentication response, use it
                if (data != null) {
                    handleAuthentication(data, url)
                    return
                }
                // if we didn't, use the session if it's available
                val session = currentSession
                if (session != null) {
                    handleSuccess(AuthenticationResponse(sessionToken = session.sessionToken, refreshToken = session.refreshToken, user = session.user, isFirstAuthentication = false))
                    return
                }
                // onSuccess must end with a valid authentication response at this point in time
                handleError(DescopeException.flowFailed.with(message = "No valid authentication tokens found"))
            }

            @JavascriptInterface
            fun onAbort(reason: String) {
                val e = if (reason.isNotEmpty()) {
                    logger.error("Flow aborted with a failure reason", reason)
                    DescopeException.flowFailed.with(message = reason)
                } else {
                    logger.info("Flow aborted with cancellation")
                    DescopeException.flowCancelled
                }
                handleError(e)
            }

            @JavascriptInterface
            fun onError(error: String) {
                handleError(DescopeException.flowFailed.with(message = error))
            }

            @JavascriptInterface
            fun native(response: String?, url: String) {
                if (response == null) {
                    logger.info("Skipping bridge call because response is null")
                    return
                }
                currentFlowUrl = url.toUri()
                val scope = webView.findViewTreeLifecycleOwner()?.lifecycleScope ?: CoroutineScope(Job())
                scope.launch(Dispatchers.Main) {
                    var type: String
                    var canceled = false
                    val nativeResponse = JSONObject()
                    try {
                        val nativePayload = NativePayload.fromJson(response)
                        type = nativePayload.type
                        when (nativePayload) {
                            is NativePayload.OAuthNative -> {
                                logger.info("Launching system UI for native oauth")
                                val resp = nativeAuthorization(webView.context, nativePayload.start)
                                nativeResponse.put("nativeOAuth", JSONObject().apply {
                                    put("stateId", resp.stateId)
                                    put("idToken", resp.identityToken)
                                })
                            }

                            is NativePayload.OAuthWeb -> {
                                logger.info("Launching custom tab for web-based oauth")
                                launchCustomTab(webView.context, nativePayload.startUrl, flow?.presentation?.createCustomTabsIntent(webView.context))
                                return@launch
                            }

                            is NativePayload.Sso -> {
                                logger.info("Launching custom tab for sso")
                                launchCustomTab(webView.context, nativePayload.startUrl, flow?.presentation?.createCustomTabsIntent(webView.context))
                                return@launch
                            }

                            is NativePayload.WebAuthnCreate -> {
                                logger.info("Attempting to create new a passkey")
                                nativeResponse.put("transactionId", nativePayload.transactionId)
                                val res = performRegister(webView.context, nativePayload.options)
                                nativeResponse.put("response", res)
                            }

                            is NativePayload.WebAuthnGet -> {
                                logger.info("Attempting to use an existing passkey")
                                nativeResponse.put("transactionId", nativePayload.transactionId)
                                val res = performAssertion(webView.context, nativePayload.options)
                                nativeResponse.put("response", res)
                            }
                        }
                    } catch (e: DescopeException) {
                        type = "failure"
                        val failure = when (e) {
                            DescopeException.oauthNativeCancelled -> {
                                logger.info("OAuth native canceled")
                                canceled = true
                                "OAuthNativeCancelled"
                            }
                            DescopeException.oauthNativeFailed -> {
                                logger.error("OAuth native failed", e)
                                "OAuthNativeFailed"
                            }
                            DescopeException.passkeyCancelled -> {
                                logger.info("Passkeys canceled")
                                canceled = true
                                "PasskeyCanceled"
                            }
                            DescopeException.passkeyFailed -> {
                                logger.error("Passkeys failed", e)
                                "PasskeyFailed"
                            }
                            DescopeException.passkeyNoPasskeys -> {
                                logger.error("No passkeys are available", e)
                                "PasskeyNoPasskeys"
                            }
                            DescopeException.customTabFailed -> {
                                logger.error("Failed to launch custom tab", e)
                                "CustomTabFailure"
                            }
                            else -> {
                                logger.error("Native execution failed", e)
                                "NativeFailed"
                            }
                        }
                        nativeResponse.put("failure", failure)
                    }

                    // we call the callback even when we fail unless the user canceled the operation
                    if (!canceled) {
                        call("handleResponse", type, nativeResponse.toString())
                    }
                }
            }
        }, "flow")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (request.isRedirect) return false
                logger.info("Flow attempting to navigate to a URL", uri)
                return when (listener?.onNavigation(uri) ?: OpenBrowser) {
                    Inline -> false
                    DoNothing -> true
                    OpenBrowser -> { 
                        try {
                            launchCustomTab(webView.context, uri, flow?.presentation?.createCustomTabsIntent(webView.context))
                        } catch (e: DescopeException) {
                            logger.error("Failed to open URL in browser", e)
                        }
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                logger.info("On page started", url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (loadFailure) {
                    logger.info("Page finished after error", url)
                    loadFailure = false
                    return
                }
                logger.info("On page finished", url, view?.progress)
                if (alreadySetUp) {
                    logger.error("Bridge is already set up", url, view?.progress)
                    return
                }
                alreadySetUp = true
                handleLoaded()
                
                val script = makeSetupScript(DescopeSystemInfo.getInstance(context))
                view?.evaluateJavascript(script, {})
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    logger.error("Error loading flow page", error?.errorCode, error?.description)
                    if (scheduleRetryAfterError()) return
                    val code = error?.errorCode ?: 0
                    val failure = error?.description?.toString() ?: ""
                    val message = when (code) {
                        ERROR_HOST_LOOKUP -> if ("INTERNET_DISCONNECTED" in failure) "The Internet connection appears to be offline" else "The server could not be found"
                        ERROR_CONNECT -> "Failed to connect to the server"
                        ERROR_TIMEOUT -> "The connection timed out"
                        else -> "The URL failed to load${if (failure.isBlank()) "" else " ($failure)"}"
                    }
                    val exception = DescopeException.networkError.with(message = message)
                    handleError(exception)
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true) {
                    logger.error("Flow page failed to load", errorResponse?.statusCode)
                    val statusCode = errorResponse?.statusCode ?: 0
                    if (statusCode >= 500 && scheduleRetryAfterError()) return // potentially retry only on server errors
                    val message = failureFromResponseCode(statusCode)
                    val exception = DescopeException.networkError.with(message = message)
                    handleError(exception)
                }
            }

            private fun scheduleRetryAfterError(): Boolean {
                val retryIn = attempts * retryInterval
                if (
                    alreadySetUp // initial loading was successful
                    || System.currentTimeMillis() - startedAt + retryIn > retryWindow // or retry window exceeded
                ) {
                    return false
                }

                loadFailure = true
                logger.info("Will retry to load in $retryIn ms")
                val ref = WeakReference(this@DescopeFlowCoordinator)
                handler.postDelayed(createRetryRunnable(ref), retryIn)
                return true
            }
        }
    }

    // Public API

    fun runJavaScript(code: String) {
        webView.evaluateJavascript(code.javaScriptAnonymousFunction(), {})
    }

    fun addStyles(css: String) {
        runJavaScript(
            """
const styles = ${css.javaScriptLiteralString()}
const element = document.createElement('style')
element.textContent = styles
document.head.appendChild(element)
"""
        )
    }

    // Internal API

    internal fun startFlow(flow: DescopeFlow) {
        this.flow = flow
        handleStarted()
        webView.loadUrl(flow.url)
    }
    
    internal fun reloadFlow() {
        val flowUrl = flow?.url ?: return
        attempts++
        logger.info("Retrying to load flow (attempt $attempts)")
        webView.loadUrl(flowUrl)
    }

    internal fun resumeFromDeepLink(deepLink: Uri) {
        if (flow == null) {
            logger.error("resumeFromDeepLink cannot be called before startFlow")
            return
        }
        activityHelper.closeCustomTab(webView.context)
        val response = JSONObject().apply { put("url", deepLink.toString()) }
        val type = if (deepLink.queryParameterNames.contains("t")) "magicLink" else "oauthWeb"
        call("handleResponse", type, response.toString())
    }
    
    // State
    
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

        call("initialize", nativeOptions.toString(), refreshJwt, clientInputs)
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
    
    internal fun periodicRefreshJwtUpdate() {
        handler.post {
            val refreshJwt = currentSession?.refreshJwt ?: ""
            call("updateRefreshJwt", refreshJwt)
        }
    }

    // Events

    private fun handleStarted() {
        if (state == Started) return
        state = Started
        alreadySetUp = false
        startedAt = System.currentTimeMillis()
        attempts++
        executeHooks(Event.Started)
    }

    private fun handleLoaded() {
        if (state != Started) return
        executeHooks(Event.Loaded)
    }
    
    private fun handleFound(attributes: JSONObject) {
        refreshCookieName = attributes.stringOrEmptyAsNull("refreshCookieName") ?: refreshCookieName
        initialize()
    }

    private fun handleReady(tag: String) {
        if (ensureState(Started)) return
        logger.info("Flow is ready ($tag)")
        startTimer()
        state = Ready
        attempts = 0
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
            attempts = 0
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
            attempts = 0
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
            jwtServerResponse.refreshJwt = jwtServerResponse.refreshJwt ?: findJwtInCookies(REFRESH_COOKIE_NAME, respCookieString, urlCookieString)
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
    
    // Utils
    
    private fun call(function: String, vararg params: String) {
        val escaped = params.joinToString(", ") { it.javaScriptLiteralString() }
        val javascript = "window.descopeBridge.internal.$function($escaped)"
        webView.evaluateJavascript(javascript, {})
    }
}

// Helper Classes

internal sealed class NativePayload {
    internal class OAuthNative(val start: JSONObject) : NativePayload()
    internal class OAuthWeb(val startUrl: String) : NativePayload()
    internal class Sso(val startUrl: String) : NativePayload()
    internal class WebAuthnCreate(val transactionId: String, val options: String) : NativePayload()
    internal class WebAuthnGet(val transactionId: String, val options: String) : NativePayload()

    val type
        get() = when (this) {
            is OAuthNative -> "oauthNative"
            is OAuthWeb -> "oauthWeb"
            is Sso -> "sso"
            is WebAuthnCreate -> "webauthnCreate"
            is WebAuthnGet -> "webauthnGet"
        }

    companion object {
        fun fromJson(jsonString: String): NativePayload {
            val json = JSONObject(jsonString)
            val type = json.getString("type")
            return json.getJSONObject("payload").run {
                when (type) {
                    "oauthNative" -> OAuthNative(start = getJSONObject("start"))
                    "oauthWeb" -> OAuthWeb(startUrl = getString("startUrl"))
                    "sso" -> Sso(startUrl = getString("startUrl"))
                    "webauthnCreate" -> WebAuthnCreate(transactionId = getString("transactionId"), options = getString("options"))
                    "webauthnGet" -> WebAuthnGet(transactionId = getString("transactionId"), options = getString("options"))
                    else -> throw DescopeException.flowFailed.with(message = "Unexpected server response in flow")
                }
            }
        }
    }
}

// New JS

private fun makeSetupScript(systemInfo: SystemInfo) = """
    
window.descopeBridge = {
    hostInfo: {
        sdkName: 'android',
        sdkVersion: ${DescopeSdk.VERSION.javaScriptLiteralString()},
        platformName: 'android',
        platformVersion: ${systemInfo.platformVersion.javaScriptLiteralString()},
        appName: ${systemInfo.appName.javaScriptLiteralString()},
        appVersion: ${systemInfo.appVersion.javaScriptLiteralString()}, 
        device: ${systemInfo.device.javaScriptLiteralString()},
    },

    abortFlow(reason) {
        this.internal.aborted = true
        flow.onAbort(typeof reason == 'string' ? reason : '')
    },

    startFlow() {
        this.internal.start()
    },

    internal: {
        component: null,

        aborted: false,

        start() {
            if (this.aborted || this.connect()) {
                return
            }

            console.debug('Waiting for Descope component')

            let interval
            interval = setInterval(() => {
                if (this.aborted || this.connect()) {
                    clearInterval(interval)
                }
            }, 20)
        },

        connect() {
            this.component ||= document.querySelector('descope-wc')
            if (!this.component) {
                return false
            }

            const attributes = {
                refreshCookieName: this.component.refreshCookieName || null,
            }
            
            flow.onFound(JSON.stringify(attributes))
            return true
        },

        initialize(nativeOptions, refreshJwt, clientInputs) {
            // update webpage sdk headers and print sdk type and version to native log
            this.updateConfigHeaders()

            this.component.nativeOptions = JSON.parse(nativeOptions)
            this.updateRefreshJwt(refreshJwt)
            this.updateClientInputs(clientInputs)
            
            if (this.component.flowStatus === 'error') {
                flow.onError('The flow failed during initialization')
            } else if (this.component.flowStatus === 'ready' || this.component.shadowRoot?.querySelector('descope-container')) {
                this.postReady('immediate') // can only happen in old web-components without lazy init
            } else {
                this.component.addEventListener('ready', () => {
                    this.postReady('listener')
                })
            }

            this.component.addEventListener('bridge', (event) => {
                flow.native(JSON.stringify(event.detail), window.location.href)
            })

            this.component.addEventListener('error', (event) => {
                flow.onError(JSON.stringify(event.detail))
            })

            this.component.addEventListener('success', (event) => {
                flow.onSuccess(event.detail ? JSON.stringify(event.detail) : null, window.location.href)
            })

            // ensure we support old web-components without this function
            this.component.lazyInit?.()

            return true
        },

        postReady(tag) {
            if (!this.component.bridgeVersion) {
                flow.onError('The flow is using an unsupported web component version')
                return
            }
            this.component.sdk.webauthn.helpers.isSupported = async () => $isWebAuthnSupported
            this.disableTouchInteractions()
            flow.onReady(tag)
        },

        updateConfigHeaders() {
            const config = window.customElements?.get('descope-wc')?.sdkConfigOverrides || {}

            const headers = config?.baseHeaders || {}
            console.debug(`Descope ${"$"}{headers['x-descope-sdk-name'] || 'unknown'} package version "${"$"}{headers['x-descope-sdk-version'] || 'unknown'}"`)

            const hostInfo = window.descopeBridge.hostInfo
            headers['x-descope-bridge-name'] = hostInfo.sdkName
            headers['x-descope-bridge-version'] = hostInfo.sdkVersion
            headers['x-descope-platform-name'] = hostInfo.platformName
            headers['x-descope-platform-version'] = hostInfo.platformVersion
            if (hostInfo.appName) {
                headers['x-descope-app-name'] = hostInfo.appName
            }
            if (hostInfo.appVersion) {
                headers['x-descope-app-version'] = hostInfo.appVersion
            }
            if (hostInfo.device) {
                headers['x-descope-device'] = hostInfo.device
            }
        },

        disableTouchInteractions() {
            const stylesheet = document.createElement("style")
            stylesheet.textContent = `
                * {
                    user-select: none;
                }
            `
            document.head.appendChild(stylesheet)
        
            this.component.injectStyle?.(`
                #content-root * {
                    user-select: none;
                }
            `)

            this.component.shadowRoot?.querySelectorAll('descope-enriched-text').forEach(t => {
                t.shadowRoot?.querySelectorAll('a').forEach(a => {
                    a.draggable = false
                })
            })

            this.component.shadowRoot?.querySelectorAll('img').forEach(a => {
                a.draggable = false
            })
        },

        updateRefreshJwt(refreshJwt) {
            if (refreshJwt) {
                const storagePrefix = this.component.storagePrefix || ''
                const storageKey = storagePrefix + ${REFRESH_COOKIE_NAME.javaScriptLiteralString()}
                window.localStorage.setItem(storageKey, refreshJwt)
            }
        },

        updateClientInputs(inputs) {
            let client = {}
            try {
                client = JSON.parse(this.component.getAttribute('client') || '{}')
            } catch (e) {}
            client = {
                ...client,
                ...JSON.parse(inputs || '{}'),
            }
            this.component.setAttribute('client', JSON.stringify(client))
        },

        handleResponse(type, payload) {
            this.component.nativeResume(type, payload)
        },
    }
}

window.descopeBridge.startFlow()

"""

// JavaScript

private fun String?.javaScriptLiteralString() = if (this == null) "''"
    else "`"+replace("\\", "\\\\")
        .replace("$", "\\$")
        .replace("`", "\\`") + "`"

private fun String.javaScriptAnonymousFunction() = """
    (function() {
        $this
    })()
"""

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

// URI

private fun String.toUri(): Uri? {
    return try {
        Uri.parse(this)
    } catch (_: Exception) {
        null
    }
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

private fun createRetryRunnable(ref: WeakReference<DescopeFlowCoordinator>): (Runnable) {
    return object : Runnable {
        override fun run() {
            val coordinator = ref.get()
            if (coordinator == null) {
                return
            } else {
                coordinator.reloadFlow()
            }
        }
    }
}
