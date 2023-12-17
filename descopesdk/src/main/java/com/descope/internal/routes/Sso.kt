package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopeSso
import com.descope.types.AuthenticationResponse
import com.descope.types.Result

internal class Sso(private val client: DescopeClient): DescopeSso {

    override suspend fun start(emailOrTenantId: String, redirectUrl: String?): String =
        client.ssoStart(emailOrTenantId, redirectUrl).url

    override fun start(emailOrTenantId: String, redirectUrl: String?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        start(emailOrTenantId, redirectUrl)
    }

    override suspend fun exchange(code: String): AuthenticationResponse =
        client.ssoExchange(code).convert()

    override fun exchange(code: String, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        exchange(code)
    }

}
