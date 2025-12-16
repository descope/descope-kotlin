package com.descope.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.descope.internal.others.activityHelper
import com.descope.internal.others.with
import com.descope.types.DescopeException
import java.util.concurrent.atomic.AtomicBoolean


// Emails

fun sendViewIntent(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply { data = uri }
        context.startActivity(intent)
    } catch (e: Exception) {
        throw DescopeException.browserError.with(cause = e, message = "Failed to launch view intent")
    }
}

// Custom Tab

fun launchCustomTab(context: Context, url: String, customTabsIntent: CustomTabsIntent? = null) {
    launchCustomTab(context, url.toUri(), customTabsIntent)
}

fun launchCustomTab(context: Context, uri: Uri, customTabsIntent: CustomTabsIntent? = null) {
    activityHelper.openCustomTab(context, customTabsIntent ?: defaultCustomTabIntent(), uri)
}

internal fun defaultCustomTabIntent(): CustomTabsIntent {
    return CustomTabsIntent.Builder()
        .setUrlBarHidingEnabled(true)
        .setShowTitle(true)
        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        .setBookmarksButtonEnabled(false)
        .setDownloadButtonEnabled(false)
        .setInstantAppsEnabled(false)
        .build()
}

internal object WebViewUtils {
    private val done = AtomicBoolean()
    
    fun warmup(context: Context, immediately: Boolean) {
        if (done.get()) return
        
        if (immediately) {
            warmup(context)
            return
        }
        
        Looper.getMainLooper().queue.addIdleHandler {
            warmup(context)
            false
        }
    }
    
    private fun warmup(context: Context) {
        val webView = try {
            WebView(context.applicationContext)
        } catch (_: Throwable) {
            return
        }

        Handler(Looper.getMainLooper()).post {
            try {
                webView.destroy()
            } catch (_: Throwable) {
                // ignore
            }
        }

        done.set(true)
        return
    }
}
