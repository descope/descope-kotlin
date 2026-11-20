package com.descope.android

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import com.descope.Descope
import com.descope.android.DescopeFlowView.NavigationStrategy
import com.descope.session.DescopeSessionManager
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
import com.descope.types.DescopeUser

/**
 * Show the Descope User Profile Widget to an authenticated user.
 *
 * Embed this view into your UI to let users view and update their own details,
 * using a widget built with the [Descope console](https://app.descope.com/widgets).
 *
 * **Setup**
 *
 * - As a prerequisite, the widget must be hosted on a webpage of your own, as there's
 * no hosted version of it offered by Descope.
 *
 * - The widget runs flows when the user updates their details, so everything in the
 * setup section of [DescopeFlowView] applies here as well.
 *
 * **Usage**
 *
 * Add a [DescopeUserProfileWidgetView] to your UI via XML, compose, or code.
 * **IMPORTANT**: When inflating programmatically, make sure to provide an Activity context.
 *
 * The widget requires an authenticated user, which by default is taken from the
 * session managed by the [DescopeSessionManager].
 *
 *     widgetView.listener = object : DescopeUserProfileWidgetView.Listener {
 *         override fun onReady() {
 *             // present the widget view via animation, or however you see fit
 *         }
 *
 *         override fun onError(exception: DescopeException) {
 *             // handle any errors here
 *         }
 *
 *         override fun onLogout() {
 *             // the session was already cleared so launch
 *             // the "logged out" UI of your app
 *         }
 *     }
 *
 *     val widget = DescopeUserProfileWidget("https://example.com/profile")
 *     widgetView.startWidget(widget)
 */
class DescopeUserProfileWidgetView : ViewGroup {
    /** The state the widget view is in. See [State] for more details. */
    val state: State
        get() = if (this::flowCoordinator.isInitialized) State.valueOf(flowCoordinator.state.name) else State.Initial

    /** The [Listener] property is called according to the widget's state */
    var listener: Listener? = null

    private lateinit var flowCoordinator: DescopeFlowCoordinator

    private val flowListener = object : DescopeFlowView.Listener {
        override fun onReady() = listener?.onReady() ?: Unit
        override fun onSuccess(response: AuthenticationResponse) = Unit
        override fun onError(exception: DescopeException) = listener?.onError(exception) ?: Unit
        override fun onNavigation(uri: Uri) = listener?.onNavigation(uri) ?: NavigationStrategy.OpenBrowser
    }

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
        this.flowCoordinator = DescopeFlowCoordinator(webView)
        flowCoordinator.listener = flowListener
        flowCoordinator.onLogout = { listener?.onLogout() }
        flowCoordinator.onUserUpdated = { listener?.onUserUpdated(it) }
    }

    // API

    /**
     * Show a widget based on the configuration provided
     * via a [DescopeUserProfileWidget]
     *
     * @param widget The [DescopeUserProfileWidget] to show
     */
    fun startWidget(widget: DescopeUserProfileWidget) {
        flowCoordinator.startWidget(widget)
    }

    /**
     * Resume a flow running inside the widget after a deep link
     * event. This function should be called to complete `Magic Link`,
     * web-based `OAuth`, `SSO`, and external authentication.
     *
     * @param deepLink The incoming deep link URI
     * @return `true` if the widget consumed the URI, `false` otherwise.
     */
    fun resumeFromDeepLink(deepLink: Uri): Boolean {
        return flowCoordinator.resumeFromDeepLink(deepLink)
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
     * The [Listener] interface is used to communicate widget lifecycle events back to the caller.
     */
    interface Listener {
        /**
         * Called when the widget is fully loaded and ready to be displayed
         */
        fun onReady()

        /**
         * Called when the widget has encountered an error.
         *
         * Typically the widget will need to be restarted at this point.
         *
         * @param exception What caused the error
         */
        fun onError(exception: DescopeException)

        /**
         * Called with the latest user details after the user makes changes in the widget.
         *
         * The session in the [DescopeSessionManager] is updated before this function
         * is called, unless a custom [DescopeUserProfileWidget.sessionProvider] is set.
         * This function may be called multiple times, and being called doesn't
         * guarantee that the user details have actually changed.
         *
         * @param user The latest user details
         */
        fun onUserUpdated(user: DescopeUser) {}

        /**
         * Called when the user logs out using the widget's logout button.
         *
         * The session is removed from the [DescopeSessionManager] before this function
         * is called, unless a custom [DescopeUserProfileWidget.sessionProvider] is set.
         */
        fun onLogout()

        /**
         * Called when the widget attempts to navigate to a different URL.
         * See [DescopeFlowView.Listener.onNavigation] for more details.
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
        /** Started state - the widget has begun loading but is not yet ready to be displayed. */
        Started,
        /** Ready state - the [DescopeUserProfileWidgetView] is ready to be presented and interacted with. */
        Ready,
        /** Failed state - the widget failed to load. This state cannot be recovered from. */
        Failed,
        /** Finished state - the final state after the user has logged out. */
        Finished,
    }
}
