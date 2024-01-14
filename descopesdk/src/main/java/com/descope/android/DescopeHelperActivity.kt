package com.descope.android

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import com.descope.internal.others.activityHelper

const val PENDING_INTENT_KEY = "pendingIntent"
const val REQUEST_CODE = 4327

class DescopeHelperActivity : Activity() {
    private var resultPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val pendingIntent: PendingIntent? = intent?.getParcelableExtra(PENDING_INTENT_KEY)
        if (pendingIntent == null) {
            finish()
            return
        }

        if (resultPending) {
            finish()
            return
        }

        resultPending = true
        startIntentSenderForResult(pendingIntent.intentSender, REQUEST_CODE, null, 0, 0, 0, null)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        resultPending = false
        activityHelper.onActivityResult(resultCode, data)
        finish()
    }
}
