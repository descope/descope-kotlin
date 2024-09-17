package com.descope.android

import androidx.browser.customtabs.CustomTabsIntent

// Custom Tab

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
