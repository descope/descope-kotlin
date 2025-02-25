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
        (context as? Activity)?.let { activity ->
            activity.startActivity(Intent(activity, DescopeHelperActivity::class.java).apply { putExtra(CUSTOM_TAB_URL, url); flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP })
            return
        }
        throw DescopeException.customTabFailed.with(message = "Custom tabs require the given context to be an Activity")
    }

    override fun closeCustomTab(context: Context) {
        if (this.customTabsIntent == null) return
        this.customTabsIntent = null
        (context as? Activity)?.let { activity ->
            activity.startActivity(Intent(activity, DescopeHelperActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP })
            return
        }
        throw DescopeException.customTabFailed.with(message = "Custom tabs require the given context to be an Activity")
    }
}
