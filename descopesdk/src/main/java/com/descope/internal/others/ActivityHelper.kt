package com.descope.internal.others

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.descope.android.CUSTOM_TAB_URL
import com.descope.android.DescopeHelperActivity
import com.descope.types.DescopeException

internal interface ActivityHelper {
    val customTabsIntent: CustomTabsIntent?
    // onCancel is registered JIT (lifecycle-bound to this custom-tab launch) so
    // it doesn't outlive the tab and can't be hijacked by a different caller.
    fun openCustomTab(context: Context, customTabsIntent: CustomTabsIntent, url: Uri, onCancel: (() -> Unit)? = null)
    fun closeCustomTab(context: Context)
    fun onCustomTabCanceled()
}

internal val activityHelper = object : ActivityHelper {
    private var cancelCallback: (() -> Unit)? = null
    override var customTabsIntent: CustomTabsIntent? = null

    override fun openCustomTab(context: Context, customTabsIntent: CustomTabsIntent, url: Uri, onCancel: (() -> Unit)?) {
        this.customTabsIntent = customTabsIntent
        if (!isBrowserSupported(context, url)) {
            throw DescopeException.customTabFailed.with(message = "No browser application was found")
        }
        val intent = Intent(context, DescopeHelperActivity::class.java)
        intent.putExtra(CUSTOM_TAB_URL, url)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            throw DescopeException.customTabFailed.with(message = "Failed to open custom tab from context", cause = e)
        }
        this.cancelCallback = onCancel
    }

    override fun closeCustomTab(context: Context) {
        if (this.customTabsIntent == null) return
        this.customTabsIntent = null
        this.cancelCallback = null
        val intent = Intent(context, DescopeHelperActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            throw DescopeException.customTabFailed.with(message = "Failed to close custom tab from context", cause = e)
        }
    }

    override fun onCustomTabCanceled() {
        cancelCallback?.invoke()
        cancelCallback = null
    }

    private fun isBrowserSupported(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val component = intent.resolveActivity(context.packageManager)
        return component != null
    }
}
