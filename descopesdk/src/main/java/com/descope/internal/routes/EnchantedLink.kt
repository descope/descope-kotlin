package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.internal.http.EnchantedLinkServerResponse
import com.descope.sdk.DescopeEnchantedLink
import com.descope.sdk.DescopeLogger.Level.Error
import com.descope.sdk.DescopeLogger.Level.Info
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
import com.descope.types.EnchantedLinkResponse
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.descope.types.UpdateOptions
import kotlinx.coroutines.delay

private const val DEFAULT_POLL_DURATION: Long = 2 /* mins */ * 60 /* secs */ * 1000 /* ms */

internal class EnchantedLink(override val client: DescopeClient) : Route, DescopeEnchantedLink {

    override suspend fun signUp(loginId: String, details: SignUpDetails?, uri: String?): EnchantedLinkResponse =
        client.enchantedLinkSignUp(loginId, details, uri).convert()

    override suspend fun signIn(loginId: String, uri: String?, options: List<SignInOptions>?): EnchantedLinkResponse =
        client.enchantedLinkSignIn(loginId, uri, options).convert()

    override suspend fun signUpOrIn(loginId: String, uri: String?, options: List<SignInOptions>?): EnchantedLinkResponse =
        client.enchantedLinkSignUpOrIn(loginId, uri, options).convert()

    override suspend fun updateEmail(email: String, loginId: String, uri: String?, refreshJwt: String, options: UpdateOptions?): EnchantedLinkResponse =
        client.enchantedLinkUpdateEmail(email, loginId, uri, refreshJwt, options).convert()

    override suspend fun checkForSession(pendingRef: String): AuthenticationResponse =
        client.enchantedLinkCheckForSession(pendingRef).convert()

    override suspend fun pollForSession(pendingRef: String, timeoutMilliseconds: Long?): AuthenticationResponse {
        val pollingEndsAt = System.currentTimeMillis() + (timeoutMilliseconds ?: DEFAULT_POLL_DURATION)
        log(Info, "Polling for enchanted link", timeoutMilliseconds ?: DEFAULT_POLL_DURATION)
        // use repeat to ensure we always check at least once
        while (true) {
            // check for the session once, any errors not specifically handled
            // below are intentionally let through to the calling code
            try {
                val response = checkForSession(pendingRef)
                log(Info, "Enchanted link authentication succeeded")
                return response
            } catch (e: Exception) {
                // sleep for a second before checking again
                log(Info, "Waiting for enchanted link")
                delay(1000L)
                // if the timer's expired then we throw as specific
                // client side error that can be handled appropriately
                // by the calling code
                if (pollingEndsAt - System.currentTimeMillis() <= 0L) {
                    log(Error, "Timed out while polling for enchanted link")
                    throw DescopeException.enchantedLinkExpired
                }
            }
        }
    }
}

private fun EnchantedLinkServerResponse.convert() = EnchantedLinkResponse(
    linkId = linkId,
    pendingRef = pendingRef,
    maskedEmail = maskedEmail,
)
