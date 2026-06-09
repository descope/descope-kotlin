package com.descope.android.bridge

import android.webkit.WebView
import com.descope.android.bridge.DescopeBridgeHook.Event

interface Coordinator {
    /** The underlying [WebView] that hosts the flow or widget. */
    val webView: WebView

    /** Runs the given JavaScript code in the page, wrapped in an IIFE. */
    fun runJavaScript(code: String)

    /** Injects a `<style>` element into the page with the given CSS. */
    fun addStyles(css: String)
}

/**
 * The [DescopeBridgeHook] class allows implementing hooks that customize how a Descope
 * bridge's webpage looks or behaves, usually by adding CSS, running JavaScript code, or
 * configuring the web view.
 *
 * The same hook type is used by both [com.descope.android.flow.DescopeFlow] and
 * [com.descope.android.widget.DescopeUserProfileWidget] — both expose a `hooks` list and
 * both pass a [Coordinator] to your hook's [execute].
 *
 * You can use hooks by setting the bridge's `hooks` array. For example, these hooks will
 * override the flow or widget to have a transparent background and set margins on the body
 * element.
 *
 *     flow.hooks = listOf(
 *         setTransparentBody(),
 *         addStyles(selector = "body", rules = listOf("margin: 16px")),
 *     )
 *
 * Alternatively, create custom hooks in a [DescopeBridgeHook] extension to have them all
 * in one place:
 *
 *     fun showFlow() {
 *         val flow = DescopeFlow("https://example.com/myflow")
 *         flow.hooks = listOf(setMaxWidth(), removeFooter(), hideScrollBar())
 *         flowView.run(flow)
 *     }
 *
 *     // elsewhere
 *
 *     val setMaxWidth = addStyles(selector = ".login-container", rules = listOf("max-width: 250px"))
 *
 *     val removeFooter = runJavaScript(DescopeBridgeHook.Event.Ready, """
 *         const footer = document.querySelector('#footer')
 *         footer?.remove()
 *     """)
 *
 *     val hideScrollBar = setUpWebView {
 *         it.isVerticalScrollBarEnabled = false
 *     }
 *
 * You can also implement your own hooks by subclassing [DescopeBridgeHook] and
 * overriding the [DescopeBridgeHook.execute] method.
 *
 * @param events A set of events for which the hook will be executed.
 */
abstract class DescopeBridgeHook(val events: Set<Event>) {

    /** The hook event determines when a hook is executed. */
    enum class Event {
        /**
         * The hook is executed when the flow or widget is started.
         *
         * - Note: The page is not loaded and the `document` element isn't available
         *     at this point, so this event is not appropriate for making changes to
         *     the page itself.
         */
        Started,

        /** The hook is executed when the `document` element is available in the page. */
        Loaded,

        /** The hook is executed when the page is fully loaded and ready to be displayed. */
        Ready,
    }

    /**
     * Override this abstract method to implement your hook.
     *
     * This method is called when one of the events in the [events] set takes place. If
     * the set has more than one member you can check the `event` parameter and take
     * different actions depending on the specific event.
     *
     * @param event The event that took place.
     * @param coordinator The [Coordinator] that's running the flow or widget.
     */
    abstract fun execute(event: Event, coordinator: Coordinator)

    companion object {
        /**
         * The list of default hooks.
         *
         * These hooks are always executed, but you can override them by adding the
         * counterpart hook to the bridge's `hooks` array.
         */
        val defaults = listOf(
            disableZoom(),
        )
    }
}

// Hooks

/**
 * Disables two finger and double tap zoom gestures.
 *
 * This hook is always run automatically when the flow or widget is loaded, so there's
 * usually no need to use it in application code.
 *
 * @return The disable zoom hook
 */
fun disableZoom(): DescopeBridgeHook {
    return SetUpWebViewHook(setOf(Event.Started)) {
        it.settings.setSupportZoom(false)
    }
}

/**
 * Use this function to change any `WebView` related settings you might want
 *
 * @param setup The setup function will be called with an instance of `WebView`
 * @return The WebView setup hook
 */
fun setUpWebView(setup: (WebView) -> Unit): DescopeBridgeHook {
    return SetUpWebViewHook(setOf(Event.Started), setup)
}

/**
 * Run some JavaScript code at after a certain event
 *
 * @param event The event at which to run (should be either Loaded or Started)
 * @param code The code to run
 * @return The run JavaScript hook
 */
fun runJavaScript(event: Event, code: String): DescopeBridgeHook {
    return RunJavaScriptHook(setOf(event), code)
}

/**
 * Add styles to the web page presented by the flow or widget
 *
 * @param event The event at which to run (Loaded by default)
 * @param selector The CSS selector
 * @param rules The CSS rules to apply
 * @return The add styles hook
 */
fun addStyles(event: Event = Event.Loaded, selector: String, rules: List<String>): DescopeBridgeHook {
    return AddStylesHook(
        setOf(event), css = """
$selector {
    ${rules.joinToString(separator = "\n") { "$it;" }}
}
"""
    )
}

/**
 * Set the `body`'s `background-color` CSS property to `transparent`
 */
fun setTransparentBody() = setBackgroundColor("body", 0x00000000)

/**
 * Set the `background-color` CSS property of a given [selector] to the given
 * Android style color long, e.g. `0xFF556677`.
 *
 * @param selector The selector to set
 * @param color The color to apply as the `background-color`
 * @return The set background color hook
 */
fun setBackgroundColor(selector: String, color: Long): DescopeBridgeHook {
    return BackgroundColorHook(selector = selector, color = color)
}

// Internal

private class SetUpWebViewHook(events: Set<Event>, private val fn: (WebView) -> Unit) : DescopeBridgeHook(events) {
    override fun execute(event: Event, coordinator: Coordinator) {
        fn(coordinator.webView)
    }
}

private class RunJavaScriptHook(events: Set<Event>, private val code: String) : DescopeBridgeHook(events) {
    override fun execute(event: Event, coordinator: Coordinator) {
        coordinator.runJavaScript(code)
    }
}

private class AddStylesHook(events: Set<Event>, private val css: String) : DescopeBridgeHook(events) {
    override fun execute(event: Event, coordinator: Coordinator) {
        coordinator.addStyles(css)
    }
}

private class BackgroundColorHook(events: Set<Event> = setOf(Event.Loaded), private val selector: String, private val color: Long) : DescopeBridgeHook(events) {
    override fun execute(event: Event, coordinator: Coordinator) {
        coordinator.addStyles("$selector { background-color: $colorStringValue; }")
    }

    private val colorStringValue: String
        get() {
            val a = (color shr 24) and 0xff
            val r = (color shr 16) and 0xff
            val g = (color shr 8) and 0xff
            val b = color and 0xff
            return if (a == 0L) "transparent"
            else {
                val alpha = a / 255.0
                "rgba($r, $g, $b, $alpha)"
            }
        }
}
