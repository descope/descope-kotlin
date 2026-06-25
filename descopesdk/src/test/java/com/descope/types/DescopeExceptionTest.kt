package com.descope.types

import com.descope.internal.others.toDescopeException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class DescopeExceptionTest {

    @Test
    fun error_comparison() {
        val mockResponse = JSONObject().apply {
            put("errorCode", "E061102")
            put("errorDescription", "server description")
            put("errorMessage", "some reason")
        }.toString()
        val serverException = mockResponse.toDescopeException()
        assertEquals(DescopeException.wrongOtpCode, serverException)
        when (serverException) {
            DescopeException.wrongOtpCode -> assertEquals("server description", serverException.desc)
            else -> fail("wrong when clause")
        }
    }

    @Test
    fun flow_aborted_parsing() {
        val payload = JSONObject().apply {
            put("errorCode", "E102122")
            put("errorDescription", "Flow aborted")
            put("errorMessage", "User canceled")
        }.toString()
        val exception = payload.toDescopeException()
        assertEquals(DescopeException.flowAborted, exception)
        assertEquals("User canceled", exception?.message)
    }

    @Test
    fun invalid_error_payload_parsing() {
        assertNull("not a json error".toDescopeException())
        assertNull(JSONObject().apply { put("errorMessage", "no code here") }.toString().toDescopeException())
    }

}
