package com.descope.session

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenTest {

    private val encoded = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJodHRwczovL2Rlc2NvcGUuY29tL2JsYS9QMTIzIiwiZXhwIjoxNjAzMTc2NjE0LCJwZXJtaXNzaW9ucyI6WyJkIiwiZSJdLCJyb2xlcyI6WyJ1c2VyIl0sInRlbmFudHMiOnsidGVuYW50Ijp7InBlcm1pc3Npb25zIjpbImEiLCJiIiwiYyJdLCJyb2xlcyI6WyJhZG1pbiJdfX19.MCSE6ZTlD0oVS0FhXe5LwBpbUVG8H5RmwU7sk_L7Bbo"

    @Test
    fun jwt_decode() {
        val token = Token(encoded)

        assertEquals(encoded, token.jwt)
        
        // Basic Fields
        assertEquals("1234567890", token.entityId)
        assertEquals("P123", token.projectId)
        assertEquals(1603176614000, token.expiresAt)
        
        // Custom Claims
        assertEquals("John Doe", token.claims["name"])
        assertEquals(1, token.claims.size)

        // Authorization
        assertEquals(listOf("d", "e"), token.permissions(tenant = null))
        assertEquals(listOf("user"), token.roles(tenant = null))

        // Tenant Authorization
        assertEquals(listOf("a", "b", "c"), token.permissions(tenant = "tenant"))
        assertEquals(listOf("admin"), token.roles(tenant = "tenant"))
        assertEquals(emptyList<String>(), token.permissions(tenant = "no-such-tenant"))
    }
}
