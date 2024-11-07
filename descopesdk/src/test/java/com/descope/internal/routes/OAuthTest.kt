package com.descope.internal.routes

import com.descope.internal.http.OAuthServerResponse
import com.descope.types.OAuthProvider
import com.descope.types.SignInOptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OAuthTest {
    @Test
    fun signUp() = runTest {
        val uri = "https://mysite.com"
        val options = listOf(SignInOptions.CustomClaims(mapOf("a" to "b")), SignInOptions.Mfa("refreshJwt"))
        val client = MockClient()
        val oAuth = OAuth(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, params: Map<String, String?> ->
            assertEquals("auth/oauth/authorize/signup", route)
            assertEquals(uri, params["redirectURL"])
            assertEquals("google", params["provider"])
            options.validate(body)
        }
        client.response = OAuthServerResponse("https://url.com")
        val response = oAuth.signUp(OAuthProvider.Google, uri, options)
        assertEquals("https://url.com", response)
        assertEquals(1, client.calls)
    }

    @Test
    fun signIn() = runTest {
        val uri = "https://mysite.com"
        val options = listOf(SignInOptions.CustomClaims(mapOf("a" to "b")), SignInOptions.Mfa("refreshJwt"))
        val client = MockClient()
        val oAuth = OAuth(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, params: Map<String, String?> ->
            assertEquals("auth/oauth/authorize/signin", route)
            assertEquals(uri, params["redirectURL"])
            assertEquals("google", params["provider"])
            options.validate(body)
        }
        client.response = OAuthServerResponse("https://url.com")
        val response = oAuth.signIn(OAuthProvider.Google, uri, options)
        assertEquals("https://url.com", response)
        assertEquals(1, client.calls)
    }

    @Test
    fun signUpOrIn() = runTest {
        val uri = "https://mysite.com"
        val options = listOf(SignInOptions.CustomClaims(mapOf("a" to "b")), SignInOptions.Mfa("refreshJwt"))
        val client = MockClient()
        val oAuth = OAuth(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, params: Map<String, String?> ->
            assertEquals("auth/oauth/authorize", route)
            assertEquals(uri, params["redirectURL"])
            assertEquals("google", params["provider"])
            options.validate(body)
        }
        client.response = OAuthServerResponse("https://url.com")
        val response = oAuth.signUpOrIn(OAuthProvider.Google, uri, options)
        assertEquals("https://url.com", response)
        assertEquals(1, client.calls)
    }

    @Test
    fun exchange() = runTest {
        val code = "code"
        val client = MockClient()
        val oAuth = OAuth(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/oauth/exchange", route)
            assertEquals(code, body["code"])
        }
        client.response = mockJwtResponse
        val response = oAuth.exchange(code)
        assertEquals(jwt, response.sessionToken.jwt)
        assertEquals(1, client.calls)
    }
}
