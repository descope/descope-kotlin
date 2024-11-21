package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopeSso
import com.descope.types.AuthenticationResponse
import com.descope.types.SignInOptions

internal class Sso(private val client: DescopeClient): DescopeSso {

    override suspend fun start(emailOrTenantId: String, redirectUrl: String?, options: List<SignInOptions>?): String =
        client.ssoStart(emailOrTenantId, redirectUrl, options).url

    override suspend fun exchange(code: String): AuthenticationResponse =
        client.ssoExchange(code).convert()

}
