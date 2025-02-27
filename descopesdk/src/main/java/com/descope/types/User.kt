package com.descope.types

import android.net.Uri
import com.descope.session.DescopeSession

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
)
