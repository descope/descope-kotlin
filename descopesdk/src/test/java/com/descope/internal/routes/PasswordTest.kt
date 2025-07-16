package com.descope.internal.routes

import com.descope.internal.http.OAuthServerResponse
import com.descope.types.SignUpDetails
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PasswordTest {
    @Test
    fun signUp() = runTest {
        val loginId = "test@test.com"
        val pw = "password"
        val details = SignUpDetails(name = "a", email = loginId, givenName = "b", middleName = "c", familyName = "d")
        val client = MockClient()
        val password = Password(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/password/signup", route)
            assertEquals(loginId, body["loginId"])
            assertEquals(pw, body["password"])
            details.validate(body)
        }
        client.response = mockJwtResponse
        val response = password.signUp(loginId, pw, details)
        assertEquals(jwt, response.sessionToken.jwt)
        assertEquals(1, client.calls)
    }

    @Test
    fun signIn() = runTest {
        val loginId = "test@test.com"
        val pw = "password"
        val client = MockClient()
        val password = Password(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/password/signin", route)
            assertEquals(loginId, body["loginId"])
            assertEquals(pw, body["password"])
        }
        client.response = mockJwtResponse
        val response = password.signIn(loginId, pw)
        assertEquals(jwt, response.sessionToken.jwt)
        assertEquals(1, client.calls)
    }

    @Test
    fun update() = runTest {
        val loginId = "test@test.com"
        val pw = "password"
        val client = MockClient()
        val password = Password(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/password/update", route)
            assertEquals(loginId, body["loginId"])
            assertEquals(pw, body["newPassword"])
        }
        client.response = mockJwtResponse
        password.update(loginId, pw, "refreshJwt")
        assertEquals(1, client.calls)
    }

    @Test
    fun replace() = runTest {
        val loginId = "test@test.com"
        val pw = "password"
        val newPw = "password"
        val client = MockClient()
        val password = Password(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/password/replace", route)
            assertEquals(loginId, body["loginId"])
            assertEquals(pw, body["oldPassword"])
            assertEquals(pw, body["newPassword"])
        }
        client.response = mockJwtResponse
        val response = password.replace(loginId, pw, newPw)
        assertEquals(jwt, response.sessionToken.jwt)
        assertEquals(1, client.calls)
    }

    @Test
    fun sendReset() = runTest {
        val loginId = "test@test.com"
        val uri = "https://mysite.com"
        val client = MockClient()
        val password = Password(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/password/reset", route)
            assertEquals(uri, body["redirectUrl"])
        }
        client.response = OAuthServerResponse("https://url.com")
        password.sendReset(loginId, uri)
        assertEquals(1, client.calls)
    }
}
