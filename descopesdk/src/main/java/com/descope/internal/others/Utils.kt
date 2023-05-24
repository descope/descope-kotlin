package com.descope.internal.others

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

// JSON

internal fun JSONObject.stringOrEmptyAsNull(key: String): String? = try {
    getString(key).ifEmpty { null }
} catch (ignored: JSONException) {
    null
}

internal fun JSONArray.toStringList(): List<String> {
    val list = mutableListOf<String>()
    for (i in 0 until length()) {
        list.add(getString(i))
    }
    return list
}

internal fun List<String>.toJsonArray(): JSONArray = JSONArray().apply { this@toJsonArray.forEach { put(it) } }

// General

internal fun Long.secToMs() = this * 1000L

inline fun <reified T> tryOrNull(block: () -> T): T? = try {
    block()
} catch (e: Exception) {
    null
}
