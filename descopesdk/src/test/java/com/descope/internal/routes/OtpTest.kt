package com.descope.internal.routes

import com.descope.internal.http.MaskedAddressServerResponse
import com.descope.types.DeliveryMethod
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.descope.types.UpdateOptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OtpTest {
    @Test
    fun signUp() = runTest {
        val loginId = "test@test.com"
        val details = SignUpDetails(name = "a", email = loginId, givenName = "b", middleName = "c", familyName = "d")
        val client = MockClient()
        val otp = Otp(client)
        val maskedAddress = MaskedAddressServerResponse("maskedEmail", "maskedPhone")
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/otp/signup/email", route)
            assertEquals(loginId, body["loginId"])
            details.validate(body)
        }
        client.response = maskedAddress
        val response = otp.signUp(DeliveryMethod.Email, loginId, details)
        assertEquals(maskedAddress.maskedEmail, response)
        assertEquals(1, client.calls)
    }

    @Test
    fun signIn() = runTest {
        val loginId = "+972123456789"
        val options = listOf(SignInOptions.CustomClaims(mapOf("a" to "b")), SignInOptions.Mfa("refreshJwt"))
        val client = MockClient()
        val otp = Otp(client)
        val maskedAddress = MaskedAddressServerResponse("maskedEmail", "maskedPhone")
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/otp/signin/sms", route)
            assertEquals(loginId, body["loginId"])
            options.validate(body)
        }
        client.response = maskedAddress
        val response = otp.signIn(DeliveryMethod.Sms, loginId, options)
        assertEquals(maskedAddress.maskedPhone, response)
        assertEquals(1, client.calls)
    }

    @Test
    fun signUpOrIn() = runTest {
        val loginId = "+972123456789"
        val options = listOf(SignInOptions.StepUp("refreshJwt"))
        val client = MockClient()
        val otp = Otp(client)
        val maskedAddress = MaskedAddressServerResponse("maskedEmail", "maskedPhone")
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/otp/signup-in/sms", route)
            assertEquals(loginId, body["loginId"])
            options.validate(body)
        }
        client.response = maskedAddress
        val response = otp.signUpOrIn(DeliveryMethod.Sms, loginId, options)
        assertEquals(maskedAddress.maskedPhone, response)
        assertEquals(1, client.calls)
    }

    @Test
    fun updatePhone() = runTest {
        val phone = "+972123456789"
        val options = UpdateOptions(addToLoginIds = true, onMergeUseExisting = false)
        val client = MockClient()
        val otp = Otp(client)
        val maskedAddress = MaskedAddressServerResponse("maskedEmail", "maskedPhone")
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/otp/update/phone/sms", route)
            assertEquals("loginId", body["loginId"])
            assertEquals(phone, body["phone"])
            options.validate(body)
        }
        client.response = maskedAddress
        val response = otp.updatePhone(phone, DeliveryMethod.Sms, "loginId", "refreshJwt", options)
        assertEquals(maskedAddress.maskedPhone, response)
        assertEquals(1, client.calls)
    }

    @Test
    fun updateEmail() = runTest {
        val loginId = "test@test.com"
        val options = UpdateOptions(addToLoginIds = true, onMergeUseExisting = false)
        val client = MockClient()
        val otp = Otp(client)
        val maskedAddress = MaskedAddressServerResponse("maskedEmail", "maskedPhone")
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/otp/update/email", route)
            assertEquals(loginId, body["loginId"])
            options.validate(body)
        }
        client.response = maskedAddress
        val response = otp.updateEmail("test2@test.com", loginId, "refreshJwt", options)
        assertEquals(maskedAddress.maskedEmail, response)
        assertEquals(1, client.calls)
    }

    @Test
    fun verify() = runTest {
        val loginId = "test@test.com"
        val code = "code"
        val client = MockClient()
        val otp = Otp(client)
        client.assert = { route: String, body: Map<String, Any?>, _: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/otp/verify/email", route)
            assertEquals(loginId, body["loginId"])
            assertEquals(code, body["code"])
        }
        client.response = mockJwtResponse
        val response = otp.verify(DeliveryMethod.Email, loginId, code)
        assertEquals(jwt, response.sessionToken.jwt)
        assertEquals(1, client.calls)
    }
}