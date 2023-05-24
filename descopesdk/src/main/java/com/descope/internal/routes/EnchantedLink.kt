package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.internal.http.EnchantedLinkServerResponse
import com.descope.sdk.DescopeEnchantedLink
import com.descope.types.AuthenticationResponse
import com.descope.types.EnchantedLinkResponse
import com.descope.types.Result
import com.descope.types.SignUpDetails
import kotlinx.coroutines.delay

private const val defaultPollDuration: Long = 2 /* mins */ * 60 /* secs */ * 1000 /* ms */

internal class EnchantedLink(private val client: DescopeClient) : DescopeEnchantedLink {

    override suspend fun signUp(loginId: String, details: SignUpDetails?, uri: String?): EnchantedLinkResponse =
        client.enchantedLinkSignUp(loginId, details, uri).convert()

    override fun signUp(loginId: String, details: SignUpDetails?, uri: String?, callback: (Result<EnchantedLinkResponse>) -> Unit) = wrapCoroutine(callback) {
        signUp(loginId, details, uri)
    }

    override suspend fun signIn(loginId: String, uri: String?): EnchantedLinkResponse =
        client.enchantedLinkSignIn(loginId, uri).convert()

    override fun signIn(loginId: String, uri: String?, callback: (Result<EnchantedLinkResponse>) -> Unit) = wrapCoroutine(callback) {
        signIn(loginId, uri)
    }

    override suspend fun signUpOrIn(loginId: String, uri: String?): EnchantedLinkResponse =
        client.enchantedLinkSignUpOrIn(loginId, uri).convert()

    override fun signUpOrIn(loginId: String, uri: String?, callback: (Result<EnchantedLinkResponse>) -> Unit) = wrapCoroutine(callback) {
        signUpOrIn(loginId, uri)
    }

    override suspend fun updateEmail(email: String, loginId: String, uri: String?, refreshJwt: String): EnchantedLinkResponse =
        client.enchantedLinkUpdateEmail(email, loginId, uri, refreshJwt).convert()

    override fun updateEmail(email: String, loginId: String, uri: String?, refreshJwt: String, callback: (Result<EnchantedLinkResponse>) -> Unit) = wrapCoroutine(callback) {
        updateEmail(email, loginId, uri, refreshJwt)
    }

    override suspend fun checkForSession(pendingRef: String): AuthenticationResponse =
        client.enchantedLinkCheckForSession(pendingRef).convert()

    override fun checkForSession(pendingRef: String, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        checkForSession(pendingRef)
    }

    override suspend fun pollForSession(pendingRef: String, timeoutMilliseconds: Long?): AuthenticationResponse {
        val pollingEndsAt = System.currentTimeMillis() + (timeoutMilliseconds ?: defaultPollDuration)
        // use repeat to ensure we always check at least once
        while (true) {
            // check for the session once, any errors not specifically handled
            // below are intentionally let through to the calling code
            try {
                return checkForSession(pendingRef)
            } catch (e: Exception) {
                // sleep for a second before checking again
                delay(1000L)
                // if the timer's expired then we throw as specific
                // client side error that can be handled appropriately
                // by the calling code
                if (pollingEndsAt - System.currentTimeMillis() <= 0L) throw Exception("Enchanted link polling expired")  // TODO: Make the enchantedLinkExpired exception
            }
        }
    }

    override fun pollForSession(pendingRef: String, timeoutMilliseconds: Long?, callback: (Result<AuthenticationResponse>) -> Unit) = wrapCoroutine(callback) {
        pollForSession(pendingRef, timeoutMilliseconds)
    }
}

private fun EnchantedLinkServerResponse.convert() = EnchantedLinkResponse(
    linkId = linkId,
    pendingRef = pendingRef,
    maskedEmail = maskedEmail,
)
