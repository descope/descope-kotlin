package com.descope.internal.routes

import com.descope.internal.http.SsoServerResponse
import com.descope.types.SignInOptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SsoTest {
    @Test
    fun start() = runTest {
        val uri = "https://mysite.com"
        val options = listOf(SignInOptions.CustomClaims(mapOf("a" to "b")), SignInOptions.Mfa("refreshJwt"))
        val client = MockClient()
        val sso = Sso(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, params: Map<String, String?> ->
            assertEquals("auth/saml/authorize", route)
            assertEquals(uri, params["redirectURL"])
            assertEquals("tenant", params["tenant"])
            options.validate(body)
        }
        client.response = SsoServerResponse("https://url.com")
        val response = sso.start("tenant", uri, options)
        assertEquals("https://url.com", response)
        assertEquals(1, client.calls)
    }

    @Test
    fun exchange() = runTest {
        val code = "code"
        val client = MockClient()
        val sso = Sso(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/saml/exchange", route)
            assertEquals(code, body["code"])
        }
        client.response = mockJwtResponse
        val response = sso.exchange(code)
        assertEquals(jwt, response.sessionToken.jwt)
        assertEquals(1, client.calls)
    }
}
