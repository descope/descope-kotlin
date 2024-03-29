package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopeMagicLink
import com.descope.types.AuthenticationResponse
import com.descope.types.DeliveryMethod
import com.descope.types.Result
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.descope.types.UpdateOptions

internal class MagicLink(private val client: DescopeClient): DescopeMagicLink {

    override suspend fun signUp(method: DeliveryMethod, loginId: String, details: SignUpDetails?, uri: String?): String =
        client.magicLinkSignUp(method, loginId, details, uri).convert(method)

    override fun signUp(method: DeliveryMethod, loginId: String, details: SignUpDetails?, uri: String?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        signUp(method, loginId, details, uri)
    }

    override suspend fun signIn(method: DeliveryMethod, loginId: String, uri: String?, options: List<SignInOptions>?): String =
        client.magicLinkSignIn(method, loginId, uri, options).convert(method)

    override fun signIn(method: DeliveryMethod, loginId: String, uri: String?, options: List<SignInOptions>?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        signIn(method, loginId, uri, options)
    }

    override suspend fun signUpOrIn(method: DeliveryMethod, loginId: String, uri: String?, options: List<SignInOptions>?): String =
        client.magicLinkSignUpOrIn(method, loginId, uri, options).convert(method)

    override fun signUpOrIn(method: DeliveryMethod, loginId: String, uri: String?, options: List<SignInOptions>?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        signUpOrIn(method, loginId, uri, options)
    }

    override suspend fun updateEmail(email: String, loginId: String, uri: String, refreshJwt: String, options: UpdateOptions?): String =
        client.magicLinkUpdateEmail(email, loginId, uri, refreshJwt, options).convert(DeliveryMethod.Email)

    override fun updateEmail(email: String, loginId: String, uri: String, refreshJwt: String, options: UpdateOptions?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        updateEmail(email, loginId, uri, refreshJwt, options)
    }

    override suspend fun updatePhone(phone: String, method: DeliveryMethod, loginId: String, uri: String?, refreshJwt: String, options: UpdateOptions?): String =
        client.magicLinkUpdatePhone(phone, method, loginId, uri, refreshJwt, options).convert(method)

    override fun updatePhone(phone: String, method: DeliveryMethod, loginId: String, uri: String?, refreshJwt: String, options: UpdateOptions?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        updatePhone(phone, method, loginId, uri, refreshJwt, options)
    }

    override suspend fun verify(token: String): AuthenticationResponse = client.magicLinkVerify(token).convert()

    override fun verify(token: String, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        verify(token)
    }

}
