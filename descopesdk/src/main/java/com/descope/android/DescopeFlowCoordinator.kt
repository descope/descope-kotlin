package com.descope.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
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
import com.descope.internal.others.with
import com.descope.internal.routes.convert
import com.descope.internal.routes.getPackageOrigin
import com.descope.internal.routes.nativeAuthorization
import com.descope.internal.routes.performAssertion
import com.descope.internal.routes.performRegister
import com.descope.sdk.DescopeLogger
import com.descope.sdk.DescopeLogger.Level.Debug
import com.descope.sdk.DescopeLogger.Level.Error
import com.descope.sdk.DescopeLogger.Level.Info
import com.descope.sdk.DescopeSdk
import com.descope.session.Token
import com.descope.types.DescopeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpCookie

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
    private var currentFlowUrl: Uri? = null
    private var alreadySetUp = false

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onReady(tag: String) {
                if (state != Started) {
                    logger?.log(Debug, "Flow onReady called in state $state - ignoring")
                    return
                }
                logger?.log(Info, "Flow is ready ($tag)")
                handler.post {
                    handleReady()
                    listener?.onReady()
                }
            }

            @JavascriptInterface
            fun onSuccess(success: String, url: String) {
                if (state != Ready) {
                    logger?.log(Debug, "Flow onSuccess called in state $state - ignoring")
                    return
                }
                state = Finished
                logger?.log(Info, "Flow finished successfully")
                val jwtServerResponse = JwtServerResponse.fromJson(success, emptyList())
                // take tokens from cookies if missing
                val cookieString = CookieManager.getInstance().getCookie(url)
                jwtServerResponse.sessionJwt = jwtServerResponse.sessionJwt ?: findJwtInCookies(cookieString, name = SESSION_COOKIE_NAME)
                jwtServerResponse.refreshJwt = jwtServerResponse.refreshJwt ?: findJwtInCookies(cookieString, name = REFRESH_COOKIE_NAME)
                handler.post {
                    try {
                        val authResponse = jwtServerResponse.convert() 
                        listener?.onSuccess(authResponse)
                    } catch (e: DescopeException) {
                        logger?.log(Error, "Failed to parse authentication response", e)
                        listener?.onError(e)
                    }
                }
            }

            @JavascriptInterface
            fun onError(error: String) {
                if (state != Ready) {
                    logger?.log(Debug, "Flow onError called in state $state - ignoring")
                    return
                }
                state = Failed
                logger?.log(Error, "Flow finished with an exception", error)
                handler.post {
                    listener?.onError(DescopeException.flowFailed.with(desc = error))
                }
            }

            @JavascriptInterface
            fun native(response: String?, url: String) {
                if (response == null) {
                    logger?.log(Info, "Skipping bridge call because response is null")
                    return
                }
                currentFlowUrl = url.toUri()
                val scope = webView.findViewTreeLifecycleOwner()?.lifecycleScope
                if (scope == null) {
                    logger?.log(Error, "Unable to find lifecycle owner coroutine scope")
                    return
                }
                scope.launch(Dispatchers.Main) {
                    var type: String
                    var canceled = false
                    val nativeResponse = JSONObject()
                    try {
                        val nativePayload = NativePayload.fromJson(response)
                        type = nativePayload.type
                        when (nativePayload) {
                            is NativePayload.OAuthNative -> {
                                logger?.log(Info, "Launching system UI for native oauth")
                                val resp = nativeAuthorization(webView.context, nativePayload.start)
                                nativeResponse.put("nativeOAuth", JSONObject().apply {
                                    put("stateId", resp.stateId)
                                    put("idToken", resp.identityToken)
                                })
                            }

                            is NativePayload.OAuthWeb -> {
                                logger?.log(Info, "Launching custom tab for web-based oauth")
                                launchCustomTab(webView.context, nativePayload.startUrl, flow?.presentation?.createCustomTabsIntent(webView.context))
                                return@launch
                            }

                            is NativePayload.Sso -> {
                                logger?.log(Info, "Launching custom tab for sso")
                                launchCustomTab(webView.context, nativePayload.startUrl, flow?.presentation?.createCustomTabsIntent(webView.context))
                                return@launch
                            }

                            is NativePayload.WebAuthnCreate -> {
                                logger?.log(Info, "Attempting to create new a passkey")
                                nativeResponse.put("transactionId", nativePayload.transactionId)
                                val res = performRegister(webView.context, nativePayload.options)
                                nativeResponse.put("response", res)
                            }

                            is NativePayload.WebAuthnGet -> {
                                logger?.log(Info, "Attempting to use an existing passkey")
                                nativeResponse.put("transactionId", nativePayload.transactionId)
                                val res = performAssertion(webView.context, nativePayload.options)
                                nativeResponse.put("response", res)
                            }
                        }
                    } catch (e: DescopeException) {
                        type = "failure"
                        val failure = when (e) {
                            DescopeException.oauthNativeCancelled -> {
                                logger?.log(Info, "OAuth native canceled" )
                                canceled = true
                                "OAuthNativeCancelled"
                            }
                            DescopeException.oauthNativeFailed -> {
                                logger?.log(Error, "OAuth native failed", e)
                                "OAuthNativeFailed"
                            }
                            DescopeException.passkeyCancelled -> {
                                logger?.log(Info, "Passkeys canceled")
                                canceled = true
                                "PasskeyCanceled"
                            }
                            DescopeException.passkeyFailed -> {
                                logger?.log(Error, "Passkeys failed", e)
                                "PasskeyFailed"
                            }
                            DescopeException.passkeyNoPasskeys -> {
                                logger?.log(Error, "No passkeys are available", e)
                                "PasskeyNoPasskeys"
                            }
                            DescopeException.customTabFailed -> {
                                logger?.log(Error, "Failed to launch custom tab", e)
                                "CustomTabFailure"
                            }
                            else -> {
                                logger?.log(Error, "Native execution failed", e)
                                "NativeFailed"
                            }
                        }
                        nativeResponse.put("failure", failure)
                    }

                    // we call the callback even when we fail unless the user canceled the operation
                    if (!canceled) {
                        webView.evaluateJavascript("document.getElementsByTagName('descope-wc')[0]?.nativeResume('$type', `${nativeResponse.toString().escapeForBackticks()}`)") {}
                    }
                }
            }
        }, "flow")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (request.isRedirect) return false
                logger?.log(Info, "Flow attempting to navigate to a URL", uri)
                return when (listener?.onNavigation(uri) ?: OpenBrowser) {
                    Inline -> false
                    DoNothing -> true
                    OpenBrowser -> { 
                        try {
                            launchCustomTab(webView.context, uri, flow?.presentation?.createCustomTabsIntent(webView.context))
                        } catch (e: DescopeException) {
                            logger?.log(Error, "Failed to open URL in browser", e)
                        }
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                logger?.log(Info, "On page started", url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                logger?.log(Info, "On page finished", url, view?.progress)
                if (alreadySetUp) {
                    logger?.log(Error, "Bridge is already set up", url, view?.progress)
                    return
                }
                alreadySetUp = true
                handleLoaded()
                view?.run {
                    val isWebAuthnSupported = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    val origin = if (isWebAuthnSupported) getPackageOrigin(context) else ""
                    val useCustomSchemeFallback = shouldUseCustomSchemeUrl(context)
                    evaluateJavascript(
                        setupScript(
                            origin = origin,
                            oauthNativeProvider = flow?.oauthNativeProvider?.name ?: "",
                            oauthRedirect = pickRedirectUrl(flow?.oauthRedirect, flow?.oauthRedirectCustomScheme, useCustomSchemeFallback),
                            ssoRedirect = pickRedirectUrl(flow?.ssoRedirect, flow?.ssoRedirectCustomScheme, useCustomSchemeFallback),
                            magicLinkRedirect = flow?.magicLinkRedirect ?: "",
                            isWebAuthnSupported = isWebAuthnSupported,
                        )
                    ) {}
                }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    logger?.log(Error, "Error loading flow page", error?.errorCode, error?.description)
                    val code = error?.errorCode ?: 0
                    val failure = error?.description?.toString() ?: ""
                    val message = when (code) {
                        ERROR_HOST_LOOKUP -> if ("INTERNET_DISCONNECTED" in failure) "The Internet connection appears to be offline" else "The server could not be found"
                        ERROR_CONNECT -> "Failed to connect to the server"
                        ERROR_TIMEOUT -> "The connection timed out"
                        else -> "The URL failed to load${if (failure.isBlank()) "" else " ($failure)"}"
                    }
                    val exception = DescopeException.networkError.with(message = message)
                    handleLoadError(exception)
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true) {
                    logger?.log(Error, "Flow page failed to load", errorResponse?.statusCode)
                    val statusCode = errorResponse?.statusCode ?: 0
                    val message = failureFromResponseCode(statusCode)
                    val exception = DescopeException.networkError.with(message = message)
                    handleLoadError(exception)
                }
            }
        }
    }

    // Public API

    fun runJavaScript(code: String) {
        val javascript = anonymousFunction(code)
        webView.evaluateJavascript(javascript) {}
    }

    fun addStyles(css: String) {
        runJavaScript(
            """
const styles = `${css.escapeForBackticks()}`
const element = document.createElement('style')
element.textContent = styles
document.head.appendChild(element)
"""
        )
    }

    private fun anonymousFunction(body: String): String {
        return """
            (function() {
                $body
            })()
        """
    }

    // Internal API

    internal fun run(flow: DescopeFlow) {
        this.flow = flow
        handleStarted()
        webView.loadUrl(flow.url)
    }

    internal fun resumeFromDeepLink(deepLink: Uri) {
        if (flow == null) {
            logger?.log(Error, "resumeFromDeepLink cannot be called before startFlow")
            return
        }
        activityHelper.closeCustomTab(webView.context)
        val response = JSONObject().apply { put("url", deepLink.toString()) }
        val type = if (deepLink.queryParameterNames.contains("t")) "magicLink" else "oauthWeb"
        webView.evaluateJavascript("document.getElementsByTagName('descope-wc')[0]?.nativeResume('$type', `${response.toString().escapeForBackticks()}`)") {}
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

    // Events

    private fun handleStarted() {
        if (state == Started) return
        state = Started
        alreadySetUp = false
        executeHooks(Event.Started)
    }

    private fun handleLoaded() {
        if (state != Started) return
        executeHooks(Event.Loaded)
    }

    private fun handleLoadError(exception: DescopeException) {
        if (state != Started) {
            logger?.log(Debug, "Flow onLoadError called in state $state - ignoring")
            return
        }
        state = Failed
        logger?.log(Error, "Flow failed to load", exception)
        listener?.onError(exception)
    }

    private fun handleReady() {
        if (state != Started) return
        state = Ready
        executeHooks(Event.Ready)
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
                    else -> throw DescopeException.flowFailed.with(desc = "Unexpected server response in flow")
                }
            }
        }
    }
}

// JS

private fun setupScript(
    origin: String,
    oauthNativeProvider: String,
    oauthRedirect: String,
    ssoRedirect: String,
    magicLinkRedirect: String,
    isWebAuthnSupported: Boolean,
) = """
function flowBridgeWaitWebComponent() {
    const styles = `
        * {
          user-select: none;
        }
    `

    const stylesheet = document.createElement("style")
    stylesheet.textContent = styles
    document.head.appendChild(stylesheet)
    
    let id
    id = setInterval(() => {
        wc = document.getElementsByTagName('descope-wc')[0]
        if (wc) {
            clearInterval(id)
            flowBridgePrepareWebComponent(wc)
        }
    }, 20)
}

function flowBridgeIsReady(wc, tag) {
    if (!wc.bridgeVersion) {
        flow.onError('Hosted flow uses unsupported web-component SDK version');
        return
    }
    wc.sdk.webauthn.helpers.isSupported = async () => $isWebAuthnSupported
    flow.onReady(tag);
}

function flowBridgePrepareWebComponent(wc) {
    wc.nativeOptions = {
        bridgeVersion: 1,
        platform: 'android',
        oauthProvider: '$oauthNativeProvider',
        oauthRedirect: '$oauthRedirect',
        ssoRedirect: '$ssoRedirect',
        magicLinkRedirect: '$magicLinkRedirect',
        origin: '$origin',
    }

    if (document.querySelector('descope-wc')?.shadowRoot?.querySelector('descope-container')) {
        flowBridgeIsReady(wc, 'immediate')
    } else {
        wc.addEventListener('ready', () => {
            flowBridgeIsReady(wc, 'listener')
        })
    }
    
    wc.addEventListener('success', (e) => {
        flow.onSuccess(JSON.stringify(e.detail), window.location.href);
    })
    
    wc.addEventListener('error', (e) => {
        flow.onError(JSON.stringify(e.detail));
    })

    wc.addEventListener('bridge', (e) => {
        flow.native(JSON.stringify(e.detail), window.location.href);
    })
}

flowBridgeWaitWebComponent();
    """.trimIndent()

private fun String.escapeForBackticks() = replace("\\", "\\\\")
    .replace("$", "\\$")
    .replace("`", "\\`")

// Cookies

internal fun findJwtInCookies(cookieString: String?, name: String): String? {
    // split and aggregate all cookies 
    val cookies = mutableListOf<HttpCookie>().apply {
        cookieString?.split("; ")?.forEach {
            try {
                addAll(HttpCookie.parse(it))
            } catch (ignored: Exception) {
            }
        }
    }

    return cookies.filter { it.name == name } // filter according cookie name
        .mapNotNull { httpCookie -> // parse token
            try {
                Token(httpCookie.value)
            } catch (e: Exception) {
                null
            }
        }
        .maxByOrNull { it.issuedAt }?.jwt // take latest
}

// URI

private fun String.toUri(): Uri? {
    return try {
        Uri.parse(this)
    } catch (ignored: Exception) {
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
