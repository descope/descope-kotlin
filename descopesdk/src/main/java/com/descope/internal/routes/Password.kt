package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.internal.http.PasswordPolicyServerResponse
import com.descope.sdk.DescopePassword
import com.descope.types.AuthenticationResponse
import com.descope.types.PasswordPolicy
import com.descope.types.SignUpDetails

internal class Password(private val client: DescopeClient) : DescopePassword {

    override suspend fun signUp(loginId: String, password: String, details: SignUpDetails?): AuthenticationResponse =
        client.passwordSignUp(loginId, password, details).convert()

    override suspend fun signIn(loginId: String, password: String): AuthenticationResponse =
        client.passwordSignIn(loginId, password).convert()

    override suspend fun update(loginId: String, newPassword: String, refreshJwt: String) =
        client.passwordUpdate(loginId, newPassword, refreshJwt)

    override suspend fun replace(loginId: String, oldPassword: String, newPassword: String): AuthenticationResponse =
        client.passwordReplace(loginId, oldPassword, newPassword).convert()

    override suspend fun sendReset(loginId: String, redirectUrl: String?) =
        client.passwordSendReset(loginId, redirectUrl)

    override suspend fun getPolicy(): PasswordPolicy =
        client.passwordGetPolicy().convert()
}

private fun PasswordPolicyServerResponse.convert() = PasswordPolicy(
    minLength = minLength,
    lowercase = lowercase,
    uppercase = uppercase,
    number = number,
    nonAlphanumeric = nonAlphanumeric,
)
