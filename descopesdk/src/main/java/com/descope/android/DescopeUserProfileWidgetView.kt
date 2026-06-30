package com.descope.android

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import com.descope.Descope
import com.descope.internal.others.error
import com.descope.sdk.DescopeSdk
import com.descope.session.DescopeSession
import com.descope.types.DescopeException

/**
 * Display the Descope [User Profile Widget](https://app.descope.com/widgets).
 *
 * Embed this view into your UI to host the widget for an already-authenticated user.
 * The widget runs in a WebView and lets the user manage their profile through
 * sub-flow modals (add passkey, change email/phone, manage MFA, sessions, etc.).
 *
 * **Setup**
 *
 * - As a prerequisite, the user must already be signed in. Calling [startWidget]
 *   without an active session fails immediately with
 *   [DescopeException.widgetAuthenticationRequired].
 *
 * - For sub-flows that use navigation-based authentication (`Magic Link`,
 *   `OAuth (social)`, `SSO`, `External Auth`) it's required to set up app links.
 *   App Links allow the application to receive navigation to specific URLs,
 *   instead of opening the browser. Follow the [Android official documentation](https://developer.android.com/training/app-links)
 *   to set up App link in your application.
 *
 * **Usage**
 *
 * Add a [DescopeUserProfileWidgetView] to your UI via XML, compose, or code.
 * **IMPORTANT**: When inflating programmatically, make sure to provide an Activity context.
 *
 * Running the widget requires providing an instance of [DescopeUserProfileWidget]
 * to the [DescopeUserProfileWidgetView]. Read the [DescopeUserProfileWidget]
 * documentation for a detailed explanation of the available configurations.
 *
 *     descopeUserProfileWidgetView.listener = object : DescopeUserProfileWidgetView.Listener {
 *         override fun onReady() {
 *             // present the widget view via animation, or however you see fit
 *         }
 *
 *         override fun onLogout() {
 *             // the SDK has already cleared the session
 *
 *             // launch the "logged out" UI of your app
 *         }
 *
 *         override fun onError(exception: DescopeException) {
 *             // handle any errors here
 *         }
 *
 *         override fun onNavigation(uri: Uri): NavigationStrategy {
 *             // manage navigation event by deciding whether to open the URI
 *             // in a custom tab (default behavior), inline, or do nothing.
 *         }
 *     }
 *
 *     val widget = DescopeUserProfileWidget("https://example.com/widgets/user-profile")
 *     // set the oauth redirect URI to use your app's deep link
 *     widget.oauthRedirect = "my-redirect-deep-link"
 *
 *     // run the widget
 *     descopeUserProfileWidgetView.startWidget(widget)
 */
class DescopeUserProfileWidgetView : ViewGroup {
    /** The state the widget view is in. See [State] for more details. */
    val state: State
        get() = if (this::coordinator.isInitialized) coordinator.state.toWidgetState() else State.Initial

    /** The [Listener] property is called according to the widget's state. */
    var listener: Listener? = null

    private lateinit var coordinator: DescopeFlowCoordinator

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
        coordinator = DescopeFlowCoordinator(webView).apply {
            isWidgetMode = true
            listener = object : CoordinatorListener {
                override fun onReady() { listener?.onReady() }
                override fun onError(exception: DescopeException) { listener?.onError(exception) }
                override fun onLogout() { listener?.onLogout() }
                override fun onNavigation(uri: Uri): NavigationStrategy = listener?.onNavigation(uri) ?: NavigationStrategy.OpenBrowser
            }
        }
    }

    // API

    /**
     * Start the widget based on the configuration provided
     * via a [DescopeUserProfileWidgetView]
     *
     * @param widget The [DescopeUserProfileWidget] to run
     */
    fun startWidget(widget: DescopeUserProfileWidget) {
        if (currentSession(widget) == null) {
            logger(widget)?.error("Widget cannot start without an authenticated session")
            listener?.onError(DescopeException.widgetAuthenticationRequired)
            return
        }
        coordinator.startFlow(widget.toFlow())
    }

    /**
     * Resume an already running widget after a deep link
     * event. This function should be called to complete `Magic Link`,
     * web-based `OAuth`, `SSO`, and external authentication initiated by
     * a sub-flow modal inside the widget.
     *
     * @param deepLink The incoming deep link URI
     * @return `true` if the running widget consumed the URI, `false` otherwise.
     */
    fun resumeFromDeepLink(deepLink: Uri): Boolean = coordinator.resumeFromDeepLink(deepLink)

    // Internal

    private fun sdk(widget: DescopeUserProfileWidget): DescopeSdk? =
        widget.sdk ?: if (Descope.isInitialized) Descope.sdk else null

    private fun logger(widget: DescopeUserProfileWidget) = sdk(widget)?.client?.config?.logger

    private fun currentSession(widget: DescopeUserProfileWidget): DescopeSession? {
        widget.sessionProvider?.let { provider -> return provider.invoke() }
        return sdk(widget)?.sessionManager?.session?.takeIf { !it.refreshToken.isExpired }
    }

    private fun CoordinatorState.toWidgetState(): State = when (this) {
        CoordinatorState.Initial -> State.Initial
        CoordinatorState.Started -> State.Started
        CoordinatorState.Ready -> State.Ready
        CoordinatorState.Failed -> State.Failed
        // can't reach finish state in widget mode, mapped for exhaustiveness
        CoordinatorState.Finished -> State.Ready
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.layout(0, 0, width, height)
        }
    }

    // Helper Classes

    /**
     * The [Listener] interface is used to communicate widget lifecycle events back to the caller.
     */
    interface Listener {
        /**
         * Called when the widget is fully loaded and ready to be displayed
         */
        fun onReady()

        /**
         * Called after the user logs out from inside the widget. By the time this fires,
         * the SDK has already revoked the refresh token and cleared the session via
         * [DescopeSdk.sessionManager].
         */
        fun onLogout()

        /**
         * Called when the widget has encountered an error.
         *
         * Errors fired here are widget-level and terminal. Errors that happen inside a
         * sub-flow modal are non-terminal and never reach this callback — the modal
         * handles its own UI for them.
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
        /** Initial state - when the [DescopeUserProfileWidgetView] is initially created. */
        Initial,
        /** Started state - the widget has begun loading but is not yet ready to be displayed. */
        Started,
        /** Ready state - the [DescopeUserProfileWidgetView] is ready to be presented and interacted with. */
        Ready,
        /** Failed state - the widget finished unsuccessfully. This state cannot be recovered from. */
        Failed,
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
