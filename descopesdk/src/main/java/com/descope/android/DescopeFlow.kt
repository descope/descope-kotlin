@file:Suppress("unused")

package com.descope.android

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.descope.Descope
import com.descope.sdk.DescopeSdk
import com.descope.session.DescopeSession
import com.descope.session.DescopeSessionManager
import com.descope.types.OAuthProvider

/**
 * The configuration class required to run a Flow. Provide an instance
 * of this class when calling [DescopeFlowView.run] to run a flow
 * according to the properties provided here.
 */
class DescopeFlow {

    /** The URL where the flow is hosted. */
    var url: String

    /** Provide an instance of `DescopeSdk` if a custom instance was initialized. Leave `null` to use [Descope]*/
    var sdk: DescopeSdk? = null

    /**
     * A list of hooks that customize how the flow webpage looks or behaves.
     *
     * You can use the built-in hooks or create custom ones. See the documentation
     * for [DescopeFlowHook] for more details.
     */
    var hooks: List<DescopeFlowHook> = emptyList()

    /**
     * An optional map of client inputs that will be provided to the flow.
     * 
     * These values can be used in the flow editor to customize the flow's behavior
     * during execution. The values set on the map must be valid JSON types.
     */
    var clientInputs: Map<String, Any> = emptyMap()

    /**
     * An object that provides the [DescopeSession] value for the currently authenticated
     * user if there is one, or `null` otherwise.
     *
     * This is used when running a flow that expects the user to already be signed in.
     * For example, a flow to update a user's email or account recovery details, or that
     * does step-up authentication.
     *
     * The default behavior is to check whether the [DescopeSessionManager] is currently
     * managing a valid session, and return it if that's the case.
     *
     * - **Note**: The default behavior checks the [DescopeSessionManager] from the [Descope]
     * singleton, or the one from the flow's [sdk] property if it is set.
     *
     * If you're not using the [DescopeSessionManager] but rather managing the tokens
     * manually, and if you also need to start a flow for an authenticated user, then you
     * should set your own [sessionProvider]. For example:
     *
     *     // create a flow object with the URL where the flow is hosted
     *     val flow = DescopeFlow("https://example.com/myflow")
     *
     *     // fetch the latest session from our model layer when needed
     *     flow.sessionProvider = {
     *         return modelLayer.fetchDescopeSession()
     *     }
     *
     * - **Important**: The provider may be called multiple times to ensure that the flow uses
     * the newest tokens, even if the session is refreshed while the flow is running.
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
     * An optional deep link link URL to use when performing OAuth authentication, overriding
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
     * An optional deep link link URL to use performing SSO authentication, overriding
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
     * An optional deep link link URL to use when sending magic link emails, overriding
     * whatever is configured in the flow or project
     */
    var magicLinkRedirect: String? = null

    /**
     * Customize the [DescopeFlowView] presentation by providing a [Presentation] implementation
     */
    var presentation: Presentation? = null

    /**
     * Creates a new [DescopeFlow] object.
     */
    constructor(url: String) {
        this.url = url
    }

    /**
     * Creates a new [DescopeFlow] object from a parsed `Uri` instance.
     * */
    constructor(uri: Uri) {
        this.url = uri.toString()
    }

    /**
     * Customize the flow's presentation by implementing the [Presentation] interface.
     */
    interface Presentation {
        /**
         * Provide your own [CustomTabsIntent] that will be used when a custom tab
         * is required, e.g. when performing web-based OAuth authentication,
         * or when [DescopeFlowView.NavigationStrategy.OpenBrowser] is returned for navigation events,
         * which is also the default behavior.
         * @param context The context the [DescopeFlowView] resides inside.
         * @return A [CustomTabsIntent]. Returning `null` will use the default custom tab intent.
         */
        fun createCustomTabsIntent(context: Context): CustomTabsIntent?
    }

    /**
     * Has been renamed and replaced by [oauthNativeProvider]. Use it instead.
     */
    @Deprecated(message = "Use oauthNativeProvider instead", replaceWith = ReplaceWith("oauthNativeProvider"))
    var oauthProvider: OAuthProvider?
        get() = oauthNativeProvider
        set(value) {
            oauthNativeProvider = value
        }
}
