package com.descope.android.widget

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import com.descope.android.WebViewUtils
import com.descope.types.DescopeException

/**
 * Display Descope's User Profile Widget inside your app.
 *
 * Embed this view into your UI to be able to use widgets built in the
 * [Descope console](https://app.descope.com). The widget always runs on behalf of an
 * authenticated user.
 *
 * **Setup**
 *
 * - As a prerequisite, the widget itself must be hosted by you — Descope does not
 * currently offer a hosted version for widgets.
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
 * Add a [DescopeUserProfileWidgetView] to your UI via XML, compose, or code.
 * **IMPORTANT**: When inflating programmatically, make sure to provide an Activity context.
 *
 * Running a widget requires providing an instance of [DescopeUserProfileWidget] to the [DescopeUserProfileWidgetView].
 * The [DescopeUserProfileWidget] object defines where and how a widget is run.
 * Read the [DescopeUserProfileWidget] documentation for a detailed,
 * explanation of the available required and optional configurations.
 *
 *     widgetView.listener = object : DescopeUserProfileWidgetView.Listener {
 *         override fun onReady() {
 *             // present the widget view via animation, or however you see fit
 *         }
 *
 *         override fun onLogout() {
 *             // the user logged out from inside the widget — the session was
 *             // revoked on the server and cleared locally, so navigate to your
 *             // "logged out" UI from here
 *         }
 *
 *         override fun onError(exception: DescopeException) {
 *             // handle any errors here
 *         }
 *
 *         override fun onNavigation(uri: Uri): DescopeUserProfileWidgetView.NavigationStrategy {
 *             // manage navigation event by deciding whether to open the URI
 *             // in a custom tab (default behavior), inline, or do nothing.
 *         }
 *     }
 *
 *     val widget = DescopeUserProfileWidget("https://example.com/mywidget")
 *     // set the OAuth provider ID that is configured to "sign in with Google"
 *     widget.oauthNativeProvider = OAuthProvider.Google
 *     // set the oauth redirect URI to use your app's deep link
 *     widget.oauthRedirect = "my-redirect-deep-link"
 *     // customize the widget presentation further
 *     widget.presentation = widgetPresentation
 *
 *     // run the widget
 *     widgetView.startWidget(widget)
 */
class DescopeUserProfileWidgetView : ViewGroup {
    /** The state the widget view is in. See [State] for more details. */
    val state: State
        get() = if (this::coordinator.isInitialized) coordinator.state else State.Initial

    /** The [Listener] property is called according to the Widget's state */
    var listener: Listener?
        get() = if (this::coordinator.isInitialized) coordinator.listener else null
        set(value) {
            if (this::coordinator.isInitialized) {
                coordinator.listener = value
            }
        }

    private lateinit var coordinator: DescopeUserProfileWidgetCoordinator

    constructor(context: Context) : super(context) {
        initWidgetView()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        initWidgetView()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initWidgetView()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initWidgetView()
    }

    private fun initWidgetView() {
        val webView = WebView(context)
        addView(webView, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        this.coordinator = DescopeUserProfileWidgetCoordinator(webView)
    }

    // API

    /**
     * Start a widget based on the configuration provided
     * via a [DescopeUserProfileWidgetView]
     *
     * @param widget The [DescopeUserProfileWidget] to execute
     */
    fun startWidget(widget: DescopeUserProfileWidget) {
        coordinator.startWidget(widget)
    }

    /**
     * Resume an already running widget after a deep link
     * event. This function should be called to complete `Magic Link`,
     * web-based `OAuth`, `SSO`, and external authentication.
     *
     * @param deepLink The incoming deep link URI
     * @return `true` if the running widget consumed the URI, `false` otherwise.
     */
    fun resumeFromDeepLink(deepLink: Uri): Boolean {
        return coordinator.resumeFromDeepLink(deepLink)
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
     * The [Listener] interface is used to communicate Widget lifecycle events back to the caller.
     */
    interface Listener {
        /**
         * Called when a widget is fully loaded and ready to be displayed
         */
        fun onReady()

        /**
         * Called when the user logs out from inside the widget. By the time this
         * callback fires the host SDK has already revoked the session on the
         * server and cleared it locally — typically navigate to your
         * "logged out" UI from here.
         */
        fun onLogout()

        /**
         * Called when a widget has encountered an error.
         *
         * Typically a widget will not be restarted at this point.
         *
         * @param exception What caused the error
         */
        fun onError(exception: DescopeException)

        /**
         * Called when the widget attempts to navigate to a different URL.
         * The [DescopeUserProfileWidgetView] will act upon the response from this function.
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
     * Represents the state the widget is in.
     */
    enum class State {
        /** Initial state - when the [DescopeUserProfileWidgetView] is initially create. */
        Initial,
        /** Started state - a widget has begun but not yet ready to be displayed. */
        Started,
        /** Ready state - the [DescopeUserProfileWidgetView] is ready to be presented and interacted with. */
        Ready,
        /** Failed state - the widget finished unsuccessfully. This state cannot be recovered from. */
        Failed,
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
         * Prepares the Android [WebView] class for use by [DescopeUserProfileWidgetView] ahead of time.
         *
         * This function is experimental. Calling it is strictly optional, but it might improve
         * the initial widget loading time on some devices, and it might help workaround some
         * internal Android bugs (e.g., https://issuetracker.google.com/issues/447973113 ).
         *
         * You can call this function when you expect your app to be idle, for example,
         * in a home screen or splash screen [Activity]'s `onStart()` method. Alternatively,
         * if you're using Jetpack Compose you can add a method such as the one below and
         * call it somewhere in your code:
         *
         *     @Composable
         *     fun warmupWidgetView() {
         *         val context = LocalContext.current
         *         LaunchedEffect(Unit) {
         *             delay(1000)
         *             DescopeUserProfileWidgetView.warmup(context)
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
}
