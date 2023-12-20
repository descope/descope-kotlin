package com.descope.internal.http

import android.net.Uri
import com.descope.internal.others.toJsonObject
import com.descope.internal.others.with
import com.descope.sdk.DescopeLogger
import com.descope.sdk.DescopeLogger.Level.Debug
import com.descope.sdk.DescopeLogger.Level.Error
import com.descope.sdk.DescopeLogger.Level.Info
import com.descope.types.DescopeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpCookie
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal open class HttpClient(
    private val baseUrl: String,
    private val logger: DescopeLogger?,
) {

    // Convenience functions

    suspend fun <T> get(
        route: String,
        decoder: (String, List<HttpCookie>) -> T,
        headers: Map<String, String> = emptyMap(),
        params: Map<String, String?> = emptyMap(),
    ) = call(route, "GET", decoder, headers = headers, params = params)

    suspend fun <T> post(
        route: String,
        decoder: (String, List<HttpCookie>) -> T,
        body: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        params: Map<String, String?> = emptyMap(),
    ) = call(route, "POST", decoder, body, headers, params)

    // Override Points

    open val basePath = "/"

    open val defaultHeaders: Map<String, String> = emptyMap()

    open fun exceptionFromResponse(response: String): Exception? = null

    // Internal

    private suspend fun <T> call(
        route: String,
        method: String,
        decoder: (String, List<HttpCookie>) -> T,
        body: Map<String, Any?>? = null,
        headers: Map<String, String>,
        params: Map<String, String?>,
    ): T = withContext(Dispatchers.IO) {
        val url = makeUrl(route, params)
        logger?.log(Info, "Starting network call", url)

        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            defaultHeaders.forEach { connection.setRequestProperty(it.key, it.value) }
            headers.forEach { connection.setRequestProperty(it.key, it.value) }

            // Send body if needed
            body?.run {
                logger?.log(Debug, "Sending request body", this)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                // Send the request
                connection.outputStream.bufferedWriter().use {
                    it.write(toJsonObject().toString())
                    it.flush()
                }
            }

            // Return response
            val responseCode = connection.responseCode
            if (responseCode == HttpsURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                logger?.log(Debug, "Received response body", response)
                decoder(response, connection.cookies)
            } else {
                val response = connection.errorStream.bufferedReader().use { it.readText() }
                logger?.log(Debug, "Received error body", response)
                exceptionFromResponse(response)?.run {
                    logger?.log(Error, "Network call failed with server error", url, responseCode, this)
                    throw this
                }
                logger?.log(Error, "Network call failed with server error", url, responseCode)
                throw exceptionFromResponseCode(responseCode) ?: Exception("Network error")
            }
        } catch (e: Exception) {
            if (e !is DescopeException) {
                logger?.log(Error, "Network call failed with network error", url, e)
                throw DescopeException.networkError.with(cause = e)
            }
            throw e
        } finally {
            logger?.log(Info, "Network call finished", url)
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

private val HttpsURLConnection.cookies: List<HttpCookie>
    get() {
        val cookies = mutableListOf<HttpCookie>()
        headerFields.keys.find { it?.lowercase() == "set-cookie" }?.let { key ->
            headerFields[key]?.forEach {
                try {
                    cookies.addAll(HttpCookie.parse(it))
                } catch (ignored: Exception) {}
            }
        }
        return cookies.toList()
    }
