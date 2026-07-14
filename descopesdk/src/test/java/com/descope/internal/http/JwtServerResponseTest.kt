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
        assertNotNull(response.flowOutput)
        assertEquals("value", response.flowOutput?.get("key"))
        assertEquals(3, response.flowOutput?.get("count"))
    }

    @Test
    fun flowOutput_nullWhenAbsent() {
        val json = """
            {
                "sessionJwt": "session",
                "refreshJwt": "refresh",
                "firstSeen": true
            }
        """.trimIndent()
        val response = JwtServerResponse.fromJson(json, emptyList())
        assertNull(response.flowOutput)
    }

    @Test
    fun flowOutput_nullWhenEmpty() {
        val json = """
            {
                "sessionJwt": "session",
                "refreshJwt": "refresh",
                "firstSeen": true,
                "flowOutput": {}
            }
        """.trimIndent()
        val response = JwtServerResponse.fromJson(json, emptyList())
        assertNull(response.flowOutput)
    }
}
