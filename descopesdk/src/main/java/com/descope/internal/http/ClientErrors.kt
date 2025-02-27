package com.descope.internal.http

import com.descope.internal.others.toMap
import com.descope.internal.others.with
import com.descope.types.DescopeException
import org.json.JSONObject

internal fun parseServerError(response: String): DescopeException? = try {
    val map = JSONObject(response).toMap()
    val code = map["errorCode"] as? String ?: throw Exception("errorCode is required")
    val desc = map["errorDescription"] as? String ?: "Descope server error"
    val message = map["errorMessage"] as? String
    DescopeException(code = code, desc = desc, message = message)
} catch (_: Exception) {
    null
}

internal fun exceptionFromResponseCode(code: Int): DescopeException? {
    val desc = failureFromResponseCode(code) ?: return null
    return DescopeException.httpError.with(desc = desc)
}

internal fun failureFromResponseCode(code: Int): String? {
    return when (code) {
        in 200..299 -> null
        400 -> "The request was invalid"
        401 -> "The request was unauthorized"
        403 -> "The request was forbidden"
        404 -> "The resource was not found"
        500, 503 -> "The request failed with status code $code"
        in 500..599 -> "The server was unreachable"
        else -> "The server returned status code $code"
    }
}
