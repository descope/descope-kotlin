package com.descope.android

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.descope.internal.http.REFRESH_COOKIE_NAME
import com.descope.internal.http.SESSION_COOKIE_NAME
import com.descope.internal.http.failureFromResponseCode
import com.descope.internal.others.activityHelper
import com.descope.internal.others.debug
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.internal.others.isUnsafeEnabled
import com.descope.internal.others.parseServerError
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
        fun onFound(key: String)
        fun onReady(key: String, tag: String)
        fun onRequest(key: String, request: FlowBridgeRequest)
        fun onNavigation(uri: Uri): Boolean
        fun onSuccess(key: String, data: String?, url: String)
        fun onError(key: String, error: DescopeException)
        fun onLogout()
    }

    var flow: DescopeFlow? = null
    var widget = false
    var listener: Listener? = null
    var logger: DescopeLogger? = null
    var attributes = FlowBridgeAttributes()

    private val handler = Handler(Looper.getMainLooper())
    private var alreadySetUp = false
    private var startedAt = 0L
    private var attempts = 0

    // JavaScript Interface

    private val javascriptInterface = object {
        @JavascriptInterface fun onFound(key: String, data: String) = bridgeOnFound(key, data)
        @JavascriptInterface fun onReady(key: String, tag: String) = bridgeOnReady(key, tag)
        @JavascriptInterface fun onSuccess(key: String, data: String?, url: String) = bridgeOnSuccess(key, data, url)
        @JavascriptInterface fun onAbort(reason: String) = bridgeOnAbort(reason)
        @JavascriptInterface fun onError(key: String, error: String) = bridgeOnError(key, error)
        @JavascriptInterface fun onLogout() = bridgeOnLogout()
        @JavascriptInterface fun native(key: String, response: String?) = bridgeOnNative(key, response)
        @JavascriptInterface fun onLog(tag: String, message: String) = bridgeOnLog(tag, message)
    }

    private fun bridgeOnFound(key: String, data: String) {
        logger.info("Received found event", key)
        val json = JSONObject(data)
        handler.post {
            attributes.refreshCookieName = json.stringOrEmptyAsNull("refreshCookieName")
            listener?.onFound(key)
        }
    }

    private fun bridgeOnReady(key: String, tag: String) {
        handler.post {
            listener?.onReady(key, tag)
        }
    }

    private fun bridgeOnSuccess(key: String, data: String?, url: String) {
        handler.post {
            listener?.onSuccess(key, data, url)
        }
    }

    private fun bridgeOnAbort(reason: String) {
        val error = if (reason.isNotEmpty()) {
            logger.error("Flow aborted with a failure reason", reason)
            DescopeException.flowFailed.with(message = reason)
        } else {
            logger.info("Flow aborted with cancellation")
            DescopeException.flowCancelled
        }
        handler.post {
            listener?.onError("", error)
        }
    }

    private fun bridgeOnError(key: String, error: String) {
        val parsed = parseServerError(error)
        val exception = when {
            parsed == null -> DescopeException.flowFailed.with(message = error)
            // Convert server flow cancellation to local flow cancellation for cohesive error handling
            parsed.code == "E102122" -> DescopeException.flowCancelled.with(message = parsed.message, traceId = parsed.traceId)
            else -> parsed
        }
        handler.post {
            listener?.onError(key, exception)
        }
    }

    private fun bridgeOnLogout() {
        logger.info("Received logout event")
        handler.post {
            listener?.onLogout()
        }
    }

    private fun bridgeOnNative(key: String, response: String?) {
        if (response == null) {
            logger.info("Skipping bridge call because response is null")
            return
        }
        handler.post {
            try {
                val request = FlowBridgeRequest.fromJson(response)
                listener?.onRequest(key, request)
            } catch (e: DescopeException) {
                listener?.onError(key, e)
            }
        }
    }

    private fun bridgeOnLog(tag: String, message: String) {
        if (tag == "fail") {
            logger.error("Bridge encountered script error in webpage", message)
        } else if (logger.isUnsafeEnabled && !message.contains("Fetched theme")) {
            val logMessage = "Webview console.$tag: $message"
            when (tag) {
                "error" -> logger.error(logMessage)
                "warn", "info", "log" -> logger.info(logMessage)
                else -> logger.debug(logMessage)
            }
        }
    }

    // Chrome Client

    private val chromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            val message = consoleMessage?.message() ?: return false
            return handleConsoleMessage(message, consoleMessage.messageLevel())
        }
        override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
            if (!widget || filePathCallback == null || fileChooserParams == null) return false
            return handleFileChooser(filePathCallback, fileChooserParams)
        }
    }

    private fun handleConsoleMessage(message: String, level: ConsoleMessage.MessageLevel): Boolean {
        if (level == ConsoleMessage.MessageLevel.ERROR) {
            logger?.error("WebView console.error", message)
        }
        return true
    }

    private fun handleFileChooser(filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: WebChromeClient.FileChooserParams): Boolean {
        logger.info("Widget is opening a file chooser")
        try {
            activityHelper.openFileChooser(webView.context, fileChooserParams.createIntent()) { uris ->
                filePathCallback.onReceiveValue(uris)
            }
        } catch (e: DescopeException) {
            logger.error("Failed to open file chooser", e)
            return false
        }
        return true
    }

    // View Client

    private val viewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url ?: return false
            if (request.isRedirect) return false
            return handleUrlLoading(uri)
        }
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = handlePageStarted(url)
        override fun onPageFinished(view: WebView?, url: String?) = handlePageFinished(url)
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true) handleReceivedError(error?.errorCode ?: 0, error?.description?.toString())
        }
        override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
            if (request?.isForMainFrame == true) handleReceivedHttpError(errorResponse?.statusCode ?: 0)
        }
    }

    private fun handleUrlLoading(uri: Uri): Boolean {
        logger.info("Flow attempting to navigate to a URL", uri)
        return listener?.onNavigation(uri) ?: true
    }

    private fun handlePageStarted(url: String?) {
        logger.info("On page started", url)
        if (alreadySetUp) {
            logger.error("Bridge is already set up", url, webView.progress)
            return
        }
        alreadySetUp = true

        webView.evaluateJavascript(loggingScript, {})

        val setupScript = makeSetupScript(DescopeSystemInfo.getInstance(webView.context), widget)
        webView.evaluateJavascript(setupScript, {})

        listener?.onLoaded()
    }

    private fun handlePageFinished(url: String?) {
        logger.info("On page finished", url, webView.progress)
    }

    private fun handleReceivedError(errorCode: Int, description: String?) {
        logger.error("Error loading flow page", errorCode, description)
        if (scheduleRetryAfterError()) return
        val failure = description ?: ""
        val message = when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP -> if ("INTERNET_DISCONNECTED" in failure) "The Internet connection appears to be offline" else "The server could not be found"
            WebViewClient.ERROR_CONNECT -> "Failed to connect to the server"
            WebViewClient.ERROR_TIMEOUT -> "The connection timed out"
            else -> "The URL failed to load${if (failure.isBlank()) "" else " ($failure)"}"
        }
        val exception = DescopeException.networkError.with(message = message)
        listener?.onError("", exception)
    }

    private fun handleReceivedHttpError(statusCode: Int) {
        logger.error("Flow page failed to load", statusCode)
        if (statusCode >= 500 && scheduleRetryAfterError()) return
        val message = failureFromResponseCode(statusCode)
        val exception = DescopeException.networkError.with(message = message)
        listener?.onError("", exception)
    }

    private fun scheduleRetryAfterError(): Boolean {
        val retryIn = attempts * retryInterval
        if (alreadySetUp || System.currentTimeMillis() - startedAt + retryIn > retryWindow) {
            return false
        }

        logger.info("Will retry to load in $retryIn ms")
        val ref = WeakReference(this)
        handler.postDelayed(createRetryRunnable(ref), retryIn)
        return true
    }

    // Init

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.domStorageEnabled = true
        webView.addJavascriptInterface(javascriptInterface, "flow")
        webView.webChromeClient = chromeClient
        webView.webViewClient = viewClient
    }

    // Lifecycle

    fun start() {
        val url = flow?.url ?: return
        alreadySetUp = false
        startedAt = System.currentTimeMillis()
        attempts = 1
        webView.loadUrl(url)
    }

    fun reload() {
        val url = flow?.url ?: return
        attempts++
        logger.info("Retrying to load flow (attempt $attempts)")
        webView.loadUrl(url)
    }

    // Bridge API

    fun initialize(key: String, nativeOptions: String, refreshJwt: String, sessionJwt: String, clientInputs: String) {
        call(key, "initialize", nativeOptions, refreshJwt, sessionJwt, clientInputs)
    }

    fun updateRefreshJwt(refreshJwt: String) {
        call("", "updateRefreshJwt", refreshJwt)
    }

    fun updateSessionJwt(sessionJwt: String) {
        call("", "updateSessionJwt", sessionJwt)
    }

    fun postResponse(key: String, response: FlowBridgeResponse) {
        if (widget && response.typeName == "magicLink") {
            call("", "handleMagicLink", response.payload)
        } else {
            call(key, "handleResponse", response.typeName, response.payload)
        }
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

    private fun call(key: String, function: String, vararg params: String) {
        val escaped = params.joinToString(", ") { it.javaScriptLiteralString() }
        val target = if (key.isEmpty()) "internal" else "internal.flows[${key.javaScriptLiteralString()}]?"
        val javascript = "window.descopeBridge.$target.$function($escaped)"
        webView.evaluateJavascript(javascript, {})
    }
}

// Supporting Types

internal data class FlowBridgeAttributes(
    var refreshCookieName: String? = null,
)

internal sealed class FlowBridgeRequest {
    class OAuthNative(val start: JSONObject) : FlowBridgeRequest()
    class WebAuth(val variant: String, val startUrl: String) : FlowBridgeRequest()
    class WebAuthnCreate(val transactionId: String, val options: String) : FlowBridgeRequest()
    class WebAuthnGet(val transactionId: String, val options: String) : FlowBridgeRequest()

    val type
        get() = when (this) {
            is OAuthNative -> "oauthNative"
            is WebAuth -> variant
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
                    "oauthWeb", "sso", "externalAuth" -> WebAuth(variant = type, startUrl = getString("startUrl"))
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
    class DeepLink(val type: String, val url: String) : FlowBridgeResponse()
    class Failure(val failure: String) : FlowBridgeResponse()

    val typeName: String
        get() = when (this) {
            is OAuthNative -> "oauthNative"
            is WebAuthn -> type
            is DeepLink -> type
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
            is DeepLink -> JSONObject().apply { put("url", url) }.toString()
            is Failure -> JSONObject().apply { put("failure", failure) }.toString()
        }
}


// Retry

private fun createRetryRunnable(ref: WeakReference<FlowBridge>) = Runnable {
    val bridge = ref.get() ?: return@Runnable
    bridge.reload()
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

private fun makeSetupScript(systemInfo: SystemInfo, widget: Boolean) = """

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

        key: '',

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

            flow.onFound(this.key, JSON.stringify(attributes))
            return true
        },

        initialize(nativeOptions, refreshJwt, sessionJwt, clientInputs) {
            // update webpage sdk headers and print sdk type and version to native log
            this.updateConfigHeaders()

            this.component.nativeOptions = JSON.parse(nativeOptions)
            this.updateRefreshJwt(refreshJwt)
            this.updateSessionJwt(sessionJwt)
            this.updateClientInputs(clientInputs)

            if (this.component.flowStatus === 'error') {
                flow.onError(this.key, 'The flow failed during initialization')
            } else if (this.component.flowStatus === 'ready' || this.component.shadowRoot?.querySelector('descope-container')) {
                this.postReady('immediate') // can only happen in old web-components without lazy init
            } else {
                this.component.addEventListener('ready', () => {
                    this.postReady('listener')
                })
            }

            this.component.addEventListener('bridge', (event) => {
                flow.native(this.key, JSON.stringify(event.detail))
            })

            this.component.addEventListener('error', (event) => {
                flow.onError(this.key, JSON.stringify(event.detail))
            })

            this.component.addEventListener('success', (event) => {
                const response = (event.detail && Object.keys(event.detail).length) ? JSON.stringify(event.detail) : null
                flow.onSuccess(this.key, response, window.location.href)
            })

            // ensure we support old web-components without this function
            this.component.lazyInit?.()

            return true
        },

        postReady(tag) {
            if (!this.component.bridgeVersion) {
                flow.onError(this.key, 'The flow is using an unsupported web component version')
                return
            }
            this.disableTouchInteractions()
            flow.onReady(this.key, tag)
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
                const storagePrefix = this.component?.storagePrefix || ''
                const storageKey = storagePrefix + ${REFRESH_COOKIE_NAME.javaScriptLiteralString()}
                window.localStorage.setItem(storageKey, refreshJwt)
            }
        },

        updateSessionJwt(sessionJwt) {
            const storagePrefix = this.component?.storagePrefix || ''
            const storageKey = storagePrefix + ${SESSION_COOKIE_NAME.javaScriptLiteralString()}
            if (sessionJwt && this.component?.getAttribute('send-session-token') === 'true') {
                window.localStorage.setItem(storageKey, sessionJwt)
            } else {
                window.localStorage.removeItem(storageKey)
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

${if (widget) widgetScript else "window.descopeBridge.startFlow()"}

"""

private const val widgetScript = """
Object.assign(window.descopeBridge, {
    registerWidget(widget) {
        this.internal.component = widget

        widget.addEventListener('ready', () => {
            this.internal.disableTouchInteractions()
            flow.onReady('', 'widget')
        })

        widget.addEventListener('error', (event) => {
            const { code, description, message } = event.detail || {}
            const error = code ? JSON.stringify({ errorCode: code, errorDescription: description, errorMessage: message }) : message
            flow.onError('', error || 'The widget failed to load')
        })

        widget.addEventListener('widget-logout', () => {
            flow.onLogout()
        })
    },

    registerFlow(component) {
        const key = 'flow' + (++this.internal.count)
        this.internal.flows[key] = { __proto__: this.internal, component, key }
        this.internal.flows[key].connect()
        return key
    },

    unregisterFlow(key) {
        delete this.internal.flows[key]
    },
})

Object.assign(window.descopeBridge.internal, {
    flows: {},

    count: 0,

    handleMagicLink(payload) {
        const runIds = new URL(JSON.parse(payload).url).searchParams.get('descope-login-flow') || ''
        const target = Object.values(this.flows).find(f => runIds.startsWith(f.component.getAttribute('flow-id') + '|#|'))
        if (!target) {
            console.debug('No flow found for magic link')
            return
        }
        target.handleResponse('magicLink', payload)
    },
})
"""
