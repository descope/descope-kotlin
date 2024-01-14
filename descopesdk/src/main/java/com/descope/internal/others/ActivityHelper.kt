package com.descope.internal.others

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.descope.android.DescopeHelperActivity
import com.descope.android.PENDING_INTENT_KEY
import com.descope.types.DescopeException

internal interface ActivityHelper {
    fun startHelperActivity(context: Context, pendingIntent: PendingIntent, callback: (Int, Intent?) -> Unit)
    fun onActivityResult(resultCode: Int, intent: Intent?)
}

internal val activityHelper = object : ActivityHelper {
    private var callback: (Int, Intent?) -> Unit = { _, _ -> }

    override fun startHelperActivity(context: Context, pendingIntent: PendingIntent, callback: (Int, Intent?) -> Unit) {
        this.callback = callback
        (context as? Activity)?.let { activity ->
            activity.startActivity(Intent(activity, DescopeHelperActivity::class.java).apply { putExtra(PENDING_INTENT_KEY, pendingIntent) })
            return
        }
        throw DescopeException.passkeyFailed.with(message = "Passkeys require the given context to be an Activity")
    }

    override fun onActivityResult(resultCode: Int, intent: Intent?) {
        callback(resultCode, intent)
    }
}
