package com.descope.internal.others

import com.descope.types.DescopeException
import org.json.JSONObject

internal fun DescopeException.with(desc: String? = null, message: String? = null, cause: Throwable? = null) = DescopeException(
    code = code,
    desc = desc ?: this.desc,
    message = message ?: this.message,
    cause = cause ?: this.cause,
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
