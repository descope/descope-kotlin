package com.descope.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient.FileChooserParams
import androidx.core.content.IntentCompat
import com.descope.internal.others.activityHelper

const val CUSTOM_TAB_URL = "customTabUrl"
const val FILE_CHOOSER_INTENT = "fileChooserIntent"

private const val fileChooserRequestCode = 1

class DescopeHelperActivity : Activity() {
    private var listenForClose = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val chooserIntent: Intent? = intent?.let { IntentCompat.getParcelableExtra(it, FILE_CHOOSER_INTENT, Intent::class.java) }
        if (chooserIntent != null) {
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(chooserIntent, fileChooserRequestCode)
            } catch (_: Exception) {
                activityHelper.onFileChooserResult(null)
                finish()
            }
            return
        }

        val url: Uri? = intent?.let { IntentCompat.getParcelableExtra(it, CUSTOM_TAB_URL, Uri::class.java) }
        listenForClose = false
        if (url == null) {
            finish()
            return
        }

        activityHelper.customTabsIntent?.launchUrl(this, url)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != fileChooserRequestCode) return
        activityHelper.onFileChooserResult(FileChooserParams.parseResult(resultCode, data))
        finish()
    }

    override fun onResume() {
        super.onResume()
        // the file chooser finishes this activity when its result is received
        if (intent?.hasExtra(FILE_CHOOSER_INTENT) == true) return
        // this activity will resume again if the user cancels the operation
        // in that case we want to close the activity, otherwise it will
        // interfere with user input, etc.
        if (listenForClose) {
            listenForClose = false
            activityHelper.onCustomTabCanceled()
            finish()
        } else {
            listenForClose = true
        }
    }
}
