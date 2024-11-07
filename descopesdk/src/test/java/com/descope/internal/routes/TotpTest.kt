package com.descope.internal.routes

import com.descope.internal.http.TotpServerResponse
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TotpTest {
    @Test
    fun signUp() = runTest {
        val loginId = "test@test.com"
        val details = SignUpDetails(name = "a", email = loginId, givenName = "b", middleName = "c", familyName = "d")
        val client = MockClient()
        val totp = Totp(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/totp/signup", route)
            assertEquals(loginId, body["loginId"])
            details.validate(body)
        }
        client.response = TotpServerResponse("https://url.com", ByteArray(0), "key")
        val response = totp.signUp(loginId, details)
        assertEquals("https://url.com", response.provisioningUrl)
        assertEquals(1, client.calls)
    }

    @Test
    fun update() = runTest {
        val loginId = "test@test.com"
        val client = MockClient()
        val totp = Totp(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/totp/update", route)
            assertEquals(loginId, body["loginId"])
        }
        client.response = TotpServerResponse("https://url.com", ByteArray(0), "key")
        val response = totp.update(loginId, "refreshJwt")
        assertEquals("https://url.com", response.provisioningUrl)
        assertEquals(1, client.calls)
    }

    @Test
    fun verify() = runTest {
        val loginId = "test@test.com"
        val code = "code"
        val options = listOf(SignInOptions.CustomClaims(mapOf("a" to "b")), SignInOptions.Mfa("refreshJwt"))
        val client = MockClient()
        val totp = Totp(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/totp/verify", route)
            assertEquals(loginId, body["loginId"])
            assertEquals(code, body["code"])
            options.validate(body)
        }
        client.response = mockJwtResponse
        val response = totp.verify(loginId, code, options)
        assertEquals(jwt, response.sessionToken.jwt)
        assertEquals(1, client.calls)
    }
}