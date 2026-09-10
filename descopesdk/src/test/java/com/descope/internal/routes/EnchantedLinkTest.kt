package com.descope.internal.routes

import com.descope.internal.http.EnchantedLinkServerResponse
import com.descope.types.DeliveryMethod
import com.descope.types.DescopeException
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.descope.types.UpdateOptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class EnchantedLinkTest {
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
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", maskedEmail = "maskedEmail")
        val response = enchantedLink.updateEmail("test2@test.com", loginId, refreshJwt = "refreshJwt", options = options)
        assertEquals("maskedEmail", response.maskedEmail)
        assertNull(response.maskedPhone)
        assertEquals(1, client.calls)
    }

    @Test
    fun signUpOverEmail() = runTest {
        val loginId = "test@test.com"
        val details = SignUpDetails(name = "a", email = loginId, givenName = "b", middleName = "c", familyName = "d")
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/enchantedlink/signup/email", route)
            assertEquals(loginId, body["loginId"])
            details.validate(body)
        }
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", maskedEmail = "maskedEmail")
        val response = enchantedLink.signUp(DeliveryMethod.Email, loginId, details)
        assertEquals("maskedEmail", response.maskedEmail)
        assertNull(response.maskedPhone)
        assertEquals(1, client.calls)
    }

    @Test
    fun signInOverEmail() = runTest {
        val loginId = "test@test.com"
        val uri = "https://mysite.com"
        val options = listOf(SignInOptions.CustomClaims(mapOf("a" to "b")), SignInOptions.Mfa("refreshJwt"))
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/enchantedlink/signin/email", route)
            assertEquals(loginId, body["loginId"])
            assertEquals(uri, body["redirectUrl"])
            options.validate(body)
        }
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", maskedEmail = "maskedEmail")
        val response = enchantedLink.signIn(DeliveryMethod.Email, loginId, uri, options)
        assertEquals("maskedEmail", response.maskedEmail)
        assertNull(response.maskedPhone)
        assertEquals(1, client.calls)
    }

    @Test
    fun signUpOrInOverEmail() = runTest {
        val loginId = "test@test.com"
        val options = listOf(SignInOptions.StepUp("refreshJwt"))
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/enchantedlink/signup-in/email", route)
            assertEquals(loginId, body["loginId"])
            options.validate(body)
        }
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", maskedEmail = "maskedEmail")
        val response = enchantedLink.signUpOrIn(DeliveryMethod.Email, loginId, options = options)
        assertEquals("maskedEmail", response.maskedEmail)
        assertEquals(1, client.calls)
    }

    @Test
    fun signUpOverSms() = runTest {
        val loginId = "+972123456789"
        val details = SignUpDetails(name = "a", phone = loginId, givenName = "b", middleName = "c", familyName = "d")
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/enchantedlink/signup/sms", route)
            assertEquals(loginId, body["loginId"])
            details.validate(body)
        }
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", maskedPhone = "maskedPhone")
        val response = enchantedLink.signUp(DeliveryMethod.Sms, loginId, details)
        assertEquals("maskedPhone", response.maskedPhone)
        assertNull(response.maskedEmail)
        assertEquals("linkId", response.linkId)
        assertEquals("pendingRef", response.pendingRef)
        assertEquals(1, client.calls)
    }

    @Test
    fun signInOverSms() = runTest {
        val loginId = "+972123456789"
        val uri = "https://mysite.com"
        val options = listOf(SignInOptions.CustomClaims(mapOf("a" to "b")), SignInOptions.Mfa("refreshJwt"))
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/enchantedlink/signin/sms", route)
            assertEquals(loginId, body["loginId"])
            assertEquals(uri, body["redirectUrl"])
            options.validate(body)
        }
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", maskedPhone = "maskedPhone")
        val response = enchantedLink.signIn(DeliveryMethod.Sms, loginId, uri, options)
        assertEquals("maskedPhone", response.maskedPhone)
        assertEquals(1, client.calls)
    }

    @Test
    fun signUpOrInOverSms() = runTest {
        val loginId = "+972123456789"
        val options = listOf(SignInOptions.StepUp("refreshJwt"))
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/enchantedlink/signup-in/sms", route)
            assertEquals(loginId, body["loginId"])
            options.validate(body)
        }
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", maskedPhone = "maskedPhone")
        val response = enchantedLink.signUpOrIn(DeliveryMethod.Sms, loginId, options = options)
        assertEquals("maskedPhone", response.maskedPhone)
        assertEquals(1, client.calls)
    }

    @Test
    fun whatsappIsRejectedWithoutCallingTheServer() = runTest {
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        try {
            enchantedLink.signUpOrIn(DeliveryMethod.Whatsapp, "+972123456789")
            fail("Expected a DescopeException to be thrown")
        } catch (e: DescopeException) {
            assertEquals(DescopeException.invalidArguments.code, e.code)
        }
        assertEquals(0, client.calls)
    }

    @Test
    fun missingMaskedEmailFailsToDecode() = runTest {
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef")
        try {
            enchantedLink.signUpOrIn(DeliveryMethod.Email, "test@test.com")
            fail("Expected a DescopeException to be thrown")
        } catch (e: DescopeException) {
            assertEquals("masked email not received", e.message)
        }
        assertEquals(1, client.calls)
    }

    @Test
    fun missingMaskedPhoneFailsToDecode() = runTest {
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef")
        try {
            enchantedLink.signUpOrIn(DeliveryMethod.Sms, "+972123456789")
            fail("Expected a DescopeException to be thrown")
        } catch (e: DescopeException) {
            assertEquals("masked phone not received", e.message)
        }
        assertEquals(1, client.calls)
    }

    @Test
    fun updatePhone() = runTest {
        val loginId = "test@test.com"
        val phone = "+972123456789"
        val options = UpdateOptions(addToLoginIds = true, onMergeUseExisting = false)
        val client = MockClient()
        val enchantedLink = EnchantedLink(client)
        client.assert = { route: String, body: Map<String, Any?>, headers: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/enchantedlink/update/phone/sms", route)
            assertEquals(loginId, body["loginId"])
            assertEquals(phone, body["phone"])
            assertTrue(headers["Authorization"]!!.contains("refreshJwt"))
            options.validate(body)
        }
        client.response = EnchantedLinkServerResponse("linkId", "pendingRef", maskedPhone = "maskedPhone")
        val response = enchantedLink.updatePhone(phone, loginId, refreshJwt = "refreshJwt", options = options)
        assertEquals("maskedPhone", response.maskedPhone)
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