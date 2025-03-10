package com.descope.sdk

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
     * An optional logger to use for logging messages in the Descope SDK.
     * - _**IMPORTANT**: Logging is intended for `DEBUG` only. Do not enable logs when building
     * the `RELEASE` versions of your application._
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
 * _**IMPORTANT**: Logging is intended for `DEBUG` only. Do not enable logs when building
 * the `RELEASE` versions of your application._
 * 
 * The [DescopeLogger] class can be used to customize logging functionality in the Descope SDK.
 *
 * The default behavior is for log messages to be written to the standard output using
 * the `println()` function.
 *
 * You can also customize how logging functions in the Descope SDK by creating a subclass
 * of [DescopeLogger] and overriding the [DescopeLogger.output] method. See the
 * documentation for that method for more details.
 */
open class DescopeLogger(private val level: Level = Level.Debug) {

    /** The severity of a log message. */
    enum class Level {
        Error, Info, Debug
    }
    
    internal var isDebug: Boolean = false

    /**
     * Formats the log message and prints it.
     *
     * Override this method to customize how to handle log messages from the Descope SDK.
     *
     * @param level the log level printed
     * @param message the message to print
     * @param values any associated values. _**IMPORTANT** - sensitive information may be printed here. Enable logs only when debugging._
     */
    open fun output(level: Level, message: String, vararg values: Any?) {
        var text = "[${DescopeSdk.NAME}] $message"
        val filtered = values.filterNotNull()
        if (filtered.isNotEmpty()) {
            text += """ (${filtered.joinToString(", ") { v -> v.toString() }})"""
        }
        println(text);
    }

    // Called by other code in the Descope SDK to output log messages.
    fun log(level: Level, message: String, vararg values: Any?) {
        if (level > this.level) return
        output(level, message, *(if (isDebug) values else emptyArray()))
    }   
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
