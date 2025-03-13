package com.descope.android

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import androidx.browser.customtabs.CustomTabsIntent
import com.descope.Descope
import com.descope.sdk.DescopeSdk
import com.descope.session.DescopeSession
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
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

/**
 * Authenticate a user using Descope Flows.
 *
 * Embed this view into your UI to be able to run flows built with the
 * [Descope Flow builder](https://app.descope.com/flows)
 *
 * **Setup**
 *
 * - As a prerequisite, the flow itself must be defined and hosted.
 * It's possible to use Descope's auth hosting solution, or host it
 * yourself. Read more [here.](https://docs.descope.com/auth-hosting-app)
 *
 * - To use the Descope authentication methods, it is required
 * to configure the desired authentication methods in the [Descope console.](https://app.descope.com/settings/authentication)
 * Some of the default configurations might be OK to start out with,
 * but it is likely that modifications will be required before release.
 *
 *    - **IMPORTANT NOTE**: even though Application links are the recommended way to configure
 *      deep links, some browsers, such as Opera, do not honor them and open the URLs inline.
 *      It is possible to circumvent this issue by using a custom scheme, albeit less secure.
 *
 * - Beyond that, in order to use navigation / redirection based authentication,
 * namely `Magic Link`, `OAuth (social)` and SSO, it's required to set up app links.
 * App Links allow the application to receive navigation to specific URLs,
 * instead of opening the browser. Follow the [Android official documentation](https://developer.android.com/training/app-links)
 * to set up App link in your application.
 *
 * - Finally, it is possible for users to authenticate using the Google account or accounts they are logged into
 * on their Android devices. If you haven't already configured your app to support `Sign in with Google` you'll
 * probably need to set up your [Google APIs console project](https://developer.android.com/identity/sign-in/credential-manager-siwg#set-google)
 * for this. You should also configure an OAuth provider for Google in the in the [Descope console](https://app.descope.com/settings/authentication/social),
 * with its `Grant Type` set to `Implicit`. Also note that the `Client ID` and
 * `Client Secret` should be set to the values of your `Web application` OAuth client,
 * rather than those from the `Android` OAuth client.
 * For more details about configuring your app see the [Credential Manager documentation](https://developer.android.com/identity/sign-in/credential-manager).
 *
 * **Usage**
 *
 * Add a [DescopeFlowView] to your UI via XML, compose, or code.
 *
 * Running a flow requires providing an instance of [DescopeFlow] to the [DescopeFlowView].
 * The [DescopeFlow] object defines where and how a flow is run.
 * Read the [DescopeFlow] documentation for a detailed,
 * explanation of the available required and optional configurations.
 *
 *     descopeFlowView.listener = object : DescopeFlowView.Listener {
 *         override fun onReady() {
 *             // present the flow view via animation, or however you see fit
 *         }
 *
 *         override fun onSuccess(response: AuthenticationResponse) {
 *             // optionally hide the flow UI
 *
 *             // manage the incoming session
 *             Descope.sessionManager.manageSession(DescopeSession(response))
 *
 *             // launch the "logged in" UI of your app
 *         }
 *
 *         override fun onError(exception: DescopeException) {
 *             // handle any errors here
 *         }
 *
 *         override fun onNavigation(uri: Uri): DescopeFlowView.NavigationStrategy {
 *             // manage navigation event by deciding whether to open the URI
 *             // in a custom tab (default behavior), inline, or do nothing.
 *         }
 *     }
 *
 *     val descopeFlow = DescopeFlow("https://example.com")
 *     // set the OAuth provider ID that is configured to "sign in with Google"
 *     descopeFlow.oauthNativeProvider = OAuthProvider.Google
 *     // set the oauth redirect URI to use your app's deep link
 *     descopeFlow.oauthRedirect = "my-redirect-deep-link"
 *     // customize the flow presentation further
 *     descopeFlow.presentation = flowPresentation
 *
 *     // run the flow
 *     descopeFlowView.run(descopeFlow)
 */
class DescopeFlowView : ViewGroup {
    /** The state the flow view is in. See [State] for more details. */
    val state: State
        get() = if (this::flowCoordinator.isInitialized) flowCoordinator.state else State.Initial

    /** The [Listener] property is called according to the Flow's state */
    var listener: Listener?
        get() = if (this::flowCoordinator.isInitialized) flowCoordinator.listener else null
        set(value) {
            if (this::flowCoordinator.isInitialized) {
                flowCoordinator.listener = value
            }
        }

    private lateinit var flowCoordinator: DescopeFlowCoordinator

    constructor(context: Context) : super(context, null, 0) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs, 0) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initView()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initView()
    }

    private fun initView() {
        val webView = WebView(context)
        addView(webView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        this.flowCoordinator = DescopeFlowCoordinator(webView)
    }

    // API

    /**
     * Run a flow based on the configuration provided
     * via a [DescopeFlowView]
     *
     * @param flow The [DescopeFlow] to execute
     */
    fun run(flow: DescopeFlow) {
        flowCoordinator.run(flow)
    }

    /**
     * Resume an already running flow after a deep link
     * event. This function should be called to complete `Magic Link`
     * and web-based `OAuth` authentication.
     *
     * @param deepLink The incoming deep link URI
     */
    fun resumeFromDeepLink(deepLink: Uri) {
        flowCoordinator.resumeFromDeepLink(deepLink)
    }

    // Internal 

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.layout(0, 0, width, height);
        }
    }

    // Helper Classes

    /**
     * The [Listener] interface is used to communicate Flow lifecycle events back to the caller.
     */
    interface Listener {
        /**
         * Called when a flow is fully loaded and ready to be displayed
         */
        fun onReady()

        /**
         * Called when a flow has completed successfully. Typically create a [DescopeSession]
         * and manage it using [Descope.sessionManager]
         *
         * @param response The successful authentication response
         */
        fun onSuccess(response: AuthenticationResponse)

        /**
         * Called when a flow has encountered an error.
         *
         * Typically a flow will not to be restarted at this point.
         *
         * @param exception What caused the error
         */
        fun onError(exception: DescopeException)

        /**
         * Called when the flow attempts to navigate to a different URL.
         * The [DescopeFlowView] will act upon the response from this function.
         * It will either open an external custom tab, if [NavigationStrategy.OpenBrowser] is returned,
         * allow the navigation to happen [NavigationStrategy.Inline] or allow the caller to
         * override the navigation altogether if [NavigationStrategy.DoNothing] is returned.
         *
         * @param uri The URI to navigate to
         * @return The [NavigationStrategy] to act upon
         */
        fun onNavigation(uri: Uri): NavigationStrategy = NavigationStrategy.OpenBrowser
    }

    /**
     * Represents the state the flow is in.
     */
    enum class State {
        /** Initial state - when the [DescopeFlowView] is initially create. */
        Initial,
        /** Started state - a flow has begun but not yet ready to be displayed. */
        Started,
        /** Ready state - the [DescopeFlowView] is ready to be presented and interacted with. */
        Ready,
        /** Failed state - the flow finished unsuccessfully. This state cannot be recovered from. */
        Failed,
        /** Finished state - the final state when a flow has completed successfully. */
        Finished,
    }

    /**
     * Returned from a [Listener.onNavigation] call, and determines
     * the how to handle navigation event. This is useful to override
     * URL opening for certain use-cases or provide you're own implementation.
     */
    enum class NavigationStrategy {
        OpenBrowser,
        Inline,
        DoNothing,
    }
}
