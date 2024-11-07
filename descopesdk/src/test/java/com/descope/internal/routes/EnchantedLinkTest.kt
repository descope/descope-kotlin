package com.descope.internal.routes

import com.descope.internal.http.EnchantedLinkServerResponse
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.descope.types.UpdateOptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EnchantedLinkTest {
    @Test
    fun signUp() = runTest {
        val loginId = "test@test.com"
        val details = SignUpDetails(name = "a", email = loginId, givenName = "b", middleName = "c", familyName = "d")
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/enchantedlink/signup/email", route)
            assertEquals(loginId, body["loginId"])
            details.validate(body)
        }
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", "maskedEmail")
        val response = enchantedLink.signUp(loginId, details)
        assertEquals("maskedEmail", response.maskedEmail)
        assertEquals(1, client.calls)
    }

    @Test
    fun signIn() = runTest {
        val loginId = "test@test.com"
        val uri = "https://mysite.com"
        val options = listOf(SignInOptions.CustomClaims(mapOf("a" to "b")), SignInOptions.Mfa("refreshJwt"))
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, params: Map<String, String?> ->
            assertEquals("auth/enchantedlink/signin/email", route)
            assertEquals(loginId, body["loginId"])
            assertEquals(uri, body["uri"])
            options.validate(body)
        }
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", "maskedEmail")
        enchantedLink.signIn(loginId, uri, options)
        assertEquals(1, client.calls)
    }

    @Test
    fun signUpOrIn() = runTest {
        val loginId = "test@test.com"
        val options = listOf(SignInOptions.StepUp("refreshJwt"))
        val client = MockClient()
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/enchantedlink/signup-in/email", route)
            assertEquals(loginId, body["loginId"])
            options.validate(body)
        }
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", "maskedEmail")
        val enchantedLink = EnchantedLink(client)
        enchantedLink.signUpOrIn(loginId, options = options)
        assertEquals(1, client.calls)
    }

    @Test
    fun updateEmail() = runTest {
        val loginId = "test@test.com"
        val options = UpdateOptions(addToLoginIds = true, onMergeUseExisting = false)
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/enchantedlink/update/email", route)
            assertEquals(loginId, body["loginId"])
            assertEquals("test2@test.com", body["email"])
            options.validate(body)
        }
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", "maskedEmail")
        enchantedLink.updateEmail("test2@test.com", loginId, refreshJwt = "refreshJwt", options = options)
        assertEquals(1, client.calls)
    }

    @Test
    fun checkForSession() = runTest {
        val pendingRef = "pendingRef"
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/enchantedlink/pending-session", route)
            assertEquals(pendingRef, body["pendingRef"])
        }
        client.response = mockJwtResponse
        val response = enchantedLink.checkForSession(pendingRef)
        assertEquals(jwt, response.sessionToken.jwt)
        assertEquals(1, client.calls)
    }
}