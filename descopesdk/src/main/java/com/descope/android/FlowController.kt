package com.descope.android

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.descope.internal.http.JwtServerResponse
import com.descope.internal.others.activityHelper
import com.descope.internal.others.stringOrEmptyAsNull
import com.descope.internal.others.with
import com.descope.internal.routes.convert
import com.descope.internal.routes.getPackageOrigin
import com.descope.internal.routes.nativeAuthorization
import com.descope.internal.routes.performAssertion
import com.descope.internal.routes.performRegister
import com.descope.sdk.DescopeFlow
import com.descope.sdk.DescopeSdk
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpCookie

@SuppressLint("SetJavaScriptEnabled")
internal class FlowController(private val webView: WebView) {

    internal var flowPresentation: DescopeFlow.Presentation? = null
    internal var onReadyCallback: (() -> Unit)? = null
    internal var onErrorCallback: ((DescopeException) -> Unit)? = null
    internal var onSuccessCallback: ((AuthenticationResponse) -> Unit)? = null
    internal var descopeSdk: DescopeSdk? = null

    private lateinit var flowUrl: String

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onReady() {
                onReadyCallback?.invoke()
            }

            @JavascriptInterface
            fun onError(error: String) {
                onErrorCallback?.invoke(DescopeException.flowFailed.with(desc = error))
            }

            @JavascriptInterface
            fun native(payload: String?) {
                webView.findViewTreeLifecycleOwner()?.lifecycleScope?.launch(Dispatchers.Main) {
                    val jsonResponse = JSONObject()
                    try {
                        if (payload == null) return@launch
                        when (val nativePayload = NativePayload.fromJson(payload)) {
                            is NativePayload.OAuthNative -> {
                                val resp = nativeAuthorization(webView.context, nativePayload.response)
                                jsonResponse.put("stateId", resp.stateId)
                                jsonResponse.put("idToken", resp.identityToken)
                            }
                            is NativePayload.OAuthWeb -> {
                                launchCustomTab(webView.context, nativePayload.url, flowPresentation?.createCustomTabsIntent(webView.context))
                                return@launch
                            }
                            is NativePayload.WebAuthnCreate -> {
                                jsonResponse.put("transactionId", nativePayload.transactionId)
                                val res = performRegister(webView.context, nativePayload.options)
                                jsonResponse.put("response", res.escapeForBackticks())
                            }
                            is NativePayload.WebAuthnGet -> {
                                jsonResponse.put("transactionId", nativePayload.transactionId)
                                val res = performAssertion(webView.context, nativePayload.options)
                                jsonResponse.put("response", res.escapeForBackticks())
                            }
                        }
                    } catch (e: DescopeException) {
                        val failure = when(e) {
                            DescopeException.oauthNativeCancelled -> "AndroidOAuthNativeCancelled"
                            DescopeException.oauthNativeFailed -> "AndroidOAuthNativeFailed"
                            DescopeException.passkeyFailed -> "AndroidPasskeyFailed"
                            DescopeException.passkeyNoPasskeys -> "AndroidPasskeyNoPasskeys"
                            DescopeException.passkeyCancelled -> "AndroidPasskeyCanceled"
                            else -> "AndroidNativeFailed"
                        }
                        jsonResponse.put("failure",failure)
                    }
                    webView.evaluateJavascript(
                        """
                            wc.nativeComplete($jsonResponse)
                        """.trimIndent()
                    ) { result ->
                        println("JS Execution result: $result")
                    }
                }
            }

            @JavascriptInterface
            fun onSuccess(success: String) {
                val cookieString: String? = CookieManager.getInstance().getCookie("https://api.descope.com")
                val cookies = mutableListOf<HttpCookie>().apply {
                    cookieString?.split("; ")?.forEach {
                        try {
                            addAll(HttpCookie.parse(it))
                        } catch (ignored: Exception) {
                        }
                    }
                }

                val jwtServerResponse = JwtServerResponse.fromJson(success, cookies)
                onSuccessCallback?.invoke(jwtServerResponse.convert())
            }
        }, "flow")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.run {
                    val isWebAuthnSupported = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    val origin = if (isWebAuthnSupported) getPackageOrigin(context) else ""
                    evaluateJavascript(setupScript(origin, "",isWebAuthnSupported)) {} // TODO: Accept native oauth provider from somewhere
                }
            }
        }
    }

    // API

    fun startFlow(flowUrl: String) {
        this.flowUrl = flowUrl
        webView.loadUrl(flowUrl)
    }

    fun resumeFromDeepLink(incomingUriString: String) {
        if (!this::flowUrl.isInitialized) throw DescopeException.flowFailed.with(desc = "`resumeFromDeepLink` cannot be called before `startFlow`")
        activityHelper.closeCustomTab(webView.context)
        // create the redirect flow URL by copying all url parameters received from the incoming URI
        val incomingUri = Uri.parse(incomingUriString)
        val uriBuilder = Uri.parse(flowUrl).buildUpon()
        incomingUri.queryParameterNames.forEach { uriBuilder.appendQueryParameter(it, incomingUri.getQueryParameter(it)) }
        val uri = uriBuilder.build()
        // load the new URL
        webView.loadUrl(uri.toString())
    }

}

// Helper Classes

internal sealed class NativePayload {
    internal class OAuthNative(val response: JSONObject): NativePayload()
    internal class OAuthWeb(val url: String, val defaultProvider: String?): NativePayload()
    internal class WebAuthnCreate(val transactionId: String, val options: String): NativePayload()
    internal class WebAuthnGet(val transactionId: String, val options: String): NativePayload()
    
    companion object {
        fun fromJson(json: String): NativePayload = JSONObject(json).run {
            println(this.toString(4))
            return when(getString("type")) {
                "oauthNative" -> OAuthNative(response = getJSONObject("response"))
                "oauthWeb" -> OAuthWeb(url = getString("url"), defaultProvider = stringOrEmptyAsNull("defaultProvider"))
                "webauthnCreate" -> WebAuthnCreate(transactionId = getString("transactionId"), options = getString("options"))
                "webauthnGet" -> WebAuthnGet(transactionId = getString("transactionId"), options = getString("options"))
                else -> throw DescopeException.flowFailed.with(desc = "Unexpected server response in flow")
            }
        }
    }
}

// JS

private fun setupScript(origin: String, oauthNativeProvider: String, isWebAuthnSupported: Boolean) = """
let wc

function waitWebComponent() {
    document.body.style.background = 'transparent'

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
    const parent = wc?.parentElement?.parentElement
    if (parent) {
        parent.style.boxShadow = 'unset'
    }

    wc.addEventListener('error', (e) => {
        flow.onError(JSON.stringify(e.detail));
    })

    wc.addEventListener('success', (e) => {
        flow.onSuccess(JSON.stringify(e.detail));
    })

    wc.addEventListener('ready', () => {
        wc.sdk.webauthn.helpers.isSupported = async () => $isWebAuthnSupported
        wc.updateNativeState('android', '$origin', '$oauthNativeProvider')
        flow.onReady();
    })
    
    wc.addEventListener('native', (e) => {
        flow.native(JSON.stringify(e.detail));
    })
}

waitWebComponent();
    """.trimIndent()

private fun String.escapeForBackticks() = replace("\\", "\\\\")
    .replace("$", "\\$")
    .replace("`", "\\`")
