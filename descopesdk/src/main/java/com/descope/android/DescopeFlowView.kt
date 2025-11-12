package com.descope.android

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import com.descope.Descope
import com.descope.session.DescopeSession
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException

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
 * **IMPORTANT**: When inflating programmatically, make sure to provide an Activity context.
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

    constructor(context: Context) : super(context) {
        initFlowView()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initFlowView()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initFlowView()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initFlowView()
    }

    private fun initFlowView() {
        val webView = WebView(context)
        addView(webView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        this.flowCoordinator = DescopeFlowCoordinator(webView)
    }

    // API

    /**
     * Start a flow based on the configuration provided
     * via a [DescopeFlowView]
     *
     * @param flow The [DescopeFlow] to execute
     */
    fun startFlow(flow: DescopeFlow) {
        flowCoordinator.startFlow(flow)
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
            child.layout(0, 0, width, height)
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
    
    // Warm up
    
    companion object {
        /**
         * Prepares the Android [WebView] class for use by [DescopeFlowView] ahead of time.
         * 
         * This function is experimental. Calling it is strictly optional, but it might improve
         * the initial flow loading time on some devices, and it might help workaround some
         * internal Android bugs (e.g., https://issuetracker.google.com/issues/447973113 ).
         * 
         * You can call this function when you expect your app to be idle, for example,
         * in a home screen or splash screen [Activity]'s `onStart()` method. Alternatively,
         * if you're using Jetpack Compose you can add a method such as the one below and
         * call it somewhere in your code:
         * 
         *     @Composable
         *     fun warmupFlowView() {
         *         val context = LocalContext.current
         *         LaunchedEffect(Unit) {
         *             delay(1000)
         *             DescopeFlowView.warmup(context)
         *         }
         *     }
         * 
         * By default, calling this function schedules the actual WebView warmup to happen when
         * the main thread is idle, unless the `immediately` parameter is set to `true`. It's safe
         * to call this function multiple times as subsequent calls do nothing.
         */
        fun warmup(context: Context, immediately: Boolean = false) {
            WebViewUtils.warmup(context, immediately)
        }
    }
    
    // Deprecated

    @Deprecated("Use startFlow instead", replaceWith = ReplaceWith("startFlow(flow)"))
    fun run(flow: DescopeFlow) {
        startFlow(flow)
    }
    
}
