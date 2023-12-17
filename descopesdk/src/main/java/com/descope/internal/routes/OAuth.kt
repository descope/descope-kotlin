package com.descope.internal.routes

import android.content.Context
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.descope.internal.http.DescopeClient
import com.descope.internal.others.with
import com.descope.sdk.DescopeOAuth
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
import com.descope.types.OAuthProvider
import com.descope.types.Result
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

internal class OAuth(override val client: DescopeClient):  Route, DescopeOAuth {

    override suspend fun start(provider: OAuthProvider, redirectUrl: String?): String =
        client.oauthWebStart(provider, redirectUrl).url

    override fun start(provider: OAuthProvider, redirectUrl: String?, callback: (Result<String>) -> Unit) = wrapCoroutine(callback) {
        start(provider, redirectUrl)
    }

    override suspend fun exchange(code: String): AuthenticationResponse =
        client.oauthWebExchange(code).convert()

    override fun exchange(code: String, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        exchange(code)
    }
    
    override suspend fun native(context: Context, provider: OAuthProvider): AuthenticationResponse {
        val startResponse = client.oauthNativeStart(provider)
        
        if (!startResponse.implicit) {
            throw DescopeException.oauthNativeFailed.with(message = "OAuth provider cannot require implicit authentication")
        }
        
        val authorization = performAuthorization(context, startResponse.clientId, startResponse.nonce)
        val identityToken = parseCredential(authorization.credential)
        return client.oauthNativeFinish(provider, startResponse.stateId, identityToken).convert()
    }

    override fun native(context: Context, provider: OAuthProvider, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        native(context, provider)
    }
    
    private suspend fun performAuthorization(context: Context, clientId: String, nonce: String?): GetCredentialResponse {
        val option = GetGoogleIdOption.Builder().run {
            setFilterByAuthorizedAccounts(false)
            setServerClientId(clientId)
            setNonce(nonce)
            build()
        }

        val request = GetCredentialRequest.Builder().run {
            addCredentialOption(option)
            build()
        }
        
        return try {
            val credentialManager = CredentialManager.create(context)
            credentialManager.getCredential(context, request)
        } catch (e: GetCredentialException) {
            throw DescopeException.oauthNativeFailed.with(cause = e)
        }
    }
    
    private fun parseCredential(credential: Credential): String {
        if (credential !is CustomCredential) {
            throw DescopeException.oauthNativeFailed.with(message = "Unexpected OAuth credential subclass")
        }
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            throw DescopeException.oauthNativeFailed.with(message = "Unexpected OAuth credential type")
        }

        val result = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: GoogleIdTokenParsingException) {
            throw DescopeException.oauthNativeFailed.with(message = "Invalid OAuth credential")
        }

        return result.idToken
    }
}
