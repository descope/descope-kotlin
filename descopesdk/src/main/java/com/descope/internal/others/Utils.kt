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

internal fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    keys().forEach { key ->
        map[key] = when(val obj = get(key)) {
            is JSONObject -> obj.toMap()
            is JSONArray -> obj.toList()
            else -> obj
        }
    }
    return map.toMap()
}

internal fun JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until length()) {
        list.add(when(val obj = get(i)) {
            is JSONObject -> obj.toMap()
            is JSONArray -> obj.toList()
            else -> obj
        })
    }
    return list
}

internal fun JSONObject.optionalMap(key: String): Map<String, Any> = try {
    val obj = getJSONObject(key)
    obj.toMap()
} catch (ignored: JSONException) {
    emptyMap()
}

internal fun JSONArray.toStringList(): List<String> {
    val list = mutableListOf<String>()
    for (i in 0 until length()) {
        list.add(getString(i))
    }
    return list
}

internal fun List<*>.toJsonArray(): JSONArray = JSONArray().apply {
    this@toJsonArray.forEach {
        when {
            it is Map<*, *> -> put(it.toJsonObject())
            it is List<*> -> put(it.toJsonArray())
            it != null -> put(it)
        }
    }
}

internal fun Map<*, *>.toJsonObject(): JSONObject = JSONObject().apply {
    forEach {
        val key = it.key as String
        val value = it.value
        when {
            value is Map<*, *> -> put(key, value.toJsonObject())
            value is List<*> -> put(key, value.toJsonArray())
            value != null -> put(key, value)
        }
    }
}

// General

internal fun Long.secToMs() = this * 1000L

inline fun <reified T> tryOrNull(block: () -> T): T? = try {
    block()
} catch (e: Exception) {
    null
}
