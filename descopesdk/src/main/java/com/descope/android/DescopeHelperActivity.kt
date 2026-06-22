package com.descope.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import com.descope.internal.others.activityHelper

const val CUSTOM_TAB_URL = "customTabUrl"
const val FILE_CHOOSER_INTENT = "fileChooserIntent"

private const val REQUEST_CODE_FILE_CHOOSER = 9173

class DescopeHelperActivity : Activity() {
    private var listenForClose = false
    private var mode: Mode = Mode.CustomTab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // file chooser
        @Suppress("DEPRECATION")
        val chooserIntent: Intent? = intent?.getParcelableExtra(FILE_CHOOSER_INTENT)
        if (chooserIntent != null) {
            mode = Mode.FileChooser
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(chooserIntent, REQUEST_CODE_FILE_CHOOSER)
            } catch (e: Exception) {
                activityHelper.onFileChosen(null, e)
                finish()
            }
            return
        }

        // custom tab launcher
        @Suppress("DEPRECATION")
        val url: Uri? = intent?.getParcelableExtra(CUSTOM_TAB_URL)
        listenForClose = false
        if (url == null) {
            finish()
            return
        }

        activityHelper.customTabsIntent?.launchUrl(this, url)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_FILE_CHOOSER) {
            val uris = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            activityHelper.onFileChosen(uris, null)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // file choosing finishes in `onActivityResult`.
        if (mode == Mode.FileChooser) return
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

private enum class Mode {
    CustomTab, FileChooser
}