package com.descope.android

import android.net.Uri
import com.descope.Descope
import com.descope.sdk.DescopeSdk
import com.descope.session.DescopeSession
import com.descope.session.DescopeSessionManager
import com.descope.types.OAuthProvider

/**
 * The configuration class required to show a User Profile Widget. Provide an instance
 * of this class when calling [DescopeUserProfileWidgetView.startWidget] to show the
 * widget according to the properties provided here.
 *
 * The widget runs flows of its own when the user updates their details, so most of
 * the properties here have the same meaning as their [DescopeFlow] counterparts.
 */
class DescopeUserProfileWidget {

    /** The URL where the widget is hosted. */
    var url: String

    /** Provide an instance of `DescopeSdk` if a custom instance was initialized. Leave `null` to use [Descope]*/
    var sdk: DescopeSdk? = null

    /**
     * A list of hooks that customize how the widget webpage looks or behaves.
     *
     * You can use the built-in hooks or create custom ones. See the documentation
     * for [DescopeFlowHook] for more details.
     */
    var hooks: List<DescopeFlowHook> = emptyList()

    /**
     * An object that provides the [DescopeSession] value for the currently authenticated user.
     *
     * The default behavior is to check whether the [DescopeSessionManager] is currently
     * managing a valid session, and return it if that's the case. See [DescopeFlow.sessionProvider]
     * for more details.
     *
     * - **Important**: When a custom provider is set the widget does not update or clear
     * the session in the [DescopeSessionManager], as it's assumed to be managed manually.
     */
    var sessionProvider: (() -> DescopeSession?)? = null

    /** See [DescopeFlow.oauthNativeProvider]. */
    var oauthNativeProvider: OAuthProvider? = null

    /** See [DescopeFlow.oauthRedirect]. */
    var oauthRedirect: String? = null

    /** See [DescopeFlow.oauthRedirectCustomScheme]. */
    var oauthRedirectCustomScheme: String? = null

    /** See [DescopeFlow.ssoRedirect]. */
    var ssoRedirect: String? = null

    /** See [DescopeFlow.ssoRedirectCustomScheme]. */
    var ssoRedirectCustomScheme: String? = null

    /** See [DescopeFlow.externalAuthRedirect]. */
    var externalAuthRedirect: String? = null

    /** See [DescopeFlow.externalAuthRedirectCustomScheme]. */
    var externalAuthRedirectCustomScheme: String? = null

    /** See [DescopeFlow.magicLinkRedirect]. */
    var magicLinkRedirect: String? = null

    /** See [DescopeFlow.presentation]. */
    var presentation: DescopeFlow.Presentation? = null

    /**
     * Creates a new [DescopeUserProfileWidget] object.
     */
    constructor(url: String) {
        this.url = url
    }

    /**
     * Creates a new [DescopeUserProfileWidget] object from a parsed `Uri` instance.
     */
    constructor(uri: Uri) {
        this.url = uri.toString()
    }

    // Internal

    internal val flow: DescopeFlow
        get() = DescopeFlow(url).also {
            it.sdk = sdk
            it.hooks = hooks
            it.sessionProvider = sessionProvider
            it.oauthNativeProvider = oauthNativeProvider
            it.oauthRedirect = oauthRedirect
            it.oauthRedirectCustomScheme = oauthRedirectCustomScheme
            it.ssoRedirect = ssoRedirect
            it.ssoRedirectCustomScheme = ssoRedirectCustomScheme
            it.externalAuthRedirect = externalAuthRedirect
            it.externalAuthRedirectCustomScheme = externalAuthRedirectCustomScheme
            it.magicLinkRedirect = magicLinkRedirect
            it.presentation = presentation
        }
}
