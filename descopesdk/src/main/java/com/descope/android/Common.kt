package com.descope.android

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Returned from an `onNavigation` call on [DescopeFlowView.Listener] or
 * [DescopeUserProfileWidgetView.Listener], and determines how to handle
 * the navigation event. This is useful to override URL opening for certain
 * use-cases or provide your own implementation.
 */
enum class NavigationStrategy {
    OpenBrowser,
    Inline,
    DoNothing,
}

/**
 * Customize the presentation of a [DescopeFlowView] or [DescopeUserProfileWidgetView]
 * by providing a [Presentation] implementation.
 */
interface Presentation {
    /**
     * Provide your own [CustomTabsIntent] that will be used when a custom tab
     * is required, e.g. when performing web-based OAuth authentication,
     * or when [NavigationStrategy.OpenBrowser] is returned for navigation events,
     * which is also the default behavior.
     * @param context The context the view resides inside.
     * @return A [CustomTabsIntent]. Returning `null` will use the default custom tab intent.
     */
    fun createCustomTabsIntent(context: Context): CustomTabsIntent?
}
