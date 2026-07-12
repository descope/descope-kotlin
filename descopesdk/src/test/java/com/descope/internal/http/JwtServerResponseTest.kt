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
}
