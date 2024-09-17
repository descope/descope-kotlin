package com.descope.android

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import com.descope.internal.others.activityHelper

const val CUSTOM_TAB_URL = "customTabUrl"

class DescopeHelperActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val url: Uri? = intent?.getParcelableExtra(CUSTOM_TAB_URL)
        if (url == null) {
            finish()
            return
        }

        activityHelper.customTabsIntent?.launchUrl(this, url)
    }
}
