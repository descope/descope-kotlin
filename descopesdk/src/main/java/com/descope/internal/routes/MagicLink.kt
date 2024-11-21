package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopeMagicLink
import com.descope.types.AuthenticationResponse
import com.descope.types.DeliveryMethod
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.descope.types.UpdateOptions

internal class MagicLink(private val client: DescopeClient): DescopeMagicLink {

    override suspend fun signUp(method: DeliveryMethod, loginId: String, details: SignUpDetails?, uri: String?): String =
        client.magicLinkSignUp(method, loginId, details, uri).convert(method)

    override suspend fun signIn(method: DeliveryMethod, loginId: String, uri: String?, options: List<SignInOptions>?): String =
        client.magicLinkSignIn(method, loginId, uri, options).convert(method)

    override suspend fun signUpOrIn(method: DeliveryMethod, loginId: String, uri: String?, options: List<SignInOptions>?): String =
        client.magicLinkSignUpOrIn(method, loginId, uri, options).convert(method)

    override suspend fun updateEmail(email: String, loginId: String, uri: String?, refreshJwt: String, options: UpdateOptions?): String =
        client.magicLinkUpdateEmail(email, loginId, uri, refreshJwt, options).convert(DeliveryMethod.Email)

    override suspend fun updatePhone(phone: String, method: DeliveryMethod, loginId: String, uri: String?, refreshJwt: String, options: UpdateOptions?): String =
        client.magicLinkUpdatePhone(phone, method, loginId, uri, refreshJwt, options).convert(method)

    override suspend fun verify(token: String): AuthenticationResponse = client.magicLinkVerify(token).convert()

}
