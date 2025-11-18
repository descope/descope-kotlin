package com.descope.types

import com.descope.session.DescopeSession

// Enums

/**
 * Which sessions to revoke when calling `DescopeAuth.revokeSessions()`
 */
enum class RevokeType {
    /** Revokes the provided refresh JWT. */
    CurrentSession,
    /** 
     * Revokes the provided refresh JWT and all other active sessions for the user.
     *
     * - Important: This causes all sessions for the user to be removed, and the provided
     *   refresh JWT will not be usable after the logout call completes. 
     */
    AllSessions,
}

/** The delivery method for an OTP or Magic Link message. */
enum class DeliveryMethod {
    Email,
    Sms,
    Whatsapp,
}

/** The provider to use in an OAuth flow. */
data class OAuthProvider(val name: String) {
    companion object {
        val Facebook = OAuthProvider("facebook")
        val Github = OAuthProvider("github")
        val Google = OAuthProvider("google")
        val Microsoft = OAuthProvider("microsoft")
        val Gitlab = OAuthProvider("gitlab")
        val Apple = OAuthProvider("apple")
        val Slack = OAuthProvider("slack")
        val Discord = OAuthProvider("discord")
    }
}

// Classes

/**
 * Used to provide additional details about a user in sign up calls.
 *
 * @property name the user's name
 * @property email the user's email
 * @property phone the user's phone
 * @property givenName the user's given name
 * @property middleName the user's middle name
 * @property familyName the user's family name
 */
data class SignUpDetails(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val givenName: String? = null,
    val middleName: String? = null,
    val familyName: String? = null,
)

/**
 * Used to require additional behaviors when authenticating a user.
 */
sealed class SignInOptions {
    /**
     * Adds additional custom claims to the user's JWT during authentication.
     *
     * For example, the following code starts an OTP sign in and requests a custom claim
     * with the authenticated user's full name:
     *
     *     val claims = mapOf("cc1" to "yes", "cc2" to true)
     *     val options = listOf(SignInOptions.CustomClaims(claims))
     *     Descope.otp.signUp(method = DeliveryMethod.Email, loginId = "andy@example.com", options = options)
     *
     * - Important: Any custom claims added via this method are considered insecure and will
     * be nested under the `nsec` custom claim.
     */
    class CustomClaims(val claims: Map<String, Any>) : SignInOptions()

    /**
     * Used to add layered security to your app by implementing Step-up authentication.
     *
     *     val session = Descope.sessionManager.session ?: return
     *     val options = listOf(SignInOptions.StepUp(session.refreshJwt))
     *     Descope.otp.signUp(method = DeliveryMethod.Email, loginId = "andy@example.com", options = options)
     *
     * After the Step-up authentication completes successfully the returned session JWT will
     * have an `su` claim with a value of `true`.
     *
     * - Note: The `su` claim is not set on the refresh JWT.
     */
    class StepUp(val refreshJwt: String) : SignInOptions()

    /**
     * Used to add layered security to your app by implementing Multi-factor authentication.
     *
     * Assuming the user has already signed in successfully with one authentication method,
     * we can take the `refreshJwt` and pass it as an `mfa` option to another authentication
     * method.
     *
     *     val session = Descope.sessionManager.session ?: return
     *     val options = listOf(SignInOptions.Mfa(session.refreshJwt))
     *     Descope.otp.signUp(method = DeliveryMethod.Email, loginId = "andy@example.com", options = options)
     *
     * After the MFA authentication completes successfully the `amr` claim in both the session
     * and refresh JWTs will be an array with an entry for each authentication method used.
     */
    class Mfa(val refreshJwt: String) : SignInOptions()


    /**
     * Revokes all other active sessions for the user besides the new session being created.
     */
    data object RevokeOtherSessions : SignInOptions()
}

/**
 * Used to configure how users are updated.
 * @param addToLoginIds Whether to allow sign in from a new `loginId` after an update.
 *
 * When a user's email address or phone number are updated and this is set to `true`
 * the new value is added to the user's list of `loginIds`, and the user from that
 * point on will be able to use it to sign in.
 * @param onMergeUseExisting Whether to keep or delete the current user when merging two users after an update.
 *
 * When updating a user's email address or phone number and with [addToLoginIds]
 * set to `true`, if another user in the the system already has the same email address
 * or phone number as the one being added in their list of `loginIds` the two users
 * are merged and one of them is deleted.
 *
 * This scenario can happen when a user uses multiple authentication methods
 * and ends up with multiple accounts. For example, a user might sign in with
 * their email address at first. Then at some point later they reinstall the
 * app and use OAuth to authenticate, and a new user account is created. If
 * the user then updates their account and adds their email address the
 * two accounts need to be merged.
 *
 * Let's define the "updated user" to be the user being updated and whom
 * the `refreshJwt` belongs to, and the "existing user" to be another user in
 * the system with the same `loginId`.
 *
 * By default, the updated user is kept, the existing user's details are merged
 * into the updated user, and the existing user is then deleted.
 *
 * If this option is set to `true` however then the updated user is merged into
 * the existing user, and the updated user is deleted. In this case the [DescopeSession]
 * and its `refreshJwt` that was used to initiate the update operation will no longer
 * be valid, and an [AuthenticationResponse] is returned for the existing user instead.
 */
data class UpdateOptions(
    val addToLoginIds: Boolean,
    val onMergeUseExisting: Boolean,
)
