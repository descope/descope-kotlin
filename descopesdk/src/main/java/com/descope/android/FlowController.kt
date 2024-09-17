package com.descope.android

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.descope.internal.http.JwtServerResponse
import com.descope.internal.others.activityHelper
import com.descope.internal.others.with
import com.descope.internal.routes.convert
import com.descope.sdk.DescopeFlow
import com.descope.sdk.DescopeSdk
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
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
                println("READY!!!")
                onReadyCallback?.invoke()
            }

            @JavascriptInterface
            fun onError(error: String) {
                println("Error!!! $error")
                onErrorCallback?.invoke(DescopeException.flowFailed.with(desc = error))
            }

            @JavascriptInterface
            fun onSuccess(success: String) {
                println("SUCCESS!!! $success")
                val cookieString: String? = CookieManager.getInstance().getCookie("https://api.descope.com")
                println("COOKIES: $cookieString")
                val cookies = mutableListOf<HttpCookie>().apply {
                    cookieString?.split("; ")?.forEach {
                        try {
                            addAll(HttpCookie.parse(it))
                        } catch (ignored: Exception) {
                        }
                    }
                }
                println("refresh JWT: $cookies")

                val jwtServerResponse = JwtServerResponse.fromJson(success, cookies)
                onSuccessCallback?.invoke(jwtServerResponse.convert())
            }
        }, "flow")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(jsScripts) { result ->
                    println("JS Execution result: $result")
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request?.url?.host?.contains("google") == true) {
                    println("Intercepted url loading: ${request.url}")
                    launchUri(webView.context, request.url)
                    return true
                }
                return super.shouldOverrideUrlLoading(view, request)
            }
        }
    }
    
    // API

    fun startFlow(flowUrl: String) {
        this.flowUrl = flowUrl
        webView.loadUrl(flowUrl)
    }

    fun resumeFromDeepLink(incomingUriString: String) {
        println("resume")
        if (!this::flowUrl.isInitialized) throw DescopeException.flowFailed.with(desc = "`resumeFromDeepLink` cannot be called before `startFlow`")
        activityHelper.closeCustomTab(webView.context)
        println("resume called with $incomingUriString")
        // create the redirect flow URL by copying all url parameters received from the incoming URI
        val incomingUri = Uri.parse(incomingUriString)
        val uriBuilder = Uri.parse(flowUrl).buildUpon()
        incomingUri.queryParameterNames.forEach { uriBuilder.appendQueryParameter(it, incomingUri.getQueryParameter(it)) }
        val uri = uriBuilder.build()

        println("resuming with $uri")
        webView.loadUrl(uri.toString())
    }

    // Custom Tab

    private fun launchUri(context: Context, uri: Uri) {
        val customTabsIntent = flowPresentation?.createCustomTabsIntent(context) ?: defaultCustomTabIntent()
        activityHelper.openCustomTab(context, customTabsIntent, uri)
    }
}

private val jsScripts = """
function waitWebComponent() {
    document.body.style.background = 'transparent'

    let id
    id = setInterval(() => {
        let wc = document.getElementsByTagName('descope-wc')[0]
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
        flow.onReady();
    })
}

waitWebComponent();
    """.trimIndent()