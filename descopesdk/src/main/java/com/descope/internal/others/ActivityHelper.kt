package com.descope.internal.others

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.descope.android.CUSTOM_TAB_URL
import com.descope.android.DescopeHelperActivity
import com.descope.android.FILE_CHOOSER_INTENT
import com.descope.types.DescopeException

internal interface ActivityHelper {
    val customTabsIntent: CustomTabsIntent?
    fun openCustomTab(context: Context, customTabsIntent: CustomTabsIntent, url: Uri)
    fun closeCustomTab(context: Context)
    fun openFileChooser(context: Context, chooserIntent: Intent, callback: (FileResponse) -> Unit)
    fun onFileChosen(uris: Array<Uri>?, e: Exception?)
}

internal val activityHelper = object : ActivityHelper {
    private var fileCallback: ((FileResponse) -> Unit)? = null
    override var customTabsIntent: CustomTabsIntent? = null

    override fun openCustomTab(context: Context, customTabsIntent: CustomTabsIntent, url: Uri) {
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
    }

    override fun closeCustomTab(context: Context) {
        if (this.customTabsIntent == null) return
        this.customTabsIntent = null
        val intent = Intent(context, DescopeHelperActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            throw DescopeException.customTabFailed.with(message = "Failed to close custom tab from context", cause = e)
        }
    }

    override fun openFileChooser(context: Context, chooserIntent: Intent, callback: (FileResponse) -> Unit) {
        this.fileCallback = callback
        val intent = Intent(context, DescopeHelperActivity::class.java)
        intent.putExtra(FILE_CHOOSER_INTENT, chooserIntent)
        if (context !is android.app.Activity) {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    override fun onFileChosen(uris: Array<Uri>?, e: Exception?) {
        when {
            e != null -> fileCallback?.invoke(FileResponse.Failure(e))
            uris.isNullOrEmpty() -> fileCallback?.invoke(FileResponse.None)
            else -> fileCallback?.invoke(FileResponse.Selected(uris))
        }
    }

    private fun isBrowserSupported(context: Context, uri: Uri): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        val component = intent.resolveActivity(context.packageManager)
        return component != null
    }
}

sealed class FileResponse {
    object None : FileResponse()
    class Selected(val uris: Array<Uri>) : FileResponse()
    class Failure(val e: Exception) : FileResponse()
}
