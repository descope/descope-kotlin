package com.descope.types

import com.descope.internal.others.parseServerError
import com.descope.internal.others.scriptletFailureMessage
import com.descope.internal.others.with
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun error_message_preserved() {
        val payload = JSONObject().apply {
            put("errorCode", "E102122")
            put("errorDescription", "Flow aborted")
            put("errorMessage", "User canceled")
        }.toString()
        val exception = parseServerError(payload)
        assertEquals("E102122", exception?.code)
        assertEquals("Flow aborted", exception?.desc)
        assertEquals("User canceled", exception?.message)
    }

    @Test
    fun trace_id_preserved() {
        val exception = DescopeException(code = "E061102", desc = "server description", traceId = "8c8e3f5a1b2c3d4e-TLV")
        assertEquals("8c8e3f5a1b2c3d4e-TLV", exception.traceId)
        assertTrue(exception.toString().contains("""traceId: "8c8e3f5a1b2c3d4e-TLV""""))
        val modified = exception.with(message = "some reason")
        assertEquals("some reason", modified.message)
        assertEquals("8c8e3f5a1b2c3d4e-TLV", modified.traceId)
    }

    @Test
    fun trace_id_null_by_default() {
        val payload = JSONObject().apply {
            put("errorCode", "E061102")
            put("errorDescription", "server description")
        }.toString()
        val exception = parseServerError(payload)
        assertNull(exception?.traceId)
        assertEquals(false, exception.toString().contains("traceId"))
    }

    @Test
    fun invalid_error_payload_parsing() {
        assertNull(parseServerError("not a json error"))
        assertNull(parseServerError(JSONObject().apply { put("errorMessage", "no code here") }.toString()))
    }

    @Test
    fun scriptlet_failure_message_extracted() {
        val logged = "[Descope] [E181001]: Failed to execute script Unexpected error occurred - Error: USER_NOT_FOUND: no such user at 12:34 {}"
        assertEquals("USER_NOT_FOUND: no such user", scriptletFailureMessage(logged))
    }

    @Test
    fun scriptlet_failure_message_ignores_unrelated_logs() {
        assertNull(scriptletFailureMessage("some unrelated console.error message"))
        assertNull(scriptletFailureMessage("[Descope] [E181001]: some other log line"))
    }

    @Test
    fun flow_scriptlet_failed_carries_extracted_message() {
        val logged = "[Descope] [E181001]: Failed to execute script Unexpected error occurred - Error: USER_NOT_FOUND: no such user at 12:34 {}"
        val extracted = scriptletFailureMessage(logged)
        val exception = DescopeException.flowScriptletFailed.with(message = extracted)
        assertEquals(DescopeException.flowScriptletFailed, exception)
        assertEquals("USER_NOT_FOUND: no such user", exception.message)
    }

}
