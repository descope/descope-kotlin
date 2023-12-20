package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopeOtp
import com.descope.types.AuthenticationResponse
import com.descope.types.DeliveryMethod
import com.descope.types.Result
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails

internal class Otp(private val client: DescopeClient) : DescopeOtp {

    override suspend fun signUp(method: DeliveryMethod, loginId: String, details: SignUpDetails?): String =
        client.otpSignUp(method, loginId, details).convert(method)

    override fun signUp(method: DeliveryMethod, loginId: String, details: SignUpDetails?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        signUp(method, loginId, details)
    }

    override suspend fun signIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>?): String =
        client.otpSignIn(method, loginId, options).convert(method)

    override fun signIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        signIn(method, loginId, options)
    }

    override suspend fun signUpOrIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>?): String =
        client.otpSignUpIn(method, loginId, options).convert(method)

    override fun signUpOrIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        signUpOrIn(method, loginId, options)
    }

    override suspend fun verify(method: DeliveryMethod, loginId: String, code: String): AuthenticationResponse =
        client.otpVerify(method, loginId, code).convert()

    override fun verify(method: DeliveryMethod, loginId: String, code: String, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        verify(method, loginId, code)
    }

    override suspend fun updateEmail(email: String, loginId: String, refreshJwt: String): String =
        client.otpUpdateEmail(email, loginId, refreshJwt).convert(DeliveryMethod.Email)

    override fun updateEmail(email: String, loginId: String, refreshJwt: String, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        updateEmail(email, loginId, refreshJwt)
    }

    override suspend fun updatePhone(phone: String, method: DeliveryMethod, loginId: String, refreshJwt: String): String =
        client.otpUpdatePhone(phone, method, loginId, refreshJwt).convert(method)

    override fun updatePhone(phone: String, method: DeliveryMethod, loginId: String, refreshJwt: String, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        updatePhone(phone, method, loginId, refreshJwt)
    }
}

