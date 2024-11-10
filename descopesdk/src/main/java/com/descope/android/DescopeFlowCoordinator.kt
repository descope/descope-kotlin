package com.descope.android

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.descope.Descope
import com.descope.android.DescopeFlow.NavigationStrategy.DoNothing
import com.descope.android.DescopeFlow.NavigationStrategy.Inline
import com.descope.android.DescopeFlow.NavigationStrategy.OpenBrowser
import com.descope.internal.http.JwtServerResponse
import com.descope.internal.http.REFRESH_COOKIE_NAME
import com.descope.internal.http.SESSION_COOKIE_NAME
import com.descope.internal.others.activityHelper
import com.descope.internal.others.with
import com.descope.internal.routes.convert
import com.descope.internal.routes.getPackageOrigin
import com.descope.internal.routes.nativeAuthorization
import com.descope.internal.routes.performAssertion
import com.descope.internal.routes.performRegister
import com.descope.sdk.DescopeLogger
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
internal class DescopeFlowCoordinator(private val webView: WebView) {

    private lateinit var flow: DescopeFlow
    private var state: State = State.Initial
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val sdk: DescopeSdk
        get() = if (this::flow.isInitialized) flow.sdk ?: Descope.sdk else Descope.sdk
    private val logger: DescopeLogger?
        get() = sdk.client.config.logger

    private var currentFlowUrl: Uri? = null

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.settings.setSupportZoom(false)
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onReady() {
                if (state != State.Loading) {
                    logger?.log(Info, "Flow onReady called in state $state - ignoring")
                    return
                }
                state = State.Ready
                logger?.log(Info, "Flow is ready")
                handler.post {
                    flow.lifecycle?.onReady()
                }
            }

            @JavascriptInterface
            fun onSuccess(success: String, url: String) {
                if (state != State.Ready) {
                    logger?.log(Info, "Flow onSuccess called in state $state - ignoring")
                    return
                }
                state = State.Success
                logger?.log(Info, "Flow finished successfully")
                val jwtServerResponse = JwtServerResponse.fromJson(success, emptyList())
                // take tokens from cookies if missing
                val cookieString = CookieManager.getInstance().getCookie(url)
                val projectId = sdk.client.config.projectId
                jwtServerResponse.sessionJwt = jwtServerResponse.sessionJwt ?: findJwtInCookies(cookieString, projectId = projectId, name = SESSION_COOKIE_NAME)
                jwtServerResponse.refreshJwt = jwtServerResponse.refreshJwt ?: findJwtInCookies(cookieString, projectId = projectId, name = REFRESH_COOKIE_NAME)
                handler.post {
                    flow.lifecycle?.onSuccess(jwtServerResponse.convert())
                }
            }

            @JavascriptInterface
            fun onError(error: String) {
                if (state != State.Ready) {
                    logger?.log(Info, "Flow onError called in state $state - ignoring")
                    return
                }
                state = State.Error
                logger?.log(Error, "Flow finished with an exception", error)
                handler.post {
                    flow.lifecycle?.onError(DescopeException.flowFailed.with(desc = error))
                }
            }

            @JavascriptInterface
            fun native(response: String?, url: String) {
                currentFlowUrl = url.toUri()
                webView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.Main) {
                    val nativeResponse = JSONObject()
                    var type = ""
                    try {
                        if (response == null) return@launch
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
                                launchCustomTab(webView.context, nativePayload.startUrl, flow.presentation?.createCustomTabsIntent(webView.context))
                                return@launch
                            }

                            is NativePayload.Sso -> {
                                logger?.log(Info, "Launching custom tab for sso")
                                launchCustomTab(webView.context, nativePayload.startUrl, flow.presentation?.createCustomTabsIntent(webView.context))
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
                        val failure = when (e) {
                            DescopeException.oauthNativeCancelled -> "OAuthNativeCancelled"
                            DescopeException.oauthNativeFailed -> "OAuthNativeFailed"
                            DescopeException.passkeyFailed -> "PasskeyFailed"
                            DescopeException.passkeyNoPasskeys -> "PasskeyNoPasskeys"
                            DescopeException.passkeyCancelled -> "PasskeyCanceled"
                            else -> "NativeFailed"
                        }
                        nativeResponse.put("failure", failure)
                    }

                    // we call the callback even when we fail
                    webView.evaluateJavascript("document.getElementsByTagName('descope-wc')[0]?.nativeResume('$type', `${nativeResponse.toString().escapeForBackticks()}`)") {}
                }
            }
        }, "flow")
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (request.isRedirect) return false
                logger?.log(Info, "Flow attempting to navigate to a URL", uri)
                return when (flow.lifecycle?.onNavigation(uri) ?: OpenBrowser) {
                    Inline -> false
                    DoNothing -> true
                    OpenBrowser -> {
                        launchCustomTab(webView.context, uri, flow.presentation?.createCustomTabsIntent(webView.context))
                        true
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.run {
                    val isWebAuthnSupported = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    val origin = if (isWebAuthnSupported) getPackageOrigin(context) else ""
                    evaluateJavascript(
                        setupScript(
                            origin = origin,
                            oauthNativeProvider = flow.oauthProvider?.name ?: "",
                            oauthNativeRedirect = flow.oauthRedirect ?: "",
                            ssoRedirect = flow.ssoRedirect ?: "",
                            magicLinkRedirect = flow.magicLinkRedirect ?: "",
                            isWebAuthnSupported = isWebAuthnSupported,
                        )
                    ) {}
                }
            }
        }
    }

    // API

    internal fun run(flow: DescopeFlow) {
        this.flow = flow
        state = State.Loading
        webView.loadUrl(flow.uri.toString())
    }

    internal fun resumeFromDeepLink(deepLink: Uri) {
        if (!this::flow.isInitialized) throw DescopeException.flowFailed.with(desc = "`resumeFromDeepLink` cannot be called before `startFlow`")
        activityHelper.closeCustomTab(webView.context)
        val response = JSONObject().apply { put("url", deepLink.toString()) }
        val type = if (deepLink.queryParameterNames.contains("t")) "magicLink" else "oauthWeb"
        webView.evaluateJavascript("document.getElementsByTagName('descope-wc')[0]?.nativeResume('$type', `${response.toString().escapeForBackticks()}`)") {}
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

private enum class State {
    Initial,
    Loading,
    Ready,
    Success,
    Error,
}

// JS

private fun setupScript(
    origin: String,
    oauthNativeProvider: String,
    oauthNativeRedirect: String,
    ssoRedirect: String,
    magicLinkRedirect: String,
    isWebAuthnSupported: Boolean
) = """
function waitWebComponent() {
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
            prepareWebComponent(wc)
        }
    }, 20)
}

function prepareWebComponent(wc) {
    wc.nativeOptions = {
        bridgeVersion: 1,
        platform: 'android',
        oauthProvider: '$oauthNativeProvider',
        oauthRedirect: '$oauthNativeRedirect',
        ssoRedirect: '$ssoRedirect',
        magicLinkRedirect: '$magicLinkRedirect',
        origin: '$origin',
    }

    wc.addEventListener('ready', () => {
        if (!wc.bridgeVersion) {
            flow.onError('Hosted flow uses unsupported web-component SDK version');
            return
        }
        wc.sdk.webauthn.helpers.isSupported = async () => $isWebAuthnSupported
        flow.onReady();
    })
    
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

waitWebComponent();
    """.trimIndent()

private fun String.escapeForBackticks() = replace("\\", "\\\\")
    .replace("$", "\\$")
    .replace("`", "\\`")

// Cookies

internal fun findJwtInCookies(cookieString: String?, projectId: String, name: String): String? {
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
        .filter { it.projectId == projectId } // enforce projectId
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
