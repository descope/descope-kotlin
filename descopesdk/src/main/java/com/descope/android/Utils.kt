package com.descope.android

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.descope.internal.others.activityHelper

// Custom Tab

fun launchCustomTab(context: Context, url: String, customTabsIntent: CustomTabsIntent? = null) {
    launchCustomTab(context, url.toUri(), customTabsIntent)
}

fun launchCustomTab(context: Context, uri: Uri, customTabsIntent: CustomTabsIntent? = null) {
    activityHelper.openCustomTab(context, customTabsIntent ?: defaultCustomTabIntent(), uri)
}

internal fun defaultCustomTabIntent(): CustomTabsIntent {
    return CustomTabsIntent.Builder()
        .setUrlBarHidingEnabled(true)
        .setShowTitle(true)
        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        .setBookmarksButtonEnabled(false)
        .setDownloadButtonEnabled(false)
        .setInstantAppsEnabled(false)
        .build()
}
