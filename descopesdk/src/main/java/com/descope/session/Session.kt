package com.descope.session

import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeUser
import com.descope.types.RefreshResponse

/**
 * The `DescopeSession` class represents a successful sign in operation.
 *
 * After a user finishes a sign in flow successfully you should create
 * a `DescopeSession` object from the [AuthenticationResponse] value returned
 * by all the authentication APIs.
 *
 *     val authResponse = Descope.otp.verify(DeliveryMethod.Email, "andy@example.com", "123456")
 *     val session = DescopeSession(authResponse)
 *
 * The session can then be used to authenticate outgoing requests to your backend
 * with a bearer token authorization header.
 *
 *     val connection = url.openConnection() as HttpsURLConnection
 *     connection.setAuthorization(Descope.sessionManager)
 *
 * If your backend uses a different authorization mechanism you can of course
 * use the session JWT directly instead of the extension function:
 *
 *     connection.setRequestProperty("X-Auth-Token", session.sessionJwt)
 *
 * As shown above the session can be used directly but in most circumstances
 * it's recommended to let a [DescopeSessionManager] object manage it instead,
 * and the code examples above are only slightly different. See the documentation
 * for [DescopeSessionManager] for more details.
 *
 * @constructor `DescopeSession` can be constructed either by using [DescopeToken]s,
 * or by providing an [AuthenticationResponse], or using the JWT strings.
 *
 * @param sessionToken the short lived session token received inside an [AuthenticationResponse].
 * @param refreshToken the long lived refresh token received inside an [AuthenticationResponse].
 * @param user the authenticated user received from an [AuthenticationResponse] or a `me` request.
 */
@Suppress("EqualsOrHashCode")
class DescopeSession(sessionToken: DescopeToken, refreshToken: DescopeToken, user: DescopeUser) {
    /**
     * The wrapper for the short lived JWT that can be sent with every server
     * request that requires authentication.
     */
    var sessionToken: DescopeToken
        internal set

    /**
     * The wrapper for the longer lived JWT that is used to create
     * new session JWTs until it expires.
     */
    var refreshToken: DescopeToken
        internal set

    /** The user to whom the [DescopeSession] belongs to. */
    var user: DescopeUser
        internal set

    init {
        this.sessionToken = sessionToken
        this.refreshToken = refreshToken
        this.user = user
    }

    /**
     * Creates a new [DescopeSession] object from an [AuthenticationResponse].
     *
     * Use this initializer to create a [DescopeSession] after the user completes
     * a sign in or sign up flow in the application.
     */
    constructor(response: AuthenticationResponse) : this(response.sessionToken, response.refreshToken, response.user)

    /**
     * Creates a new [DescopeSession] object from two JWT strings.
     *
     * This constructor can be used to manually recreate a user's [DescopeSession] after
     * the application is relaunched if not using a `DescopeSessionManager` for this.
     */
    constructor(sessionJwt: String, refreshJwt: String, user: DescopeUser) : this(Token(sessionJwt), Token(refreshJwt), user)

    // Enable correct comparison between sessions
    override fun equals(other: Any?): Boolean {
        val session = other as? DescopeSession ?: return false
        return sessionJwt == session.sessionJwt &&
                refreshJwt == session.refreshJwt &&
                user == session.user
    }

}

// Convenience accessors for getting values from the underlying JWTs.

/** The short lived JWT that is sent with every request that requires authentication. */
val DescopeSession.sessionJwt: String
    get() = sessionToken.jwt

/** The longer lived JWT that is used to create new session JWTs until it expires. */
val DescopeSession.refreshJwt: String
    get() = refreshToken.jwt

/**
 * A map with all the custom claims in the underlying JWT. It includes
 * any claims whose values aren't already exposed by other accessors or
 * authorization functions.
 */
val DescopeSession.claims: Map<String, Any>
    get() = refreshToken.claims

/**
 * Returns the list of permissions granted for the user. Leave tenant as `null`
 * if the user isn't associated with any tenant.
 *
 * @param tenant optional tenant ID to get tenant permissions, if the user belongs to that tenant.
 * @return a list of permissions for the user.
 */
fun DescopeSession.permissions(tenant: String? = null): List<String> = refreshToken.permissions(tenant)

/**
 * Returns the list of roles for the user. Leave tenant as `null`
 * if the user isn't associated with any tenant.
 *
 * @param tenant optional tenant ID to get tenant roles, if the user belongs to that tenant.
 * @return a list of roles for the user.
 */
fun DescopeSession.roles(tenant: String? = null): List<String> = refreshToken.roles(tenant)

// Updating the session manually when not using a `DescopeSessionManager`.

/**
 * Updates the underlying JWTs with those from a `RefreshResponse`.
 *
 *     if (session.sessionToken.isExpired) {
 *         val refreshResponse = Descope.auth.refreshSession(session.refreshJwt)
 *         session.updateTokens(refreshResponse)
 *     }
 *
 * Important: It's recommended to use a `DescopeSessionManager` to manage sessions,
 * in which case you should call `updateTokens` on the manager itself, or
 * just call `refreshSessionIfNeeded` to do everything for you.
 *
 * @param refreshResponse the response to manually update from.
 */
fun DescopeSession.updateTokens(refreshResponse: RefreshResponse) {
    sessionToken = refreshResponse.sessionToken
    refreshToken = refreshResponse.refreshToken ?: refreshToken
}

/**
 * Updates the session user's details with those from another `DescopeUser` value.
 *
 *     val userResponse = Descope.auth.me(session.refreshJwt)
 *     session.updateUser(userResponse)
 *
 * Important: It's recommended to use a `DescopeSessionManager` to manage sessions,
 * in which case you should call `updateUser` on the manager itself instead
 * to ensure that the updated user details are saved.
 *
 * @param descopeUser the user to manually update from.
 */
fun DescopeSession.updateUser(descopeUser: DescopeUser) {
    user = descopeUser
}
