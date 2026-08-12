package com.descope.internal.http

import com.descope.android.SystemInfo
import com.descope.sdk.DescopeConfig
import com.descope.sdk.DescopeNetworkClient
import com.descope.types.DescopeException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class DescopeClientTest {

    @Test
    fun baseUrlForProjectId_variousInputs() {
        assertEquals("https://api.descope.com", baseUrlForProjectId(""))
        assertEquals("https://api.descope.com", baseUrlForProjectId("Puse"))
        assertEquals("https://api.descope.com", baseUrlForProjectId("Puse1ar"))
        assertEquals("https://api.use1.descope.com", baseUrlForProjectId("Puse12aAc4T2V93bddihGEx2Ryhc8e5Z"))
        assertEquals("https://api.use1.descope.com", baseUrlForProjectId("Puse12aAc4T2V93bddihGEx2Ryhc8e5Zfoobar"))
    }

    @Test
    fun serverError_carriesTraceIdFromCfRayHeader() = runTest {
        val client = mockClient(
            code = 400,
            responseBody = """{"errorCode":"E061102","errorDescription":"server description"}""",
            responseHeaders = mapOf("CF-Ray" to listOf("8c8e3f5a1b2c3d4e-TLV")),
        )
        try {
            client.post("auth/test", { _, _ -> })
            fail("Expected a DescopeException to be thrown")
        } catch (e: DescopeException) {
            assertEquals("E061102", e.code)
            assertEquals("8c8e3f5a1b2c3d4e-TLV", e.traceId)
        }
    }

    @Test
    fun httpError_carriesTraceIdFromCfRayHeader() = runTest {
        val client = mockClient(
            code = 500,
            responseBody = "internal error",
            responseHeaders = mapOf("cf-ray" to listOf("8c8e3f5a1b2c3d4e-TLV")),
        )
        try {
            client.post("auth/test", { _, _ -> })
            fail("Expected a DescopeException to be thrown")
        } catch (e: DescopeException) {
            assertEquals(DescopeException.httpError, e)
            assertEquals("8c8e3f5a1b2c3d4e-TLV", e.traceId)
        }
    }

    @Test
    fun error_withoutCfRayHeader_hasNullTraceId() = runTest {
        val client = mockClient(
            code = 400,
            responseBody = """{"errorCode":"E061102","errorDescription":"server description"}""",
        )
        try {
            client.post("auth/test", { _, _ -> })
            fail("Expected a DescopeException to be thrown")
        } catch (e: DescopeException) {
            assertEquals("E061102", e.code)
            assertNull(e.traceId)
        }
    }

    @Test
    fun successfulResponse_isDecoded() = runTest {
        val client = mockClient(code = 200, responseBody = "response body")
        assertEquals("response body", client.post("auth/test", { body, _ -> body }))
    }

    private fun mockClient(code: Int, responseBody: String, responseHeaders: Map<String, List<String>> = emptyMap()): DescopeClient {
        val config = DescopeConfig("p1").apply {
            networkClient = object : DescopeNetworkClient {
                override suspend fun sendRequest(url: URL, method: String, body: Map<String, Any?>?, headers: Map<String, String>) =
                    DescopeNetworkClient.Response(code = code, body = responseBody, headers = responseHeaders)
            }
        }
        return DescopeClient(config, object : SystemInfo {
            override val appName = "appName"
            override val appVersion = "appVersion"
            override val platformVersion = "platformVersion"
            override val device = "device"
        })
    }

}
