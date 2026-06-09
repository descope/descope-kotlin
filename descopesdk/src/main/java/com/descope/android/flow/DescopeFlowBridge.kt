package com.descope.android.flow

import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.descope.android.SystemInfo
import com.descope.android.bridge.BridgeRequest
import com.descope.android.bridge.BridgeResponse
import com.descope.android.bridge.WebViewBridge
import com.descope.android.bridge.javaScriptLiteralString
import com.descope.android.bridge.makeHostInfoJsObject
import com.descope.internal.http.REFRESH_COOKIE_NAME
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.internal.others.stringOrEmptyAsNull
import com.descope.internal.others.with
import com.descope.sdk.DescopeLogger
import com.descope.types.DescopeException
import org.json.JSONObject

// Bridge

internal class FlowBridge(webView: WebView) {

    interface Listener {
        fun onLoaded()
        fun onFound()
        fun onReady(tag: String)
        fun onRequest(request: BridgeRequest)
        fun onNavigation(uri: Uri): Boolean
        fun onSuccess(data: String?, url: String)
        fun onError(error: DescopeException)
    }

    var flow: DescopeFlow? = null
    var listener: Listener? = null
    var logger: DescopeLogger?
        get() = bridge.logger
        set(value) { bridge.logger = value }
    var attributes = FlowBridgeAttributes()

    private val handler = Handler(Looper.getMainLooper())
    private val bridge = WebViewBridge(
        webView = webView,
        tag = "flow",
        urlProvider = { flow?.url },
        setupScriptBuilder = { systemInfo -> makeSetupScript(systemInfo) },
        onLoaded = { handler.post { listener?.onLoaded() } },
        onNavigation = { uri -> listener?.onNavigation(uri) ?: true },
        onFatalError = { error -> handler.post { listener?.onError(error) } },
    )

    // JavaScript Interface

    private val javascriptInterface = object {
        @JavascriptInterface fun onFound(data: String) = bridgeOnFound(data)
        @JavascriptInterface fun onReady(tag: String) = bridgeOnReady(tag)
        @JavascriptInterface fun onSuccess(data: String?, url: String) = bridgeOnSuccess(data, url)
        @JavascriptInterface fun onAbort(reason: String) = bridgeOnAbort(reason)
        @JavascriptInterface fun onError(error: String) = bridgeOnError(error)
        @JavascriptInterface fun native(response: String?) = bridgeOnNative(response)
        @JavascriptInterface fun onLog(tag: String, message: String) = bridge.bridgeOnLog(tag, message)
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
        handler.post {
            listener?.onSuccess(data, url)
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
            listener?.onError(error)
        }
    }

    private fun bridgeOnError(error: String) {
        handler.post {
            listener?.onError(DescopeException.flowFailed.with(message = error))
        }
    }

    private fun bridgeOnNative(response: String?) {
        if (response == null) {
            logger.info("Skipping bridge call because response is null")
            return
        }
        handler.post {
            try {
                val request = BridgeRequest.fromJson(response)
                listener?.onRequest(request)
            } catch (e: DescopeException) {
                listener?.onError(e)
            }
        }
    }

    // Init

    // kept here (not in WebViewBridge) so lint can see the @JavascriptInterface annotations on the typed object
    init {
        webView.addJavascriptInterface(javascriptInterface, "flow")
    }

    // Lifecycle

    fun start() = bridge.start()

    fun reload() = bridge.reload()

    // Bridge API

    fun initialize(nativeOptions: String, refreshJwt: String, clientInputs: String) {
        bridge.call("descopeBridge", "initialize", nativeOptions, refreshJwt, clientInputs)
    }

    fun updateRefreshJwt(refreshJwt: String) {
        bridge.call("descopeBridge", "updateRefreshJwt", refreshJwt)
    }

    fun postResponse(response: BridgeResponse) {
        bridge.call("descopeBridge", "handleResponse", response.typeName, response.payload)
    }

    // Public Utilities

    fun runJavaScript(code: String) = bridge.runJavaScript(code)

    fun addStyles(css: String) = bridge.addStyles(css)
}

// Supporting Types

internal data class FlowBridgeAttributes(
    var refreshCookieName: String? = null,
)

// JavaScript

private fun makeSetupScript(systemInfo: SystemInfo) = """

window.descopeBridge = {
    hostInfo: ${makeHostInfoJsObject(systemInfo)},

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
                flow.native(JSON.stringify(event.detail))
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
