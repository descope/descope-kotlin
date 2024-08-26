package com.descope.internal.flow

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import com.descope.internal.http.JwtServerResponse
import com.descope.internal.routes.convert
import com.descope.types.AuthenticationResponse
import java.net.HttpCookie

class Flow {
    private var onReadyCallback: (() -> Unit)? = null
    private var onSuccessCallback: ((AuthenticationResponse) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, flowUrl: String, onReadyCallback: () -> Unit, onSuccessCallback: (AuthenticationResponse) -> Unit): View {
        this.onReadyCallback = onReadyCallback
        this.onSuccessCallback = onSuccessCallback
        val webView = WebView(context)
        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(this, "flow")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(
                    """
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
                ) { result ->
                    println("JS Execution result: $result")
                }
            }
        }
        webView.loadUrl(flowUrl)
        return webView
    }

    @JavascriptInterface
    fun onReady() {
        println("READY!!!")
        onReadyCallback?.invoke()
    }

    @JavascriptInterface
    fun onError(error: String) {
        println("Error!!! $error")
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
}