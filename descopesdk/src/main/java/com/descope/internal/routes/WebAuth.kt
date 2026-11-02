package com.descope.internal.routes

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import com.descope.Descope
import com.descope.android.launchCustomTab
import com.descope.internal.others.activityHelper
import com.descope.internal.others.with
import com.descope.types.DescopeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun performWebAuthentication(context: Context, url: String, customTabsIntent: CustomTabsIntent? = null): String = withContext(Dispatchers.Main) {
    if (!Descope.isInitialized) throw DescopeException.customTabFailed.with(message = "The Descope SDK must be initialized before use")
    val contextRef = WeakReference(context)
    awaitWebAuthentication(
        register = { onRedirect -> Descope.sdk.resume = { uri -> onRedirect(uri.getQueryParameter("code")) } },
        open = { onCancel -> launchCustomTab(context, url, customTabsIntent, onCancel) },
        close = { contextRef.get()?.let { activityHelper.closeCustomTab(it) } },
    )
}

internal suspend fun awaitWebAuthentication(
    register: (onRedirect: (code: String?) -> Boolean) -> Unit,
    open: (onCancel: () -> Unit) -> Unit,
    close: () -> Unit,
): String = suspendCancellableCoroutine { continuation ->
    register { code ->
        if (!continuation.isActive) return@register false
        if (code.isNullOrEmpty()) {
            continuation.resumeWithException(DescopeException.customTabFailed.with(message = "Web authentication finished without authorization code"))
        } else {
            continuation.resume(code)
        }
        runCatching { close() }
        true
    }
    continuation.invokeOnCancellation { runCatching { close() } }
    try {
        open {
            if (continuation.isActive) continuation.resumeWithException(DescopeException.customTabCancelled)
        }
    } catch (e: DescopeException) {
        continuation.resumeWithException(e)
    }
}
