package com.descope.sdk

import com.descope.internal.others.ConsoleLogger
import java.net.URL

/**
 * The configuration of the Descope SDK.
 *
 * @property projectId The ID of the Descope project.
 */
class DescopeConfig(val projectId: String) {
    /** An optional override for the base URL of the Descope server. */
    var baseUrl: String? = null

    /**
     * The name of the session cookie used for authentication.
     * Defaults to "DS" if not specified. Override this if your cookies are named differently.
     */
    var sessionCookieName: String = "DS"

    /**
     * The name of the refresh cookie used for authentication.
     * Defaults to "DSR" if not specified. Override this if your cookies are named differently.
     */
    var refreshCookieName: String = "DSR"
    
    /**
     * An optional object to handle logging in the Descope SDK.
     *
     * The default value of this property is `null` and thus logging will be completely
     * disabled. You can set this to [DescopeLogger.basicLogger] to print error and info
     * log messages to the console.
     *
     * If you encounter any issues you can also use [DescopeLogger.debugLogger] to enable
     * more verbose logging. This will configure a simple logger that prints all logs to the
     * console. If the logger detects that the app is was built for debugging (i.e., the build
     * config has the `debuggable` flag) it will also output potentially sensitive runtime values,
     * such as full network request and response payloads, secrets and tokens in cleartext, etc.
     *
     *     Descope.setup(this, projectId = "...") {
     *         logger = DescopeLogger.debugLogger
     *     }
     *
     * In rare cases you might need to use [DescopeLogger.unsafeLogger] which skips the
     * debuggable flag check and always prints all log data including all sensitive runtime
     * values. Make sure you don't use [DescopeLogger.unsafeLogger] in release builds
     * intended for production.
     *
     * If your application uses some logging framework or third party service you can forward
     * the Descope SDK log messages to it by subclassing [DescopeLogger] and overriding
     * the `output` method. See the documentation for [DescopeLogger] for more details.
     */
    var logger: DescopeLogger? = null
    
    /**
     * An optional object to override how HTTP requests are performed.
     *  - The default value of this property is always `null`, and the SDK uses its own
     *  internal network client to perform HTTP requests.
     *  - This property can be useful to test code that uses the Descope SDK without any
     *  network requests actually taking place. In most other cases there shouldn't be
     *  any need to use it.
     */
    var networkClient: DescopeNetworkClient? = null
}

/**
 * The [DescopeLogger] class can be used if you need to customize how logging works in the
 * Descope SDK, but in most cases you can simply use [DescopeLogger.debugLogger] (see the
 * documentation for the `logger` property in [DescopeConfig] for more details).
 *
 * Create a subclass of [DescopeLogger] and override the [output] method. See the documentation
 * for that method for more details.
 *
 *     Descope.setup(this, projectId = "...") {
 *         logger = RemoteDescopeLogger()
 *     }
 *
 *     // elsewhere
 *
 *     class RemoveDescopeLogger: DescopeLogger(level = Level.Info, unsafe = false) {
 *         override fun output(level: Level, message: String, values: List<Any>) {
 *             RemoveLogger.sendLog("Descope: $message")
 *         }
 *     }
 *
 * The logging functions might be called concurrently on multiple threads, so you
 * should make sure your subclass implementation is thread safe.
 * 
 * @param level The maximum log level that should be printed.
 * @param unsafe Whether to print unsafe runtime value.
 */
open class DescopeLogger(open val level: Level, open val unsafe: Boolean) {
    /** Built-in console loggers for use during development. */
    companion object {
        /**
         * A simple logger that prints basic error and info logs using `println`.
         */
        val basicLogger: DescopeLogger by lazy { ConsoleLogger.basic }

        /**
         * A simple logger that prints all logs using `println`, but does not output any
         * potentially unsafe runtime values unless a debugger is attached.
         */
        val debugLogger: DescopeLogger by lazy { ConsoleLogger.debug }

        /**
         * A simple logger that prints all logs using `println`, including potentially unsafe
         * runtime values such as secrets, personal information, network payloads, etc.
         * 
         * - **IMPORTANT**: Do not use unsafeLogger in release builds intended for production.
         */
        val unsafeLogger: DescopeLogger by lazy { ConsoleLogger.unsafe }
    }
    
    /** The severity of a log message. */
    enum class Level {
        Error, Info, Debug
    }
    
    /** Called by other code in the Descope SDK to output log messages. */
    fun log(level: Level, message: String, vararg values: Any?) {
        if (level > this.level) return
        val filtered = if (unsafe) values.filterNotNull() else emptyList()
        output(level, message, filtered)
    }

    /**
     * Override this method to implement formatting and printing of logs from the Descope SDK.
     *
     * @param level the log level of the message.
     * @param message the log message is guaranteed to be a plain string that's safe for logging. You can
     * assume it doesn't contain any secrets, user data, or personal information and that it can be safely
     * printed or sent to a third party logging service.
     * @param values this array has runtime values that might be useful when debugging issues with
     * the Descope SDK. As these values are not considered safe this array is always empty unless
     * the logger was created with [unsafe] set to `true`.
     */
    open fun output(level: Level, message: String, values: List<Any>) {
    }
    
    @Deprecated(message = "Use DescopeLogger.basicLogger or DescopeLogger.debugLogger to diagnose issues during development")
    constructor(level: Level = Level.Debug) : this(level, false)
}

/**
 * The [DescopeNetworkClient] interface can be used to override how HTTP requests
 * are performed by the SDK when calling the Descope server.
 *
 * If you want to provide your own client for testing or other purposes, implement the
 * [sendRequest] function and either return some value or throw an exception.
 */
interface DescopeNetworkClient {
    /**
     * Send a request and expect an response string and a list of headers to be returned asynchronously
     * or an exception to be thrown
     *
     * @param url The request URL
     * @param method An upper case string representing the HTTP request method, e.g. "GET", "POST", etc
     * @param body An optional request body
     * @param headers The request headers
     * @return The response code, body and headers via the [Response] object
     */
    suspend fun sendRequest(url: URL, method: String, body: Map<String, Any?>?, headers: Map<String, String>): Response

    /**
     * A network response
     *
     * @property code The response HTTP code
     * @property body The response body as a string
     * @property headers All response headers mapped by header name to a list of its values
     */
    class Response(
        val code: Int,
        val body: String,
        val headers: Map<String, List<String>>
    )
}
