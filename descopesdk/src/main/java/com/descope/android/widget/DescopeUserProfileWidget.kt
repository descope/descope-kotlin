package com.descope.android.widget

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.descope.Descope
import com.descope.android.bridge.DescopeBridgeHook
import com.descope.sdk.DescopeSdk
import com.descope.session.DescopeSession
import com.descope.session.DescopeSessionManager
import com.descope.types.OAuthProvider

/**
 * The configuration class required to run a User Profile Widget. Provide an instance
 * of this class when calling [DescopeUserProfileWidgetView.startWidget] to run a widget
 * according to the properties provided here.
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
     * for [DescopeBridgeHook] for more details.
     */
    var hooks: List<DescopeBridgeHook> = emptyList()

    /**
     * An optional map of client inputs that will be provided to the widget.
     *
     * These values can be used to customize the widget's behavior during execution.
     * The values set on the map must be valid JSON types.
     */
    var clientInputs: Map<String, Any> = emptyMap()

    /**
     * An object that provides the [DescopeSession] value for the currently authenticated
     * user if there is one, or `null` otherwise.
     *
     * This is required because a widget always runs on behalf of an authenticated user.
     * The default behavior is to check whether the [DescopeSessionManager] is currently
     * managing a valid session, and return it if that's the case.
     *
     * - **Note**: The default behavior checks the [DescopeSessionManager] from the [Descope]
     * singleton, or the one from the widget's [sdk] property if it is set.
     *
     * If you're not using the [DescopeSessionManager] but rather managing the tokens
     * manually, then you should set your own [sessionProvider]. For example:
     *
     *     // create a widget object with the URL where the widget is hosted
     *     val widget = DescopeUserProfileWidget("https://example.com/mywidget")
     *
     *     // fetch the latest session from our model layer when needed
     *     widget.sessionProvider = {
     *         return modelLayer.fetchDescopeSession()
     *     }
     *
     * - **Important**: The provider may be called multiple times to ensure that the widget uses
     * the newest tokens, even if the session is refreshed while the widget is running.
     * This is especially important for projects that use refresh token rotation.
     */
    var sessionProvider: (() -> DescopeSession?)? = null

    /**
     * The ID of the oauth provider that is configured to natively "Sign In with Google".
     * Will likely be "google" if the Descope "Google" provider was customized,
     * or alternatively a custom provider ID.
     */
    var oauthNativeProvider: OAuthProvider? = null

    /**
     * An optional deep link URL to use when performing OAuth authentication, overriding
     * whatever is configured in the flow or project.
     * - **IMPORTANT NOTE**: even though App Links are the recommended way to configure
     * deep links, some browsers, such as Opera, do not respect them and open the URLs inline.
     * It is possible to circumvent this issue by providing a custom scheme based URL via [oauthRedirectCustomScheme].
     */
    var oauthRedirect: String? = null

    /**
     * An optional custom scheme based URL, e.g. `mycustomscheme://myhost`,
     * to use when performing OAuth authentication overriding whatever is configured in the flow or project.
     * Functionally, this URL is exactly the same as [oauthRedirect], and will be used in its stead, only
     * when the user has a default browser that does not honor App Links by default.
     * That means the `https` based App Links are opened inline in the browser, instead
     * of being handled by the application.
     */
    var oauthRedirectCustomScheme: String? = null

    /**
     * An optional deep link URL to use when performing SSO authentication, overriding
     * whatever is configured in the flow or project
     * - **IMPORTANT NOTE**: even though App Links are the recommended way to configure
     * deep links, some browsers, such as Opera, do not respect them and open the URLs inline.
     * It is possible to circumvent this issue by providing a custom scheme via [ssoRedirectCustomScheme]
     */
    var ssoRedirect: String? = null

    /**
     * An optional custom scheme based URL, e.g. `mycustomscheme://myhost`,
     * to use when performing SSO authentication overriding whatever is configured in the flow or project.
     * Functionally, this URL is exactly the same as [ssoRedirect], and will be used in its stead, only
     * when the user has a default browser that does not honor App Links by default.
     * That means the `https` based App Links are opened inline in the browser, instead
     * of being handled by the application.
     */
    var ssoRedirectCustomScheme: String? = null

    /**
     * An optional deep link URL to use when performing external authentication, overriding
     * whatever is configured in the flow or project.
     * - **IMPORTANT NOTE**: even though App Links are the recommended way to configure
     * deep links, some browsers, such as Opera, do not respect them and open the URLs inline.
     * It is possible to circumvent this issue by providing a custom scheme via [externalAuthRedirectCustomScheme]
     */
    var externalAuthRedirect: String? = null

    /**
     * An optional custom scheme based URL, e.g. `mycustomscheme://myhost`,
     * to use when performing external authentication overriding whatever is configured in the flow or project.
     * Functionally, this URL is exactly the same as [externalAuthRedirect], and will be used in its stead, only
     * when the user has a default browser that does not honor App Links by default.
     * That means the `https` based App Links are opened inline in the browser, instead
     * of being handled by the application.
     */
    var externalAuthRedirectCustomScheme: String? = null

    /**
     * An optional deep link URL to use when sending magic link emails, overriding
     * whatever is configured in the flow or project
     */
    var magicLinkRedirect: String? = null

    /**
     * Customize the [DescopeUserProfileWidgetView] presentation by providing a [Presentation] implementation
     */
    var presentation: Presentation? = null

    /**
     * Creates a new [DescopeUserProfileWidget] object.
     */
    constructor(url: String) {
        this.url = url
    }

    /**
     * Creates a new [DescopeUserProfileWidget] object from a parsed `Uri` instance.
     * */
    constructor(uri: Uri) {
        this.url = uri.toString()
    }

    /**
     * Customize the widget's presentation by implementing the [Presentation] interface.
     */
    interface Presentation {
        /**
         * Provide your own [CustomTabsIntent] that will be used when a custom tab
         * is required, e.g. when performing web-based OAuth authentication.
         * @param context The context the [DescopeUserProfileWidgetView] resides inside.
         * @return A [CustomTabsIntent]. Returning `null` will use the default custom tab intent.
         */
        fun createCustomTabsIntent(context: Context): CustomTabsIntent?
    }
}
