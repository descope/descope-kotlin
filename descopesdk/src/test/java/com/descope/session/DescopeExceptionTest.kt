package com.descope.session

import com.descope.internal.http.parseServerError
import com.descope.types.DescopeException
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
        val serverException = parseServerError(mockResponse)
        assertEquals(DescopeException.wrongOtpCode, serverException)
        when (serverException) {
            DescopeException.wrongOtpCode -> assertEquals("server description", serverException.desc)
            else -> fail("wrong when clause")
        }
    }
}
