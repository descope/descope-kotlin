package com.descope.android

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.descope.internal.http.REFRESH_COOKIE_NAME
import com.descope.internal.http.failureFromResponseCode
import com.descope.internal.others.debug
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.internal.others.isUnsafeEnabled
import com.descope.internal.others.stringOrEmptyAsNull
import com.descope.internal.others.with
import com.descope.internal.routes.isWebAuthnSupported
import com.descope.sdk.DescopeLogger
import com.descope.sdk.DescopeSdk
import com.descope.types.DescopeException
import org.json.JSONObject
import java.lang.ref.WeakReference

private const val retryWindow = 10 * 1000L
private const val retryInterval = 1250L

// Bridge

@SuppressLint("SetJavaScriptEnabled")
internal class FlowBridge(val webView: WebView) {

    interface Listener {
        fun onLoaded()
        fun onFound()
        fun onReady(tag: String)
        fun onRequest(request: FlowBridgeRequest)
        fun onNavigation(uri: Uri): Boolean
        fun onSuccess(data: String?, url: String)
        fun onError(error: DescopeException)
    }

    var listener: Listener? = null
    var logger: DescopeLogger? = null
    var attributes = FlowBridgeAttributes()

    private val handler = Handler(Looper.getMainLooper())
    private var alreadySetUp = false
    private var startedAt = 0L
    private var attempts = 0

    // JavaScript Interface

    private val javascriptInterface = object {
        @JavascriptInterface fun onFound(data: String) = bridgeOnFound(data)
        @JavascriptInterface fun onReady(tag: String) = bridgeOnReady(tag)
        @JavascriptInterface fun onSuccess(data: String?, url: String) = bridgeOnSuccess(data, url)
        @JavascriptInterface fun onAbort(reason: String) = bridgeOnAbort(reason)
        @JavascriptInterface fun onError(error: String) = bridgeOnError(error)
        @JavascriptInterface fun native(response: String?, url: String) = bridgeOnNative(response, url)
        @JavascriptInterface fun onLog(tag: String, message: String) = bridgeOnLog(tag, message)
    }

    private fun bridgeOnFound(data: String) {
        logger.info("Received found event")
        val json = JSONObject(data)
        handler.post {
            attributes.refreshCookieName = json.stringOrEmptyAsNull("refreshCookieName")
            listener?.onFound()
        }
    }

    private fun bridgeOnReady(tag: String) {
        handler.post {
            listener?.onReady(tag)
        }
    }

    private fun bridgeOnSuccess(data: String?, url: String) {
        listener?.onSuccess(data, url)
    }

    private fun bridgeOnAbort(reason: String) {
        val error = if (reason.isNotEmpty()) {
            logger.error("Flow aborted with a failure reason", reason)
            DescopeException.flowFailed.with(message = reason)
        } else {
            logger.info("Flow aborted with cancellation")
            DescopeException.flowCancelled
        }
        listener?.onError(error)
    }

    private fun bridgeOnError(error: String) {
        listener?.onError(DescopeException.flowFailed.with(message = error))
    }

    private fun bridgeOnNative(response: String?, url: String) {
        if (response == null) {
            logger.info("Skipping bridge call because response is null")
            return
        }
        handler.post {
            try {
                val request = FlowBridgeRequest.fromJson(response)
                listener?.onRequest(request)
            } catch (e: DescopeException) {
                listener?.onError(e)
            }
        }
    }

    private fun bridgeOnLog(tag: String, message: String) {
        if (tag == "fail") {
            logger.error("Bridge encountered script error in webpage", message)
        } else if (logger.isUnsafeEnabled) {
            val logMessage = "Webview console.$tag: $message"
            when (tag) {
                "error" -> logger.error(logMessage)
                "warn", "info", "log" -> logger.info(logMessage)
                else -> logger.debug(logMessage)
            }
        }
    }

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true

        webView.addJavascriptInterface(javascriptInterface, "flow")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                val message = consoleMessage?.message() ?: return false
                if (consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    logger?.error("WebView console.error", message)
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (request.isRedirect) return false
                logger.info("Flow attempting to navigate to a URL", uri)
                return listener?.onNavigation(uri) ?: true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                logger.info("On page started", url)
                if (alreadySetUp) {
                    logger.error("Bridge is already set up", url, view?.progress)
                    return
                }
                alreadySetUp = true

                view?.evaluateJavascript(loggingScript, {})

                val setupScript = makeSetupScript(DescopeSystemInfo.getInstance(webView.context))
                view?.evaluateJavascript(setupScript, {})

                listener?.onLoaded()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                logger.info("On page finished", url, view?.progress)
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
                    listener?.onError(exception)
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                if (request?.isForMainFrame == true) {
                    logger.error("Flow page failed to load", errorResponse?.statusCode)
                    val statusCode = errorResponse?.statusCode ?: 0
                    if (statusCode >= 500 && scheduleRetryAfterError()) return
                    val message = failureFromResponseCode(statusCode)
                    val exception = DescopeException.networkError.with(message = message)
                    listener?.onError(exception)
                }
            }

            private fun scheduleRetryAfterError(): Boolean {
                val retryIn = attempts * retryInterval
                if (
                    alreadySetUp
                    || System.currentTimeMillis() - startedAt + retryIn > retryWindow
                ) {
                    return false
                }

                logger.info("Will retry to load in $retryIn ms")
                val ref = WeakReference(this@FlowBridge)
                handler.postDelayed(createRetryRunnable(ref), retryIn)
                return true
            }
        }
    }

    // Lifecycle

    fun start(url: String) {
        alreadySetUp = false
        startedAt = System.currentTimeMillis()
        attempts = 1
        webView.loadUrl(url)
    }

    fun reload(url: String) {
        attempts++
        logger.info("Retrying to load flow (attempt $attempts)")
        webView.loadUrl(url)
    }

    // Bridge API

    fun initialize(nativeOptions: String, refreshJwt: String, clientInputs: String) {
        call("initialize", nativeOptions, refreshJwt, clientInputs)
    }

    fun updateRefreshJwt(refreshJwt: String) {
        call("updateRefreshJwt", refreshJwt)
    }

    fun postResponse(response: FlowBridgeResponse) {
        call("handleResponse", response.typeName, response.payload)
    }

    // Public Utilities

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

    // Private

    private fun call(function: String, vararg params: String) {
        val escaped = params.joinToString(", ") { it.javaScriptLiteralString() }
        val javascript = "window.descopeBridge.internal.$function($escaped)"
        webView.evaluateJavascript(javascript, {})
    }
}

// Supporting Types

internal data class FlowBridgeAttributes(
    var refreshCookieName: String? = null,
)

internal sealed class FlowBridgeRequest {
    class OAuthNative(val start: JSONObject) : FlowBridgeRequest()
    class OAuthWeb(val startUrl: String) : FlowBridgeRequest()
    class Sso(val startUrl: String) : FlowBridgeRequest()
    class WebAuthnCreate(val transactionId: String, val options: String) : FlowBridgeRequest()
    class WebAuthnGet(val transactionId: String, val options: String) : FlowBridgeRequest()

    val type
        get() = when (this) {
            is OAuthNative -> "oauthNative"
            is OAuthWeb -> "oauthWeb"
            is Sso -> "sso"
            is WebAuthnCreate -> "webauthnCreate"
            is WebAuthnGet -> "webauthnGet"
        }

    companion object {
        fun fromJson(jsonString: String): FlowBridgeRequest {
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

internal sealed class FlowBridgeResponse {
    class OAuthNative(val stateId: String, val identityToken: String) : FlowBridgeResponse()
    class WebAuthn(val type: String, val transactionId: String, val response: String) : FlowBridgeResponse()
    class WebAuth(val type: String, val url: String) : FlowBridgeResponse()
    class MagicLink(val url: String) : FlowBridgeResponse()
    class Failure(val failure: String) : FlowBridgeResponse()

    val typeName: String
        get() = when (this) {
            is OAuthNative -> "oauthNative"
            is WebAuthn -> type
            is WebAuth -> type
            is MagicLink -> "magicLink"
            is Failure -> "failure"
        }

    val payload: String
        get() = when (this) {
            is OAuthNative -> JSONObject().apply {
                put("nativeOAuth", JSONObject().apply {
                    put("stateId", stateId)
                    put("idToken", identityToken)
                })
            }.toString()
            is WebAuthn -> JSONObject().apply {
                put("transactionId", transactionId)
                put("response", response)
            }.toString()
            is WebAuth -> JSONObject().apply { put("url", url) }.toString()
            is MagicLink -> JSONObject().apply { put("url", url) }.toString()
            is Failure -> JSONObject().apply { put("failure", failure) }.toString()
        }
}


// Retry

private fun createRetryRunnable(ref: WeakReference<FlowBridge>) = Runnable {
    val bridge = ref.get() ?: return@Runnable
    val url = bridge.webView.url ?: return@Runnable
    bridge.reload(url)
}

// JavaScript

private fun String?.javaScriptLiteralString() = if (this == null) "''"
    else "`" + replace("\\", "\\\\")
        .replace("$", "\\$")
        .replace("`", "\\`") + "`"

private fun String.javaScriptAnonymousFunction() = """
    (function() {
        $this
    })()
"""

private const val loggingScript = """
(function() {
    function stringify(args) {
        return Array.from(args).map(arg => {
            if (!arg) return ""
            if (typeof arg === 'string') return arg
            return JSON.stringify(arg)
        }).join(' ')
    }
    window.onerror = function() { flow.onLog('fail', stringify(arguments)); return true; };
    window.console.error = function() { flow.onLog('error', stringify(arguments)); };
    window.console.warn = function() { flow.onLog('warn', stringify(arguments)); };
    window.console.info = function() { flow.onLog('info', stringify(arguments)); };
    window.console.debug = function() { flow.onLog('debug', stringify(arguments)); };
    window.console.log = function() { flow.onLog('log', stringify(arguments)); };
})();
"""

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
        webauthn: $isWebAuthnSupported,
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
                const response = (event.detail && Object.keys(event.detail).length) ? JSON.stringify(event.detail) : null
                flow.onSuccess(response, window.location.href)
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
