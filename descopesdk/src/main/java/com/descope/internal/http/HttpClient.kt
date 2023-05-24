package com.descope.internal.http

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal open class HttpClient(
    private val baseUrl: String,
) {

    // Convenience functions

    suspend fun <T> get(
        route: String,
        decoder: (String) -> T,
        headers: Map<String, String> = emptyMap(),
        params: Map<String, String?> = emptyMap(),
    ) = call(route, "GET", decoder, headers = headers, params = params)

    suspend fun <T> post(
        route: String,
        decoder: (String) -> T,
        body: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        params: Map<String, String?> = emptyMap(),
    ) = call(route, "POST", decoder, body, headers, params)

    // Override Points

    open val basePath = "/"

    open val defaultHeaders: Map<String, String> = emptyMap()

    // Internal

    private suspend fun <T> call(
        route: String,
        method: String,
        decoder: (String) -> T,
        body: Map<String, Any?>? = null,
        headers: Map<String, String>,
        params: Map<String, String?>,
    ): T = withContext(Dispatchers.IO) {
        val url = makeUrl(route, params)

        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            defaultHeaders.forEach { connection.setRequestProperty(it.key, it.value) }
            headers.forEach { connection.setRequestProperty(it.key, it.value) }

            // Send body if needed
            body?.run {
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                // Send the request
                connection.outputStream.bufferedWriter().use {
                    it.write(JSONObject().apply {
                        filterValues { value ->  value != null }
                            .forEach { entry -> put(entry.key, entry.value) }
                    }.toString())
                    it.flush()
                }
            }

            // Return response
            val responseCode = connection.responseCode
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                decoder(response)
            } else {
                // TODO: handle error responses and network errors
                throw Exception("Network error")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun makeUrl(route: String, params: Map<String, String?>): URL {
        val composed = "$baseUrl$basePath$route"
        val urlString = if (params.isNotEmpty()) {
            Uri.parse(composed).buildUpon().apply {
                params
                    .filterValues { it != null }
                    .forEach { appendQueryParameter(it.key, it.value) }
            }.build().toString()
        } else composed
        return URL(urlString)
    }
}
