package com.descope.sdk

/** The default base URL for the Descope API. */
const val DEFAULT_BASE_URL = "https://api.descope.com"

/**
 * The configuration of the Descope SDK.
 *
 * @property projectId the id of the Descope project.
 * @property baseUrl the base URL of the Descope server.
 * @property logger an option logger to use to log messages in the Descope SDK.
 * _**IMPORTANT**: Logging is intended for `DEBUG` only. Do not enable logs when building
 * the `RELEASE` versions of your application._
 */
data class DescopeConfig(
    val projectId: String,
    val baseUrl: String = DEFAULT_BASE_URL,
    val logger: DescopeLogger? = null,
) {

    companion object {
        val initial = DescopeConfig("")
    }

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

    /**
     * Formats the log message and prints it.
     *
     * Override this method to customize how to handle log messages from the Descope SDK.
     *
     * @param level the log level printed
     * @param message the message to print
     * @param values any associated values. _**IMPORTANT** - sensitive information may be printed here. Enable logs only when debugging._
     */
    open fun output(level: Level, message: String, vararg values: Any) {
        var text = "[${DescopeSdk.name}] $message"
        if (values.isNotEmpty()) {
            text += """ (${values.joinToString(", ") { v -> v.toString() }})"""
        }
        println(text);
    }

    // Called by other code in the Descope SDK to output log messages.
    fun log(level: Level, message: String, vararg values: Any) {
        if (level <= this.level) {
            output(level, message, *values)
        }
    }
}
