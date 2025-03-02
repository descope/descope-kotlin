package com.descope.internal.others

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.descope.android.CUSTOM_TAB_URL
import com.descope.android.DescopeHelperActivity
import com.descope.types.DescopeException

internal interface ActivityHelper {
    val customTabsIntent: CustomTabsIntent?
    fun openCustomTab(context: Context, customTabsIntent: CustomTabsIntent, url: Uri)
    fun closeCustomTab(context: Context)
}

internal val activityHelper = object : ActivityHelper {
    override var customTabsIntent: CustomTabsIntent? = null

    override fun openCustomTab(context: Context, customTabsIntent: CustomTabsIntent, url: Uri) {
        this.customTabsIntent = customTabsIntent
        if (!isBrowserSupported(context, url)) {
            throw DescopeException.customTabFailed.with(message = "No browser application was found")
        }
        if (context !is Activity) {
            throw DescopeException.customTabFailed.with(message = "Custom tabs require the given context to be an Activity")
        }
        val intent = Intent(context, DescopeHelperActivity::class.java)
        intent.putExtra(CUSTOM_TAB_URL, url)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        context.startActivity(intent)
    }

    override fun closeCustomTab(context: Context) {
        if (this.customTabsIntent == null) return
        this.customTabsIntent = null
        if (context !is Activity) {
            throw DescopeException.customTabFailed.with(message = "Custom tabs require the given context to be an Activity")
        }
        val intent = Intent(context, DescopeHelperActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        context.startActivity(intent)
    }

    private fun isBrowserSupported(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val component = intent.resolveActivity(context.packageManager)
        return component != null
    }
}
