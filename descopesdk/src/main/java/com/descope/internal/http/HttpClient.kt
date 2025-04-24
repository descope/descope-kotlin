package com.descope.internal.http

import android.net.Uri
import com.descope.internal.others.debug
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.internal.others.isUnsafeEnabled
import com.descope.internal.others.toJsonObject
import com.descope.internal.others.with
import com.descope.sdk.DescopeLogger
import com.descope.sdk.DescopeNetworkClient
import com.descope.types.DescopeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpCookie
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal open class HttpClient(
    private val baseUrl: String,
    private val logger: DescopeLogger?,
    client: DescopeNetworkClient?,
) {

    private val networkClient: DescopeNetworkClient = client ?: defaultNetworkClient(logger)

    // Convenience functions

    open suspend fun <T> get(
        route: String,
        decoder: (String, List<HttpCookie>) -> T,
        headers: Map<String, String> = emptyMap(),
        params: Map<String, String?> = emptyMap(),
    ) = call(route, "GET", decoder, headers = headers, params = params)

    open suspend fun <T> post(
        route: String,
        decoder: (String, List<HttpCookie>) -> T,
        body: Map<String, Any?> = emptyMap(),
        headers: Map<String, String> = emptyMap(),
        params: Map<String, String?> = emptyMap(),
    ) = call(route, "POST", decoder, body, headers, params)

    // Override Points

    open val basePath = "/"

    open val defaultHeaders: Map<String, String> = emptyMap()

    open fun exceptionFromResponse(response: String): DescopeException? = null

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
        val combinedHeaders = mutableMapOf<String, String>().apply {
            putAll(defaultHeaders)
            putAll(headers)
        }

        logger.info("Starting network call", url)
        if (logger.isUnsafeEnabled) {
            logger.info("Starting network call", url)
        } else {
            logger.info("Starting network call to $route")
        }

        val result = try {
            val response = networkClient.sendRequest(url, method, body, combinedHeaders)
            if (response.code != HttpsURLConnection.HTTP_OK) {
                exceptionFromResponse(response.body)?.let { e ->
                    logger.error("Network call failed with server error", url, response.code, e)
                    if (logger.isUnsafeEnabled) {
                        logger.error("Network call failed with server error", url, e)
                    } else {
                        logger.error("Network call to $route failed with ${e.code} server error")
                    }
                    throw e
                }
                exceptionFromResponseCode(response.code)?.let { e ->
                    if (logger.isUnsafeEnabled) {
                        logger.error("Network call failed with ${response.code} http error", url, e)
                    } else {
                        logger.error("Network call to $route failed with ${response.code} http error")
                    }
                    throw e
                }
                throw Exception("Unexpected response")
            }

            decoder(response.body, response.headers.cookies)
        } catch (e: DescopeException) {
            throw e // just rethrow
        } catch (e: Exception) {
            if (logger.isUnsafeEnabled) {
                logger.error("Network call failed with network error", url, e)
            } else {
                val type = e::class.java.simpleName ?: "unknown"
                logger.error("Network call to $route failed with $type network error")
            }
            throw DescopeException.networkError.with(cause = e)
        }

        if (logger.isUnsafeEnabled) {
            logger.info("Network call finished", url)
        } else {
            logger.info("Network call to $route finished")
        }
        
        result
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

private val Map<String, List<String>>.cookies: List<HttpCookie>
    get() {
        val cookies = mutableListOf<HttpCookie>()
        keys.find { it.lowercase() == "set-cookie" }?.let { key ->
            this[key]?.forEach {
                try {
                    cookies.addAll(HttpCookie.parse(it))
                } catch (ignored: Exception) {}
            }
        }
        return cookies.toList()
    }

private fun defaultNetworkClient(logger: DescopeLogger?) = object : DescopeNetworkClient {
    override suspend fun sendRequest(url: URL, method: String, body: Map<String, Any?>?, headers: Map<String, String>): DescopeNetworkClient.Response {
        val connection = url.openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { connection.setRequestProperty(it.key, it.value) }

            // Send body if needed
            if (body != null) {
                val data = body.toJsonObject().toString()
                if (logger.isUnsafeEnabled) {
                    logger.debug("Sending request body", data)
                }
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                // Send the request
                connection.outputStream.bufferedWriter().use {
                    it.write(data)
                    it.flush()
                }
            }

            // Return response
            val responseCode = connection.responseCode
            val responseBody = if (responseCode == HttpsURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream.bufferedReader().use { it.readText() }
            }
            val responseHeaders = connection.headerFields.filterKeys {
                !it.isNullOrEmpty() // inexplicably, the keys can be null
            }
            if (logger.isUnsafeEnabled) {
                logger.debug("Received response body", responseBody)
            }
            return DescopeNetworkClient.Response(code = responseCode, body = responseBody, headers = responseHeaders)
        } finally {
            connection.disconnect()
        }
    }
}
