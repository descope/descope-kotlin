package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.internal.http.PasswordPolicyServerResponse
import com.descope.sdk.DescopePassword
import com.descope.types.AuthenticationResponse
import com.descope.types.PasswordPolicy
import com.descope.types.Result
import com.descope.types.SignUpDetails

internal class Password(private val client: DescopeClient) : DescopePassword {

    override suspend fun signUp(loginId: String, password: String, details: SignUpDetails?): AuthenticationResponse =
        client.passwordSignUp(loginId, password, details).convert()

    override fun signUp(loginId: String, password: String, details: SignUpDetails?, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        signUp(loginId, password, details)
    }

    override suspend fun signIn(loginId: String, password: String): AuthenticationResponse =
        client.passwordSignIn(loginId, password).convert()

    override fun signIn(loginId: String, password: String, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        signIn(loginId, password)
    }

    override suspend fun update(loginId: String, newPassword: String, refreshJwt: String) =
        client.passwordUpdate(loginId, newPassword, refreshJwt)

    override fun update(loginId: String, newPassword: String, refreshJwt: String, callback: (Result<Unit>) -> Unit) = wrapCoroutine(callback) {
        update(loginId, newPassword, refreshJwt)
    }

    override suspend fun replace(loginId: String, oldPassword: String, newPassword: String) =
        client.passwordReplace(loginId, oldPassword, newPassword)

    override fun replace(loginId: String, oldPassword: String, newPassword: String, callback: (Result<Unit>) -> Unit) = wrapCoroutine(callback) {
        replace(loginId, oldPassword, newPassword)
    }

    override suspend fun sendReset(loginId: String, redirectUrl: String?) =
        client.passwordSendReset(loginId, redirectUrl)

    override fun sendReset(loginId: String, redirectUrl: String?, callback: (Result<Unit>) -> Unit) = wrapCoroutine(callback) {
        sendReset(loginId, redirectUrl)
    }

    override suspend fun getPolicy(): PasswordPolicy =
        client.passwordGetPolicy().convert()

    override fun getPolicy(callback: (Result<PasswordPolicy>) -> Unit) = wrapCoroutine(callback) {
        getPolicy()
    }
}

private fun PasswordPolicyServerResponse.convert() = PasswordPolicy(
    minLength = minLength,
    lowercase = lowercase,
    uppercase = uppercase,
    number = number,
    nonAlphanumeric = nonAlphanumeric,
)
