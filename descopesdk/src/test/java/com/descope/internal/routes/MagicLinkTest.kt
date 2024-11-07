package com.descope.internal.routes

import com.descope.internal.http.MaskedAddressServerResponse
import com.descope.types.DeliveryMethod
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.descope.types.UpdateOptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MagicLinkTest {
    @Test
    fun signUp() = runTest {
        val loginId = "test@test.com"
        val details = SignUpDetails(name = "a", email = loginId, givenName = "b", middleName = "c", familyName = "d")
        val client = MockClient()
        val magicLink = MagicLink(client)
        val maskedAddress = MaskedAddressServerResponse("maskedEmail", "maskedPhone")
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/magiclink/signup/email", route)
            assertEquals(loginId, body["loginId"])
            details.validate(body)
        }
        client.response = maskedAddress
        val response = magicLink.signUp(DeliveryMethod.Email, loginId, details)
        assertEquals(maskedAddress.maskedEmail, response)
        assertEquals(1, client.calls)
    }

    @Test
    fun signIn() = runTest {
        val loginId = "+972123456789"
        val uri = "https://mysite.com"
        val options = listOf(SignInOptions.CustomClaims(mapOf("a" to "b")), SignInOptions.Mfa("refreshJwt"))
        val client = MockClient()
        val magicLink = MagicLink(client)
        val maskedAddress = MaskedAddressServerResponse("maskedEmail", "maskedPhone")
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, params: Map<String, String?> ->
            assertEquals("auth/magiclink/signin/sms", route)
            assertEquals(loginId, body["loginId"])
            assertEquals(uri, body["uri"])
            options.validate(body)
        }
        client.response = maskedAddress
        val response = magicLink.signIn(DeliveryMethod.Sms, loginId, uri, options)
        assertEquals(maskedAddress.maskedPhone, response)
        assertEquals(1, client.calls)
    }

    @Test
    fun signUpOrIn() = runTest {
        val loginId = "+972123456789"
        val options = listOf(SignInOptions.StepUp("refreshJwt"))
        val client = MockClient()
        val magicLink = MagicLink(client)
        val maskedAddress = MaskedAddressServerResponse("maskedEmail", "maskedPhone")
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/magiclink/signup-in/sms", route)
            assertEquals(loginId, body["loginId"])
            options.validate(body)
        }
        client.response = maskedAddress
        val response = magicLink.signUpOrIn(DeliveryMethod.Sms, loginId, options = options)
        assertEquals(maskedAddress.maskedPhone, response)
        assertEquals(1, client.calls)
    }

    @Test
    fun updatePhone() = runTest {
        val phone = "+972123456789"
        val options = UpdateOptions(addToLoginIds = true, onMergeUseExisting = false)
        val client = MockClient()
        val magicLink = MagicLink(client)
        val maskedAddress = MaskedAddressServerResponse("maskedEmail", "maskedPhone")
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/magiclink/update/phone/sms", route)
            assertEquals("loginId", body["loginId"])
            assertEquals(phone, body["phone"])
            options.validate(body)
        }
        client.response = maskedAddress
        val response = magicLink.updatePhone(phone, DeliveryMethod.Sms, "loginId", refreshJwt = "refreshJwt", options = options)
        assertEquals(maskedAddress.maskedPhone, response)
        assertEquals(1, client.calls)
    }

    @Test
    fun updateEmail() = runTest {
        val loginId = "test@test.com"
        val options = UpdateOptions(addToLoginIds = true, onMergeUseExisting = false)
        val client = MockClient()
        val magicLink = MagicLink(client)
        val maskedAddress = MaskedAddressServerResponse("maskedEmail", "maskedPhone")
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/magiclink/update/email", route)
            assertEquals(loginId, body["loginId"])
            options.validate(body)
        }
        client.response = maskedAddress
        val response = magicLink.updateEmail("test2@test.com", loginId, refreshJwt = "refreshJwt", options = options)
        assertEquals(maskedAddress.maskedEmail, response)
        assertEquals(1, client.calls)
    }

    @Test
    fun verify() = runTest {
        val loginId = "test@test.com"
        val token = "token"
        val client = MockClient()
        val magicLink = MagicLink(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/magiclink/verify", route)
            assertEquals(token, body["token"])
        }
        client.response = mockJwtResponse
        val response = magicLink.verify(token)
        assertEquals(jwt, response.sessionToken.jwt)
        assertEquals(1, client.calls)
    }
}