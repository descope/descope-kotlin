package com.descope.internal.http

import org.junit.Assert.*
import org.junit.Test

class JwtServerResponseTest {

    @Test
    fun externalToken_populatedWhenPresent() {
        val json = """
            {
                "sessionJwt": "session",
                "refreshJwt": "refresh",
                "firstSeen": true,
                "externalToken": "ext-token-value"
            }
        """.trimIndent()
        val response = JwtServerResponse.fromJson(json, emptyList())
        assertEquals("ext-token-value", response.externalToken)
    }

    @Test
    fun externalToken_nullWhenAbsent() {
        val json = """
            {
                "sessionJwt": "session",
                "refreshJwt": "refresh",
                "firstSeen": true
            }
        """.trimIndent()
        val response = JwtServerResponse.fromJson(json, emptyList())
        assertNull(response.externalToken)
    }

    @Test
    fun externalPasskey_trueWhenPresent() {
        val json = """
            {
                "sessionJwt": "session",
                "refreshJwt": "refresh",
                "externalPasskey": true
            }
        """.trimIndent()
        val response = JwtServerResponse.fromJson(json, emptyList())
        assertTrue(response.externalPasskey)
    }

    @Test
    fun externalPasskey_falseWhenAbsent() {
        val json = """
            {
                "sessionJwt": "session",
                "refreshJwt": "refresh"
            }
        """.trimIndent()
        val response = JwtServerResponse.fromJson(json, emptyList())
        assertFalse(response.externalPasskey)
    }

    @Test
    fun flowOutput_populatedWhenPresent() {
        val json = """
            {
                "sessionJwt": "session",
                "refreshJwt": "refresh",
                "firstSeen": true,
                "flowOutput": {
                    "key": "value",
                    "count": 3,
                    "nested": { "inner": true }
                }
            }
        """.trimIndent()
        val response = JwtServerResponse.fromJson(json, emptyList())
        assertEquals("value", response.flowOutput["key"])
        assertEquals(3, response.flowOutput["count"])
    }

    @Test
    fun flowOutput_emptyWhenAbsent() {
        val json = """
            {
                "sessionJwt": "session",
                "refreshJwt": "refresh",
                "firstSeen": true
            }
        """.trimIndent()
        val response = JwtServerResponse.fromJson(json, emptyList())
        assertTrue(response.flowOutput.isEmpty())
    }

    @Test
    fun flowOutput_emptyWhenEmpty() {
        val json = """
            {
                "sessionJwt": "session",
                "refreshJwt": "refresh",
                "firstSeen": true,
                "flowOutput": {}
            }
        """.trimIndent()
        val response = JwtServerResponse.fromJson(json, emptyList())
        assertTrue(response.flowOutput.isEmpty())
    }
}
