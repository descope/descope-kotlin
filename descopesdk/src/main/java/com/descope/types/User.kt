package com.descope.types

import android.net.Uri
import com.descope.session.DescopeSession
import java.util.Locale

/**
 * The `DescopeUser` data class represents an existing user in Descope.
 *
 * After a user is signed in with any authentication method the [DescopeSession] object
 * keeps a `DescopeUser` value in its [DescopeSession.user] property so the user's details are always
 * available.
 *
 * In the example below we finalize an OTP authentication for the user by verifying the
 * code. The authentication response has a `DescopeUser` property which can be used
 * directly or later on when it's kept in the `DescopeSession`.
 *
 *     val authResponse = Descope.otp.verify(method = DeliveryMethod.Email, loginId = "andy@example.com", code = "123456")
 *     print("Finished OTP login for user: ${authResponse.user}")
 *
 *     Descope.sessionManager.session = DescopeSession(authResponse)
 *     print("Created session for user ${descopeSession.user.userId}")
 *
 * The details for a signed in user can be updated manually by calling the `auth.me` API with
 * the `refreshJwt` from the active `DescopeSession`. If the operation is successful the call
 * returns a new `DescopeUser` value.
 *
 *     val session = Descope.sessionManager.session ?: return
 *     val user = Descope.auth.me(refreshJwt = session.refreshJwt)
 *     Descope.sessionManager.updateUser(user)
 *
 * In the code above we check that there's an active [DescopeSession] in the shared
 * session manager. If so we ask the Descope server for the latest user details and
 * then update the [DescopeSession] with them.
 *
 * @property userId the unique identifier for the user in Descope.
 * This value never changes after the user is created, and it always matches
 * the `Subject` (`sub`) claim value in the user's JWT after signing in.
 * @property loginIds the identifiers the user can sign in with.
 * This is a list of one or more email addresses, phone numbers, usernames, or any
 * custom identifiers the user can authenticate with.
 * @property createdAt the time at which the user was created in Descope.
 * @property name the user's full name.
 * @property picture the user's profile picture.
 * @property email the user's email address.
 * If this is non-null and the `isVerifiedEmail` flag is `true` then this email address
 * can be used to do email based authentications such as magic link, OTP, etc.
 * @property isVerifiedEmail whether the email address has been verified to be a valid authentication method
 * for this user. If `email` is `null` then this is always `false`.
 * @property phone the user's phone number.
 * If this is non-null and the `isVerifiedPhone` flag is `true` then this phone number
 * can be used to do phone based authentications such as OTP.
 * @property isVerifiedPhone whether the phone number has been verified to be a valid authentication method
 * for this user. If `phone` is `null` then this is always `false`.
 * @property customAttributes a mapping of any custom attributes associated with this user.
 * User custom attributes are managed via the Descope console.
 * @property givenName optional user's given name.
 * @property middleName optional user's middle name.
 * @property familyName optional user's family name.
 * @property status the user's status, one of 'enabled', 'disabled' or 'invited'.
 * @property authentication details about the authentication methods the user has set up.
 * @property authorization details about the authorization settings for this user.
 * @property isUpdateRequired This flag indicates that the [DescopeUser] of the signed in user was saved by an older
 * version of the Descope SDK, and some fields that were added to the [DescopeUser] class
 * might show empty values (`false`, `null`, etc) as placeholders, until the user is loaded
 * from the server again.
 * The scenario described above can happen when deploying an app update with a new version of
 * the Descope SDK, in which case it's recommended to call `Descope.auth.me()` to update the
 * user data, after which this flag will become `false`.
 */
data class DescopeUser(
    val userId: String,
    val loginIds: List<String>,
    val createdAt: Long,
    val name: String?,
    val picture: Uri?,
    val email: String?,
    val isVerifiedEmail: Boolean,
    val phone: String?,
    val isVerifiedPhone: Boolean,
    val customAttributes: Map<String, Any>,
    val givenName: String?,
    val middleName: String?,
    val familyName: String?,
    val status: Status,
    val authentication: Authentication,
    val authorization: Authorization,
    val isUpdateRequired: Boolean,
) {

    enum class Status {
        Invited,
        Enabled,
        Disabled;

        fun serialize(): String = name.lowercase()
        
        companion object {
            fun deserialize(value: String): Status =
                valueOf(value.lowercase().replaceFirstChar { it.titlecase(Locale.ROOT) })
        }
    }
    
    /**
     * Details about the authentication methods the user has set up.
     * 
     * @property passkey whether the user has passkey (WebAuthn) authentication set up.
     * @property password whether the user has a password set up.
     * @property totp whether the user has TOTP (authenticator app) set up.
     * @property oauth the OAuth providers the user has used to sign in. Can be empty.
     * @property sso whether the user has SSO set up.
     * @property scim whether SCIM provisioning is enabled for this user.
     */
    data class Authentication(
        val passkey: Boolean,
        val password: Boolean,
        val totp: Boolean,
        val oauth: Set<String>,
        val sso: Boolean,
        val scim: Boolean,
    ) {
        internal companion object {
            val placeholder = Authentication(false, false, false, emptySet(), false, false)
        }
    }
    
    /**
     * Details about the authorization settings for this user.
     * 
     * @property roles the names of the roles assigned to this user. Can be empty.
     * @property ssoAppIds the IDs of the SSO Apps assigned to this user. Can be empty.
     */
    data class Authorization(
        val roles: Set<String>,
        val ssoAppIds: Set<String>,
    ) {
        internal companion object {
            val placeholder = Authorization(emptySet(), emptySet())
        }
    }
    
    companion object {
        /**
         * A placeholder [DescopeUser] value.
         * 
         * This can be useful in some circumstances, such as an app that only keeps the JWT values
         * it gets after the user authenticates but needs to create a [DescopeSession] value.
         * 
         * If your code ends up accessing any of the [DescopeUser] fields in the [DescopeSession]
         * then make sure to call `Descope.auth.me()` to get an actual [DescopeUser] value and
         * update your session by calling [DescopeSession.updateUser].
         * 
         * You can check if a [DescopeSession] has a valid [DescopeSession.user] field by checking
         * if the [isUpdateRequired] property is `false`.
         */
        val placeholder = DescopeUser("", emptyList(), 0, null, null, null, false, null, false, emptyMap(), null, null, null, Status.Enabled, Authentication.placeholder, Authorization.placeholder, true)
    }
}
