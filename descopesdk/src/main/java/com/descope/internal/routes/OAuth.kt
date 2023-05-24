package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopeOAuth
import com.descope.types.AuthenticationResponse
import com.descope.types.OAuthProvider
import com.descope.types.Result

internal class OAuth(private val client: DescopeClient): DescopeOAuth {

    override suspend fun start(provider: OAuthProvider, redirectUrl: String?): String =
        client.oauthStart(provider, redirectUrl).url

    override fun start(provider: OAuthProvider, redirectUrl: String?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        start(provider, redirectUrl)
    }

    override suspend fun exchange(code: String): AuthenticationResponse =
        client.oauthExchange(code).convert()

    override fun exchange(code: String, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        exchange(code)
    }

}
