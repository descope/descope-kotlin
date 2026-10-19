package com.descope.android

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import androidx.core.content.IntentCompat
import com.descope.internal.others.activityHelper

const val CUSTOM_TAB_URL = "customTabUrl"

class DescopeHelperActivity : Activity() {
    private var listenForClose = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url: Uri? = intent?.let { IntentCompat.getParcelableExtra(it, CUSTOM_TAB_URL, Uri::class.java) }
        listenForClose = false
        if (url == null) {
            finish()
            return
        }

        activityHelper.customTabsIntent?.launchUrl(this, url)
    }

    override fun onResume() {
        super.onResume()
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
