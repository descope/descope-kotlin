package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.internal.http.TotpServerResponse
import com.descope.sdk.DescopeTotp
import com.descope.types.AuthenticationResponse
import com.descope.types.Result
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.descope.types.TotpResponse

internal class Totp(private val client: DescopeClient) : DescopeTotp {

    override suspend fun signUp(loginId: String, details: SignUpDetails?): TotpResponse =
        client.totpSignUp(loginId, details).convert()

    override fun signUp(loginId: String, details: SignUpDetails?, callback: (Result<TotpResponse>) -> Unit) = wrapCoroutine(callback) {
        signUp(loginId, details)
    }

    override suspend fun update(loginId: String, refreshJwt: String): TotpResponse =
        client.totpUpdate(loginId, refreshJwt).convert()

    override suspend fun update(loginId: String, refreshJwt: String, callback: (Result<TotpResponse>) -> Unit) = wrapCoroutine(callback) {
        update(loginId, refreshJwt)
    }

    override suspend fun verify(loginId: String, code: String, options: List<SignInOptions>?): AuthenticationResponse =
        client.totpVerify(loginId, code, options).convert()

    override fun verify(loginId: String, code: String, options: List<SignInOptions>?, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        verify(loginId, code, options)
    }

}

private fun TotpServerResponse.convert() = TotpResponse(
    provisioningUrl = provisioningUrl,
    image = image,
    key = key,
)
