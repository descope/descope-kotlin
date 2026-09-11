package com.descope.internal.others

import com.descope.types.DescopeException
import org.json.JSONObject

internal fun DescopeException.with(desc: String? = null, message: String? = null, cause: Throwable? = null, traceId: String? = null) = DescopeException(
    code = code,
    desc = desc ?: this.desc,
    message = message ?: this.message,
    cause = cause ?: this.cause,
    traceId = traceId ?: this.traceId,
)

internal fun parseServerError(response: String): DescopeException? {
    try {
        val map = JSONObject(response).toMap()
        val code = map["errorCode"] as? String ?: return null
        val desc = map["errorDescription"] as? String ?: "Descope server error"
        val message = map["errorMessage"] as? String
        return DescopeException(code = code, desc = desc, message = message)
    } catch (_: Exception) {
        return null
    }
}

/**
 * Detects whether a `console.error` message logged by the web component is its generic report
 * of a task that failed inside a scriptlet with `errorHandlingType: Automatic`, and if so, returns
 * the original thrown text.
 *
 * The web component logs a message shaped like this for such failures:
 *
 *     [Descope] [E181001]: Failed to execute script Unexpected error occurred - Error: <message> at <line>:<col> {}
 *
 * This matches on that shape rather than on any specific error text, so it isn't tied to what a
 * particular scriptlet happens to throw.
 */
internal fun scriptletFailureMessage(message: String): String? {
    if (!message.startsWith("[Descope] [") || !message.contains("]: Failed to execute script")) return null
    val errorIndex = message.indexOf("Error: ")
    if (errorIndex == -1) return message
    val remainder = message.substring(errorIndex + "Error: ".length)
    val atIndex = remainder.lastIndexOf(" at ")
    return if (atIndex == -1) remainder else remainder.substring(0, atIndex)
}
