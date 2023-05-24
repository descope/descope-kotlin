package com.descope.types

import com.descope.session.DescopeToken

/**
 * Returned from user authentication calls.
 *
 * @property sessionToken the user's session token is used to perform authorized backend requests.
 * @property refreshToken the refresh token is used to refresh expired session tokens.
 * @property isFirstAuthentication whether this the user's first authentication.
 * @property user information about the user.
 */
data class AuthenticationResponse(
    val sessionToken: DescopeToken,
    val refreshToken: DescopeToken,
    val isFirstAuthentication: Boolean,
    val user: DescopeUser,
)

/**
 * Returned from the refreshSession call.
 * The refresh token might get updated as well with new information on the user that
 * might have changed.
 *
 * @property sessionToken refreshed session token
 * @property refreshToken optionally a refresh token
 */
data class RefreshResponse(
    val sessionToken: DescopeToken,
    val refreshToken: DescopeToken?,
)

/**
 * Returned from calls that start an enchanted link flow.
 *
 * The [linkId] value needs to be displayed to the user so they know which
 * link should be clicked on in the enchanted link email. The [maskedEmail]
 * field can also be shown to inform the user to which address the email
 * was sent. The [pendingRef] field is used to poll the server for the
 * enchanted link flow result.
 *
 * @property linkId which link the user should click on
 * @property pendingRef poll for session using this reference
 * @property maskedEmail a masked version of the email address the link was sent to
 */
data class EnchantedLinkResponse(
    val linkId: String,
    val pendingRef: String,
    val maskedEmail: String,
)

/**
 * Returned from TOTP calls that create a new seed.
 *
 * The [provisioningUrl] field wraps the key (seed) in a URL that can be
 * opened by authenticator apps. The [image] field encodes the key (seed)
 * in a QR code image.
 *
 * @property provisioningUrl a URL wrapped key
 * @property image QR code image to scan via authenticator app
 * @property key the (key) seed itself
 */
data class TotpResponse (
    val provisioningUrl: String,
    val image: ByteArray,
    val key: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TotpResponse

        if (provisioningUrl != other.provisioningUrl) return false
        if (!image.contentEquals(other.image)) return false
        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        var result = provisioningUrl.hashCode()
        result = 31 * result + image.contentHashCode()
        result = 31 * result + key.hashCode()
        return result
    }
}

/**
 * Represents the rules for valid passwords.
 *
 * The policy is configured in the password settings in the Descope console, and
 * these values can be used to implement client-side validation of new user passwords
 * for a better user experience.
 * In any case, all password rules are enforced by Descope on the server side as well.
 *
 * @property minLength minimum password length
 * @property lowercase password must contain at least one lowercase character
 * @property uppercase password must contain at least one uppercase character
 * @property number password must contain at least one number character
 * @property nonAlphanumeric password must contain at least one non alphanumeric character
 */
class PasswordPolicy (
    val minLength: Int,
    val lowercase: Boolean,
    val uppercase: Boolean,
    val number: Boolean,
    val nonAlphanumeric: Boolean,
)
