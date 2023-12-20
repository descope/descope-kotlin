package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopeOAuth
import com.descope.types.AuthenticationResponse
import com.descope.types.OAuthProvider
import com.descope.types.Result
import com.descope.types.SignInOptions

internal class OAuth(private val client: DescopeClient): DescopeOAuth {

    override suspend fun start(provider: OAuthProvider, redirectUrl: String?, options: List<SignInOptions>?): String =
        client.oauthStart(provider, redirectUrl, options).url

    override fun start(provider: OAuthProvider, redirectUrl: String?, options: List<SignInOptions>?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        start(provider, redirectUrl, options)
    }

    override suspend fun exchange(code: String): AuthenticationResponse =
        client.oauthExchange(code).convert()

    override fun exchange(code: String, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        exchange(code)
    }

}
