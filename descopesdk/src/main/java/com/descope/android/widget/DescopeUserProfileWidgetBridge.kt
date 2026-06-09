package com.descope.android.widget

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

internal const val REQUIRED_WC_BRIDGE_VERSION = 3
internal const val WIDGET_BRIDGE_VERSION = 1

// Bridge

internal class WidgetBridge(webView: WebView) {

    interface Listener {
        fun onLoaded()
        fun onRegisterWidget()
        fun onRegisterComponent(handle: String, meta: JSONObject)
        fun onUnregisterComponent(handle: String)
        fun onWidgetReady()
        fun onWidgetError(error: DescopeException)
        fun onRequestLogout()
        fun onRequest(request: BridgeRequest)
        fun onNavigation(uri: Uri): Boolean
        fun onComponentError(handle: String?, error: DescopeException)
    }

    var widget: DescopeUserProfileWidget? = null
    var listener: Listener? = null
    var logger: DescopeLogger?
        get() = bridge.logger
        set(value) { bridge.logger = value }
    // per-component analogue of Flow's `attributes` — multiple <descope-wc>s, keyed by handle.
    // LinkedHashMap is required: the coordinator routes magic-link deep links via `.keys.lastOrNull()`.
    val componentAttributes: LinkedHashMap<String, WidgetComponentAttributes> = LinkedHashMap()

    private val handler = Handler(Looper.getMainLooper())
    private var wcVersionValidated = false
    private val bridge = WebViewBridge(
        webView = webView,
        tag = "widget",
        urlProvider = { widget?.url },
        setupScriptBuilder = { systemInfo -> makeSetupScript(systemInfo) },
        onLoaded = { handler.post { listener?.onLoaded() } },
        onNavigation = { uri -> listener?.onNavigation(uri) ?: true },
        onFatalError = { error -> handler.post { listener?.onWidgetError(error) } },
    )

    // JavaScript Interface

    private val javascriptInterface = object {
        @JavascriptInterface fun registerWidget(meta: String) = bridgeOnRegisterWidget(meta)
        @JavascriptInterface fun registerComponent(handle: String, meta: String) = bridgeOnRegisterComponent(handle, meta)
        @JavascriptInterface fun unregisterComponent(handle: String) = bridgeOnUnregisterComponent(handle)
        @JavascriptInterface fun widgetReady() = bridgeOnWidgetReady()
        @JavascriptInterface fun widgetError(data: String) = bridgeOnWidgetError(data)
        @JavascriptInterface fun requestLogout() = bridgeOnRequestLogout()
        @JavascriptInterface fun native(response: String?) = bridgeOnNative(response)
        @JavascriptInterface fun componentError(handle: String?, error: String) = bridgeOnComponentError(handle, error)
        @JavascriptInterface fun onLog(tag: String, message: String) = bridge.bridgeOnLog(tag, message)
    }

    private fun bridgeOnRegisterWidget(meta: String) {
        logger.info("Widget root registered", meta)
        if (!validateWcVersion(meta)) return
        handler.post {
            listener?.onRegisterWidget()
        }
    }

    private fun bridgeOnRegisterComponent(handle: String, data: String) {
        logger.info("Component registered", handle)
        try {
            val meta = JSONObject(data)
            if (!validateWcVersion(data)) return
            componentAttributes[handle] = WidgetComponentAttributes(
                refreshCookieName = meta.stringOrEmptyAsNull("refreshCookieName")
            )
            handler.post {
                listener?.onRegisterComponent(handle, meta)
            }
        } catch (e: Exception) {
            logger.error("Failed to parse register payload", e, data)
        }
    }

    private fun bridgeOnUnregisterComponent(handle: String) {
        logger.info("Component unregistered", handle)
        componentAttributes.remove(handle)
        handler.post {
            listener?.onUnregisterComponent(handle)
        }
    }

    private fun bridgeOnWidgetReady() {
        handler.post {
            listener?.onWidgetReady()
        }
    }

    private fun bridgeOnWidgetError(data: String) {
        val parsed = try {
            val json = JSONObject(data)
            val message = json.stringOrEmptyAsNull("message") ?: json.stringOrEmptyAsNull("description")
            DescopeException.widgetFailed.with(message = message ?: "Widget failed to initialize")
        } catch (_: Exception) {
            DescopeException.widgetFailed.with(message = data)
        }
        logger.error("Widget reported fatal error", parsed)
        handler.post {
            listener?.onWidgetError(parsed)
        }
    }

    private fun bridgeOnRequestLogout() {
        handler.post {
            listener?.onRequestLogout()
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
                listener?.onComponentError(null, DescopeException.widgetFailed.with(message = e.message, cause = e))
            }
        }
    }

    private fun bridgeOnComponentError(handle: String?, error: String) {
        val exception = DescopeException.widgetFailed.with(message = error)
        logger.error("Component reported error", handle, exception)
        handler.post {
            listener?.onComponentError(handle, exception)
        }
    }

    private fun validateWcVersion(metaJson: String): Boolean {
        if (wcVersionValidated) return true
        return try {
            val meta = JSONObject(metaJson)
            val wcVersion = meta.optInt("wcBridgeVersion", -1)
            if (wcVersion < REQUIRED_WC_BRIDGE_VERSION) {
                val msg = "User Profile Widget requires @descope/web-component with bridgeVersion >= " +
                    "$REQUIRED_WC_BRIDGE_VERSION (got $wcVersion). Update the widget bundle and rebuild."
                logger.error(msg)
                handler.post {
                    listener?.onWidgetError(DescopeException.widgetSetup.with(message = msg))
                }
                false
            } else {
                wcVersionValidated = true
                true
            }
        } catch (_: Exception) {
            true // missing version in payload — proceed and let later steps fail loudly
        }
    }

    // Init

    // kept here (not in WebViewBridge) so lint can see the @JavascriptInterface annotations on the typed object
    init {
        webView.addJavascriptInterface(javascriptInterface, "widget")
    }

    // Lifecycle

    fun start() {
        wcVersionValidated = false
        bridge.start()
    }

    fun reload() = bridge.reload()

    // Bridge API
    //   descopeBridge        per-component, handle-keyed (same shape as Flow)
    //   descopeWidgetBridge  widget-root lifecycle (no Flow analogue)

    fun seedAndReleaseWidget(refreshJwt: String) {
        if (refreshJwt.isNotEmpty()) {
            bridge.call("descopeWidgetBridge", "seedWidgetRootSession", refreshJwt)
        }
        bridge.call("descopeWidgetBridge", "lazyInitWidget")
    }

    fun seedWidgetRootSession(refreshJwt: String) {
        if (refreshJwt.isEmpty()) return
        bridge.call("descopeWidgetBridge", "seedWidgetRootSession", refreshJwt)
    }

    fun initializeComponent(handle: String, nativeOptions: String, refreshJwt: String, clientInputs: String) {
        bridge.call("descopeBridge", "initialize", handle, nativeOptions, refreshJwt, clientInputs)
    }

    fun updateRefreshJwt(handle: String, refreshJwt: String) {
        bridge.call("descopeBridge", "updateRefreshJwt", handle, refreshJwt)
    }

    fun postResponse(handle: String, response: BridgeResponse) {
        bridge.call("descopeBridge", "handleResponse", handle, response.typeName, response.payload)
    }

    fun dispatchLogoutComplete() {
        bridge.call("descopeWidgetBridge", "dispatchLogoutComplete")
    }

    fun dispatchLogoutFailed(error: DescopeException) {
        val payload = JSONObject().apply {
            put("code", error.code)
            put("description", error.desc)
            error.message?.let { put("message", it) }
        }
        bridge.call("descopeWidgetBridge", "dispatchLogoutFailed", payload.toString())
    }

    // Public Utilities

    fun runJavaScript(code: String) = bridge.runJavaScript(code)

    fun addStyles(css: String) = bridge.addStyles(css)
}

// Supporting Types

// per-component scope, keyed by handle — slice of register-payload fields the bridge keeps around between events
internal data class WidgetComponentAttributes(
    var refreshCookieName: String? = null,
)

// JavaScript

private fun makeSetupScript(systemInfo: SystemInfo) = """

(function() {
    // Widget-level bridge
    const widgetRoots = {};

    window.descopeWidgetBridge = {
        version: $WIDGET_BRIDGE_VERSION,

        hostInfo: ${makeHostInfoJsObject(systemInfo)},

        registerWidget(widgetElement) {
            if (!widgetElement) return
            widgetRoots.current = widgetElement
            const wcCtor = window.customElements?.get('descope-wc')
            const wcBridgeVersion = wcCtor?.bridgeVersion || 0
            widget.registerWidget(JSON.stringify({
                widgetBridgeVersion: $WIDGET_BRIDGE_VERSION,
                wcBridgeVersion: wcBridgeVersion,
            }))
            widgetElement.addEventListener('ready', () => widget.widgetReady())
            widgetElement.addEventListener('error', (event) => {
                widget.widgetError(JSON.stringify(event.detail || {}))
            })
        },

        requestLogout() { widget.requestLogout() },

        internal: {
            seedWidgetRootSession(refreshJwt) {
                if (!refreshJwt) return
                window.localStorage.setItem(${REFRESH_COOKIE_NAME.javaScriptLiteralString()}, refreshJwt)
            },
            lazyInitWidget() {
                widgetRoots.current?.lazyInit?.()
            },
            dispatchLogoutComplete() {
                const root = widgetRoots.current || document.querySelector('descope-user-profile-widget')
                root?.dispatchEvent(new CustomEvent('logout'))
            },
            dispatchLogoutFailed(payload) {
                const root = widgetRoots.current || document.querySelector('descope-user-profile-widget')
                try {
                    const parsed = JSON.parse(payload || '{}')
                    root?.dispatchEvent(new CustomEvent('logout-failed', { detail: parsed }))
                } catch (e) {
                    root?.dispatchEvent(new CustomEvent('logout-failed'))
                }
            },
        },
    }

    // Per-component bridge
    const components = {}
    let nextHandle = 0

    window.descopeBridge = {
        version: 2,

        hostInfo: window.descopeWidgetBridge.hostInfo,

        register(component) {
            if (!component) return null
            const handle = 'wc_' + (++nextHandle)
            components[handle] = component
            component.dataset.descopeWidgetHandle = handle

            const wcCtor = window.customElements?.get('descope-wc')
            const wcBridgeVersion = wcCtor?.bridgeVersion || 0

            component.addEventListener('bridge', (event) => {
                const detail = event.detail || {}
                widget.native(JSON.stringify({
                    handle: handle,
                    type: detail.type,
                    payload: detail.payload || {},
                }))
            })

            component.addEventListener('error', (event) => {
                const detail = event.detail
                const message = detail
                    ? (typeof detail === 'string' ? detail : JSON.stringify(detail))
                    : 'Unknown error'
                widget.componentError(handle, message)
            })

            widget.registerComponent(handle, JSON.stringify({
                wcBridgeVersion: wcBridgeVersion,
                refreshCookieName: component.refreshCookieName || null,
                flowId: component.getAttribute('flow-id') || null,
                projectId: component.getAttribute('project-id') || null,
            }))
            return handle
        },

        unregister(handle) {
            if (!handle) return
            const component = components[handle]
            if (component) {
                delete component.dataset.descopeWidgetHandle
            }
            delete components[handle]
            widget.unregisterComponent(handle)
        },

        internal: {
            initialize(handle, nativeOptions, refreshJwt, clientInputs) {
                const component = components[handle]
                if (!component) {
                    console.warn('initialize called for unknown handle', handle)
                    return
                }
                component.nativeOptions = JSON.parse(nativeOptions)
                this.updateRefreshJwt(handle, refreshJwt)
                this.updateClientInputs(component, clientInputs)
                component.lazyInit?.()
            },

            updateRefreshJwt(handle, refreshJwt) {
                if (!refreshJwt) return
                // Keep the widget-root's SDK alive too (it calls /me between
                // modal opens). Single key, no prefix, matches what
                // @descope/web-js-sdk reads.
                window.localStorage.setItem(${REFRESH_COOKIE_NAME.javaScriptLiteralString()}, refreshJwt)
                const component = components[handle]
                if (!component) return
                const storagePrefix = component.storagePrefix || ''
                if (storagePrefix) {
                    window.localStorage.setItem(storagePrefix + ${REFRESH_COOKIE_NAME.javaScriptLiteralString()}, refreshJwt)
                }
            },

            updateClientInputs(component, inputs) {
                let client = {}
                try {
                    client = JSON.parse(component.getAttribute('client') || '{}')
                } catch (e) {}
                client = {
                    ...client,
                    ...JSON.parse(inputs || '{}'),
                }
                component.setAttribute('client', JSON.stringify(client))
            },

            handleResponse(handle, type, payload) {
                const component = components[handle]
                if (!component) {
                    console.warn('handleResponse called for unknown handle ' + handle + ' (type=' + type + ')')
                    return
                }
                component.nativeResume(type, payload)
            },
        },
    }
})()

"""
