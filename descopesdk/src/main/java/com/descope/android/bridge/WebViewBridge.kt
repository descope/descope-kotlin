package com.descope.android.bridge

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.descope.android.DescopeSystemInfo
import com.descope.android.SystemInfo
import com.descope.internal.http.failureFromResponseCode
import com.descope.internal.others.FileResponse
import com.descope.internal.others.activityHelper
import com.descope.internal.others.debug
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.internal.others.isUnsafeEnabled
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
internal class WebViewBridge(
    val webView: WebView,
    private val tag: String,
    private val urlProvider: () -> String?,
    private val setupScriptBuilder: (SystemInfo) -> String,
    private val onLoaded: () -> Unit,
    private val onNavigation: (Uri) -> Boolean,
    private val onFatalError: (DescopeException) -> Unit,
) {
    var logger: DescopeLogger? = null

    private val handler = Handler(Looper.getMainLooper())
    private var alreadySetUp = false
    private var startedAt = 0L
    private var attempts = 0

    // Chrome Client

    private val chromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            val message = consoleMessage?.message() ?: return false
            return handleConsoleMessage(message, consoleMessage.messageLevel())
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            return try {
                activityHelper.openFileChooser(webView.context, fileChooserParams.createIntent()) { fileResponse ->
                    when (fileResponse) {
                        is FileResponse.Failure -> {
                            logger.error("File chooser resulted in a failure", fileResponse.e)
                            filePathCallback.onReceiveValue(emptyArray())
                        }
                        is FileResponse.None -> filePathCallback.onReceiveValue(emptyArray())
                        is FileResponse.Selected -> filePathCallback.onReceiveValue(fileResponse.uris)
                    }
                }
                true
            } catch (e: Exception) {
                logger.error("Failed to launch file chooser", e)
                filePathCallback.onReceiveValue(emptyArray())
                false
            }
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
        logger.info("$tag attempting to navigate to a URL", uri)
        return onNavigation(uri)
    }

    private fun handlePageStarted(url: String?) {
        logger.info("On page started", url)
        if (alreadySetUp) {
            logger.error("Bridge is already set up", url, webView.progress)
            return
        }
        alreadySetUp = true

        // tag doubles as the JS-side @JavascriptInterface name (e.g. "flow", "widget")
        webView.evaluateJavascript(makeLoggingScript(tag), {})

        val setupScript = setupScriptBuilder(DescopeSystemInfo.getInstance(webView.context))
        webView.evaluateJavascript(setupScript, {})

        onLoaded()
    }

    private fun handlePageFinished(url: String?) {
        logger.info("On page finished", url, webView.progress)
    }

    private fun handleReceivedError(errorCode: Int, description: String?) {
        logger.error("Error loading $tag page", errorCode, description)
        if (scheduleRetryAfterError()) return
        val failure = description ?: ""
        val message = when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP -> if ("INTERNET_DISCONNECTED" in failure) "The Internet connection appears to be offline" else "The server could not be found"
            WebViewClient.ERROR_CONNECT -> "Failed to connect to the server"
            WebViewClient.ERROR_TIMEOUT -> "The connection timed out"
            else -> "The URL failed to load${if (failure.isBlank()) "" else " ($failure)"}"
        }
        onFatalError(DescopeException.networkError.with(message = message))
    }

    private fun handleReceivedHttpError(statusCode: Int) {
        logger.error("$tag page failed to load", statusCode)
        if (statusCode >= 500 && scheduleRetryAfterError()) return
        val message = failureFromResponseCode(statusCode)
        onFatalError(DescopeException.networkError.with(message = message))
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
        webView.webChromeClient = chromeClient
        webView.webViewClient = viewClient
    }

    // Lifecycle

    fun start() {
        val url = urlProvider() ?: return
        alreadySetUp = false
        startedAt = System.currentTimeMillis()
        attempts = 1
        webView.loadUrl(url)
    }

    fun reload() {
        val url = urlProvider() ?: return
        attempts++
        logger.info("Retrying to load $tag (attempt $attempts)")
        webView.loadUrl(url)
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

    // positional vararg — callers go through typed wrappers in the stack bridges
    fun call(target: String, function: String, vararg params: String) {
        val escaped = params.joinToString(", ") { it.javaScriptLiteralString() }
        val javascript = "window.$target.internal.$function($escaped)"
        webView.evaluateJavascript(javascript, {})
    }

    fun bridgeOnLog(tag: String, message: String) {
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
}

// Retry

private fun createRetryRunnable(ref: WeakReference<WebViewBridge>) = Runnable {
    val bridge = ref.get() ?: return@Runnable
    bridge.reload()
}

// JavaScript

internal fun String?.javaScriptLiteralString() = if (this == null) "''"
    else "`" + replace("\\", "\\\\")
        .replace("$", "\\$")
        .replace("`", "\\`") + "`"

internal fun String.javaScriptAnonymousFunction() = """
    (function() {
        $this
    })()
"""

internal fun makeLoggingScript(interfaceName: String) = """
(function() {
    function stringify(args) {
        return Array.from(args).map(arg => {
            if (!arg) return ""
            if (typeof arg === 'string') return arg
            return JSON.stringify(arg)
        }).join(' ')
    }
    window.onerror = function() { $interfaceName.onLog('fail', stringify(arguments)); return true; };
    window.console.error = function() { $interfaceName.onLog('error', stringify(arguments)); };
    window.console.warn = function() { $interfaceName.onLog('warn', stringify(arguments)); };
    window.console.info = function() { $interfaceName.onLog('info', stringify(arguments)); };
    window.console.debug = function() { $interfaceName.onLog('debug', stringify(arguments)); };
    window.console.log = function() { $interfaceName.onLog('log', stringify(arguments)); };
})();
"""

internal fun makeHostInfoJsObject(systemInfo: SystemInfo) = """
{
    sdkName: 'android',
    sdkVersion: ${DescopeSdk.VERSION.javaScriptLiteralString()},
    platformName: 'android',
    platformVersion: ${systemInfo.platformVersion.javaScriptLiteralString()},
    appName: ${systemInfo.appName.javaScriptLiteralString()},
    appVersion: ${systemInfo.appVersion.javaScriptLiteralString()},
    device: ${systemInfo.device.javaScriptLiteralString()},
    webauthn: $isWebAuthnSupported,
}
"""

// Bridge Messages

// handle is null for Flow (single component) and non-null for the Widget (one entry per <descope-wc>)
internal sealed class BridgeRequest {
    abstract val handle: String?

    class OAuthNative(override val handle: String?, val start: JSONObject) : BridgeRequest()
    class WebAuth(override val handle: String?, val variant: String, val startUrl: String) : BridgeRequest()
    class WebAuthnCreate(override val handle: String?, val transactionId: String, val options: String) : BridgeRequest()
    class WebAuthnGet(override val handle: String?, val transactionId: String, val options: String) : BridgeRequest()

    val type
        get() = when (this) {
            is OAuthNative -> "oauthNative"
            is WebAuth -> variant
            is WebAuthnCreate -> "webauthnCreate"
            is WebAuthnGet -> "webauthnGet"
        }

    companion object {
        fun fromJson(jsonString: String): BridgeRequest {
            val json = JSONObject(jsonString)
            val handle = if (json.has("handle")) json.getString("handle") else null
            val type = json.getString("type")
            return json.getJSONObject("payload").run {
                when (type) {
                    "oauthNative" -> OAuthNative(handle = handle, start = getJSONObject("start"))
                    "oauthWeb", "sso", "externalAuth" -> WebAuth(handle = handle, variant = type, startUrl = getString("startUrl"))
                    "webauthnCreate" -> WebAuthnCreate(handle = handle, transactionId = getString("transactionId"), options = getString("options"))
                    "webauthnGet" -> WebAuthnGet(handle = handle, transactionId = getString("transactionId"), options = getString("options"))
                    else -> throw DescopeException.flowFailed.with(message = "Unexpected bridge request type: $type")
                }
            }
        }
    }
}

internal sealed class BridgeResponse {
    class OAuthNative(val stateId: String, val identityToken: String) : BridgeResponse()
    class WebAuthn(val type: String, val transactionId: String, val response: String) : BridgeResponse()
    class DeepLink(val type: String, val url: String) : BridgeResponse()
    class Failure(val failure: String) : BridgeResponse()

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
