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
        // Per-component registration. Replaces the pre-multi-flow `onFound` single-component signal.
        fun onRegister(wcKey: String, attributes: FlowBridgeAttributes)
        fun onUnregister(wcKey: String)
        fun onReady(wcKey: String, tag: String)
        fun onRequest(wcKey: String, request: FlowBridgeRequest)
        fun onNavigation(uri: Uri): Boolean
        fun onSuccess(wcKey: String, data: String?, url: String)
        fun onError(wcKey: String, error: DescopeException)
        // CustomEvents dispatched on the <descope-user-profile-widget> element:
        // "ready" / "error" / "widget-logout". `detail` is the stringified event
        // detail, or empty string if absent.
        fun onWidgetEvent(name: String, detail: String)
    }

    var flow: DescopeFlow? = null
    var listener: Listener? = null
    var logger: DescopeLogger? = null

    private val handler = Handler(Looper.getMainLooper())
    private var alreadySetUp = false
    private var startedAt = 0L
    private var attempts = 0

    // JavaScript Interface

    private val javascriptInterface = object {
        @JavascriptInterface fun onRegister(wcKey: String, data: String) = bridgeOnRegister(wcKey, data)
        @JavascriptInterface fun onUnregister(wcKey: String) = bridgeOnUnregister(wcKey)
        @JavascriptInterface fun onReady(wcKey: String, tag: String) = bridgeOnReady(wcKey, tag)
        @JavascriptInterface fun onSuccess(wcKey: String, data: String?, url: String) = bridgeOnSuccess(wcKey, data, url)
        @JavascriptInterface fun onAbort(reason: String) = bridgeOnAbort(reason)
        @JavascriptInterface fun onError(wcKey: String, error: String) = bridgeOnError(wcKey, error)
        @JavascriptInterface fun native(wcKey: String, response: String?) = bridgeOnNative(wcKey, response)
        @JavascriptInterface fun onWidgetEvent(name: String, detail: String) = bridgeOnWidgetEvent(name, detail)
        @JavascriptInterface fun onLog(tag: String, message: String) = bridgeOnLog(tag, message)
    }

    private fun bridgeOnRegister(wcKey: String, data: String) {
        logger.info("Component registered", wcKey)
        val json = JSONObject(data)
        val attributes = FlowBridgeAttributes(refreshCookieName = json.stringOrEmptyAsNull("refreshCookieName"))
        handler.post {
            listener?.onRegister(wcKey, attributes)
        }
    }

    private fun bridgeOnUnregister(wcKey: String) {
        logger.info("Component unregistered", wcKey)
        handler.post {
            listener?.onUnregister(wcKey)
        }
    }

    private fun bridgeOnReady(wcKey: String, tag: String) {
        handler.post {
            listener?.onReady(wcKey, tag)
        }
    }

    private fun bridgeOnSuccess(wcKey: String, data: String?, url: String) {
        handler.post {
            listener?.onSuccess(wcKey, data, url)
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
            // Abort is a top-level signal not associated with a specific component;
            // pass an empty wcKey so the View layer can recognize it and decide.
            listener?.onError("", error)
        }
    }

    private fun bridgeOnError(wcKey: String, error: String) {
        val parsed = parseServerError(error)
        val exception = when {
            parsed == null -> DescopeException.flowFailed.with(message = error)
            // Convert server flow cancellation to local flow cancellation for cohesive error handling
            parsed.code == "E102122" -> DescopeException.flowCancelled.with(message = parsed.message)
            else -> parsed
        }
        handler.post {
            listener?.onError(wcKey, exception)
        }
    }

    private fun bridgeOnNative(wcKey: String, response: String?) {
        if (response == null) {
            logger.info("Skipping bridge call because response is null", wcKey)
            return
        }
        handler.post {
            try {
                val request = FlowBridgeRequest.fromJson(response)
                listener?.onRequest(wcKey, request)
            } catch (e: DescopeException) {
                listener?.onError(wcKey, e)
            }
        }
    }

    private fun bridgeOnWidgetEvent(name: String, detail: String) {
        handler.post {
            listener?.onWidgetEvent(name, detail)
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
    }

    private fun handleConsoleMessage(message: String, level: ConsoleMessage.MessageLevel): Boolean {
        if (level == ConsoleMessage.MessageLevel.ERROR) {
            logger?.error("WebView console.error", message)
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

        val setupScript = makeSetupScript(DescopeSystemInfo.getInstance(webView.context))
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

    fun initialize(wcKey: String, nativeOptions: String, clientInputs: String) {
        call("initialize", wcKey, nativeOptions, clientInputs)
    }

    // Session-level refresh JWT write. Lands in localStorage["DSR"] — the single
    // key read by every consumer on the page (descope-wcs and the widget shell
    // alike). Idempotent; safe to call as often as needed. Empty input is a no-op
    // on the JS side so flow pages without an authenticated session work too.
    fun setRefreshJwt(refreshJwt: String) {
        call("setRefreshJwt", refreshJwt)
    }

    fun postResponse(wcKey: String, response: FlowBridgeResponse) {
        call("handleResponse", wcKey, response.typeName, response.payload)
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

private fun makeSetupScript(systemInfo: SystemInfo) = """

// Widget bridge: presence + version handshake only. The actual JS->native
// widget signals are CustomEvents dispatched on the <descope-user-profile-widget>
// element; we subscribe to them in internal.bootstrap() below.
window.descopeWidgetBridge = { version: 1 }

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

    // v3 multi-component opt-in API. descope-wc bridgeVersion >= 3 calls this
    // automatically during its own init. Returns a wcKey string that native
    // uses to route subsequent calls back to this specific element.
    register(element) {
        return window.descopeBridge.internal.registerComponent(element)
    },

    // v3 multi-component opt-in API. descope-wc bridgeVersion >= 3 calls this
    // from its disconnectedCallback. Native cleans up registry entries.
    unregister(wcKey) {
        window.descopeBridge.internal.unregisterComponent(wcKey)
    },

    abortFlow(reason) {
        this.internal.aborted = true
        flow.onAbort(typeof reason == 'string' ? reason : '')
    },

    startFlow() {
        this.internal.start()
    },

    internal: {
        // wcKey string -> descope-wc element
        components: {},
        nextWcKey: 1,
        widgetSubscribed: false,

        // Cached refresh JWT (last value from native) and the storage prefix
        // discovered when the first descope-wc or widget root mounts. Writes
        // are deferred until storagePrefix is locked so the JWT never lands
        // at the wrong key (descope-wc reads from (prefix + REFRESH_COOKIE_NAME)).
        refreshJwt: '',
        storagePrefix: null,

        aborted: false,

        // Bootstrap polls for the first descope-wc (legacy v2 fallback) and for
        // the <descope-user-profile-widget> root. v3+ descope-wcs auto-register
        // via descopeBridge.register() so polling is just a safety net for them.
        // Polling stops once either is found; later modal-opened descope-wcs
        // auto-register without needing polling.
        start() {
            if (this.aborted || this.connect()) {
                return
            }

            console.debug('Waiting for Descope component or widget')

            let interval
            interval = setInterval(() => {
                if (this.aborted || this.connect()) {
                    clearInterval(interval)
                }
            }, 20)
        },

        connect() {
            // Subscribe to the widget root if present (idempotent).
            this.subscribeToWidgetIfNeeded()
            const component = document.querySelector('descope-wc')
            if (component) {
                if (!this.isRegistered(component)) {
                    // v2 fallback: register on the element's behalf
                    this.registerComponent(component)
                }
                return true
            }
            // No descope-wc, but widget subscription alone is enough on UPW pages.
            return this.widgetSubscribed
        },

        subscribeToWidgetIfNeeded() {
            if (this.widgetSubscribed) return
            const widget = document.querySelector('descope-user-profile-widget')
            if (!widget) return
            this.widgetSubscribed = true
            // UPW shell has no storagePrefix attribute, flush the cached refresh JWT
            this.lockStoragePrefix('')
            // widget-register dispatched synchronously in connectedCallback before polling could catch it,
            // resolve the awaited Promise directly (idempotent — no-op if no resolver)
            widget.lazyInit?.()
            // lifecycle events fire later, subscribing now is in time
            const events = ['ready', 'error', 'widget-logout']
            for (const name of events) {
                widget.addEventListener(name, (event) => {
                    const detail = event && event.detail ? JSON.stringify(event.detail) : ''
                    flow.onWidgetEvent(name, detail)
                })
            }
        },

        isRegistered(element) {
            for (const key in this.components) {
                if (this.components[key] === element) return true
            }
            return false
        },

        registerComponent(element) {
            // Embed the wc's flow-id in the key for readable native-side logs
            // (e.g. "add-passkey_3"). Falls back to "wc" when no flow-id is set.
            const wcKey = (element.flowId || 'wc') + '_' + (this.nextWcKey++)
            this.components[wcKey] = element
            // First wc to mount determines the page's storage prefix; subsequent
            // wcs on the same page share it (single project per WebView).
            this.lockStoragePrefix(element.storagePrefix || '')
            this.bindComponent(wcKey, element)

            const attributes = {
                refreshCookieName: element.refreshCookieName || null,
            }
            flow.onRegister(wcKey, JSON.stringify(attributes))
            return wcKey
        },

        unregisterComponent(wcKey) {
            if (!this.components[wcKey]) return
            delete this.components[wcKey]
            flow.onUnregister(wcKey)
        },

        bindComponent(wcKey, element) {
            element.addEventListener('bridge', (event) => {
                flow.native(wcKey, JSON.stringify(event.detail))
            })

            element.addEventListener('error', (event) => {
                flow.onError(wcKey, JSON.stringify(event.detail))
            })

            element.addEventListener('success', (event) => {
                const response = (event.detail && Object.keys(event.detail).length) ? JSON.stringify(event.detail) : null
                flow.onSuccess(wcKey, response, window.location.href)
            })
        },

        initialize(wcKey, nativeOptions, clientInputs) {
            const element = this.components[wcKey]
            if (!element) {
                console.debug('Ignoring initialize for unknown wcKey', wcKey)
                return
            }

            // update webpage sdk headers and print sdk type and version to native log
            this.updateConfigHeaders()

            element.nativeOptions = JSON.parse(nativeOptions)
            this.updateClientInputs(wcKey, clientInputs)

            if (element.flowStatus === 'error') {
                flow.onError(wcKey, 'The flow failed during initialization')
            } else if (element.flowStatus === 'ready' || element.shadowRoot?.querySelector('descope-container')) {
                this.postReady(wcKey, 'immediate') // can only happen in old web-components without lazy init
            } else {
                element.addEventListener('ready', () => {
                    this.postReady(wcKey, 'listener')
                })
            }

            // ensure we support old web-components without this function
            element.lazyInit?.()
        },

        postReady(wcKey, tag) {
            const element = this.components[wcKey]
            if (!element) return
            if (!element.bridgeVersion) {
                flow.onError(wcKey, 'The flow is using an unsupported web component version')
                return
            }
            this.disableTouchInteractions(element)
            flow.onReady(wcKey, tag)
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

        disableTouchInteractions(element) {
            const stylesheet = document.createElement("style")
            stylesheet.textContent = `
                * {
                    user-select: none;
                }
            `
            document.head.appendChild(stylesheet)

            element.injectStyle?.(`
                #content-root * {
                    user-select: none;
                }
            `)

            element.shadowRoot?.querySelectorAll('descope-enriched-text').forEach(t => {
                t.shadowRoot?.querySelectorAll('a').forEach(a => {
                    a.draggable = false
                })
            })

            element.shadowRoot?.querySelectorAll('img').forEach(a => {
                a.draggable = false
            })
        },

        // Session-level write. All descope-wcs and the widget shell on a page
        // share one storagePrefix (single project per WebView), so we write
        // the session under (prefix + REFRESH_COOKIE_NAME) once the prefix
        // has been discovered. Until then we cache the JWT and write as soon
        // as something mounts.
        setRefreshJwt(refreshJwt) {
            this.refreshJwt = refreshJwt || ''
            this.writeRefreshJwt()
        },

        writeRefreshJwt() {
            if (!this.refreshJwt || this.storagePrefix === null) return
            const key = this.storagePrefix + ${REFRESH_COOKIE_NAME.javaScriptLiteralString()}
            window.localStorage.setItem(key, this.refreshJwt)
        },

        lockStoragePrefix(prefix) {
            if (this.storagePrefix !== null) return
            this.storagePrefix = prefix || ''
            this.writeRefreshJwt()
        },

        updateClientInputs(wcKey, inputs) {
            const element = this.components[wcKey]
            if (!element) return
            let client = {}
            try {
                client = JSON.parse(element.getAttribute('client') || '{}')
            } catch (e) {}
            client = {
                ...client,
                ...JSON.parse(inputs || '{}'),
            }
            element.setAttribute('client', JSON.stringify(client))
        },

        handleResponse(wcKey, type, payload) {
            const element = this.components[wcKey]
            if (!element) {
                console.debug('Ignoring handleResponse for unknown wcKey', wcKey)
                return
            }
            element.nativeResume(type, payload)
        },
    }
}

window.descopeBridge.startFlow()

"""
