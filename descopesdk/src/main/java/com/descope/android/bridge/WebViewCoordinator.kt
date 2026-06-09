package com.descope.android.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.descope.android.launchCustomTab
import com.descope.android.sendViewIntent
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.internal.routes.nativeAuthorization
import com.descope.internal.routes.performAssertion
import com.descope.internal.routes.performRegister
import com.descope.sdk.DescopeLogger
import com.descope.types.DescopeException

// Coordinator

internal class WebViewCoordinator(webView: WebView) {

    var logger: DescopeLogger? = null
    var customTabsIntent: CustomTabsIntent? = null

    private val context: Context = webView.context

    // Redirect Resolution

    fun shouldUseCustomSchemeUrl(): Boolean {
        val browserIntent = Intent("android.intent.action.VIEW", "http://".toUri())
        val resolveInfo = context.packageManager.resolveActivity(browserIntent, PackageManager.MATCH_DEFAULT_ONLY)
        val label = resolveInfo?.loadLabel(context.packageManager).toString()
        return when (label.lowercase()) {
            "opera", "opera mini", "duckduckgo", "mi browser" -> true
            else -> false
        }
    }

    fun pickRedirectUrl(main: String?, fallback: String?, useFallback: Boolean): String {
        var url = main
        if (useFallback && fallback != null) url = fallback
        return url ?: ""
    }

    // Bridge Events

    fun openInBrowser(uri: Uri) {
        try {
            when (uri.scheme) {
                "mailto", "tel" -> sendViewIntent(context, uri)
                else -> launchCustomTab(context, uri, customTabsIntent)
            }
        } catch (e: DescopeException) {
            logger.error("Failed to open URL in browser", e)
        }
    }

    // Hooks

    fun executeHooks(coordinator: Coordinator, event: DescopeBridgeHook.Event, hooks: List<DescopeBridgeHook>) {
        val all = mutableListOf<DescopeBridgeHook>().apply {
            addAll(DescopeBridgeHook.defaults)
            addAll(hooks)
        }
        all.filter { it.events.contains(event) }
            .forEach { it.execute(event, coordinator) }
    }

    // Native Operations

    suspend fun handleNativeAction(request: BridgeRequest): BridgeResponse? {
        return when (request) {
            is BridgeRequest.OAuthNative -> handleOAuthNative(request)
            is BridgeRequest.WebAuth -> handleWebAuth(request)
            is BridgeRequest.WebAuthnCreate -> handleWebAuthnCreate(request)
            is BridgeRequest.WebAuthnGet -> handleWebAuthnGet(request)
        }
    }

    private suspend fun handleOAuthNative(request: BridgeRequest.OAuthNative): BridgeResponse? {
        logger.info("Launching system UI for native oauth")
        return try {
            val resp = nativeAuthorization(context, request.start)
            BridgeResponse.OAuthNative(resp.stateId, resp.identityToken)
        } catch (e: DescopeException) {
            if (e == DescopeException.oauthNativeCancelled) {
                logger.info("OAuth native canceled")
                return null
            }
            logger.error("OAuth native failed", e)
            BridgeResponse.Failure("OAuthNativeFailed")
        }
    }

    private fun handleWebAuth(request: BridgeRequest.WebAuth): BridgeResponse? {
        logger.info("Launching custom tab for ${request.variant}")
        return try {
            launchCustomTab(context, request.startUrl, customTabsIntent)
            null
        } catch (e: DescopeException) {
            logger.error("Failed to launch custom tab", e)
            BridgeResponse.Failure("CustomTabFailure")
        }
    }

    private suspend fun handleWebAuthnCreate(request: BridgeRequest.WebAuthnCreate): BridgeResponse? {
        logger.info("Attempting to create new a passkey")
        return try {
            val res = performRegister(context, request.options)
            BridgeResponse.WebAuthn(type = "webauthnCreate", transactionId = request.transactionId, response = res)
        } catch (e: DescopeException) {
            mapPasskeyException(e)
        }
    }

    private suspend fun handleWebAuthnGet(request: BridgeRequest.WebAuthnGet): BridgeResponse? {
        logger.info("Attempting to use an existing passkey")
        return try {
            val res = performAssertion(context, request.options)
            BridgeResponse.WebAuthn(type = "webauthnGet", transactionId = request.transactionId, response = res)
        } catch (e: DescopeException) {
            mapPasskeyException(e)
        }
    }

    private fun mapPasskeyException(e: DescopeException): BridgeResponse? {
        if (e == DescopeException.passkeyCancelled) {
            logger.info("Passkeys canceled")
            return null
        }
        val failure = when (e) {
            DescopeException.passkeyFailed -> {
                logger.error("Passkeys failed", e)
                "PasskeyFailed"
            }
            DescopeException.passkeyNoPasskeys -> {
                logger.error("No passkeys are available", e)
                "PasskeyNoPasskeys"
            }
            else -> {
                logger.error("Native execution failed", e)
                "NativeFailed"
            }
        }
        return BridgeResponse.Failure(failure)
    }
}
