package com.descope.session

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenTest {

    private val encoded = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJleHAiOjE1MTYyMzkwMjJ9.4Adcj3UFYzPUVaVF43FmMab6RlaQD8A9V8wFzzht-KQ"

    @Test
    fun jwt_decode() {
        val decoded = decodeJwt(encoded)
        assertEquals("1234567890", decoded["sub"])
        assertEquals("John Doe", decoded["name"])
        assertEquals(1516239022, decoded["exp"])
    }
}
