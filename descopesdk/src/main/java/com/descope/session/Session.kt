package com.descope.session

import android.text.format.DateFormat
import androidx.annotation.CheckResult
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeUser
import com.descope.types.RefreshResponse
import java.util.Objects

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
 */
class DescopeSession {
    /**
     * The wrapper for the short lived JWT that can be sent with every server
     * request that requires authentication.
     */
    var sessionToken: DescopeToken
        private set

    /**
     * The wrapper for the longer lived JWT that is used to create
     * new session JWTs until it expires.
     */
    var refreshToken: DescopeToken
        private set

    /** The user to whom the [DescopeSession] belongs to. */
    var user: DescopeUser
        private set

    /**
     * Creates a new [DescopeSession] object.
     *
     * @param sessionToken the short lived session token from an [AuthenticationResponse].
     * @param refreshToken the long lived refresh token from an [AuthenticationResponse].
     * @param user the authenticated user from an [AuthenticationResponse] or a `me` request.
     */
    constructor(sessionToken: DescopeToken, refreshToken: DescopeToken, user: DescopeUser) {
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
    
    //
    // Convenience accessors for getting values from the underlying JWTs.
    //

    /** The short lived JWT that is sent with every request that requires authentication. */
    val sessionJwt: String
        get() = sessionToken.jwt

    /** The longer lived JWT that is used to create new session JWTs until it expires. */
    val refreshJwt: String
        get() = refreshToken.jwt

    /**
     * A map with all the custom claims in the underlying JWT. It includes
     * any claims whose values aren't already exposed by other accessors or
     * authorization functions.
     */
    val claims: Map<String, Any>
        get() = refreshToken.claims

    /**
     * Returns the list of permissions granted for the user. Leave tenant as `null`
     * if the user isn't associated with any tenant.
     *
     * @param tenant optional tenant ID to get tenant permissions, if the user belongs to that tenant.
     * @return a list of permissions for the user.
     */
    fun permissions(tenant: String? = null): List<String> = refreshToken.permissions(tenant)

    /**
     * Returns the list of roles for the user. Leave tenant as `null`
     * if the user isn't associated with any tenant.
     *
     * @param tenant optional tenant ID to get tenant roles, if the user belongs to that tenant.
     * @return a list of roles for the user.
     */
    fun roles(tenant: String? = null): List<String> = refreshToken.roles(tenant)

    //
    // Updating the session manually when not using a `DescopeSessionManager`.
    //

    /**
     * Returns a copy of the session object with updated JWTs from a [RefreshResponse].
     *
     *     if (session.sessionToken.isExpired) {
     *         val refreshResponse = Descope.auth.refreshSession(session.refreshJwt)
     *         session = session.withUpdatedTokens(refreshResponse)
     *     }
     *
     * Important: It's recommended to use a [DescopeSessionManager] to manage sessions,
     * in which case you should call `updateTokens` on the manager itself, or
     * just call `refreshSessionIfNeeded` to do everything for you.
     *
     * @param refreshResponse the response to manually update from.
     * @return a new session object with updated tokens.
     */
    @CheckResult
    fun withUpdatedTokens(refreshResponse: RefreshResponse): DescopeSession {
        return DescopeSession(refreshResponse.sessionToken, refreshResponse.refreshToken ?: refreshToken, user)
    }

    /**
     * Returns a copy of the session object with updated user details.
     *
     *     val userResponse = Descope.auth.me(session.refreshJwt)
     *     session = session.withUpdatedUser(userResponse)
     *
     * Important: It's recommended to use a [DescopeSessionManager] to manage sessions,
     * in which case you should call `updateUser` on the manager itself instead
     * to ensure that the updated user details are saved.
     *
     * @param user the user to manually update from.
     * @return a new session object with updated user details.
     */
    @CheckResult
    fun withUpdatedUser(user: DescopeUser): DescopeSession {
        return DescopeSession(sessionToken, refreshToken, user)
    }
    
    // Deprecated

    /**
     * Use the [withUpdatedTokens] method that returns a new [DescopeSession] object to ensure there
     * are no data races and that sessions can be passed safely across thread boundaries, or use
     * the [DescopeSessionManager] to manage sessions for you.
     * 
     *     // change this:
     *     session.updateTokens(refreshResponse)
     *     // to this:
     *     val newSession = session.withUpdatedTokens(refreshResponse)
     */
    @Deprecated(message = "Use withUpdatedTokens instead")
    fun updateTokens(refreshResponse: RefreshResponse) {
        sessionToken = refreshResponse.sessionToken
        refreshToken = refreshResponse.refreshToken ?: refreshToken
    }

    /**
     * Use the [withUpdatedUser] method that returns a new [DescopeSession] object to ensure there
     * are no data races and that sessions can be passed safely across thread boundaries, or use
     * the [DescopeSessionManager] to manage sessions for you.
     *
     *     // change this:
     *     session.updateUser(user)
     *     // to this:
     *     val newSession = session.withUpdatedUser(user)
     */
    @Deprecated(message = "Use withUpdatedUser instead")
    fun updateUser(descopeUser: DescopeUser) {
        user = descopeUser
    }

    // Utilities
    
    override fun equals(other: Any?): Boolean {
        val session = other as? DescopeSession ?: return false
        return sessionJwt == session.sessionJwt &&
            refreshJwt == session.refreshJwt &&
            user == session.user
    }

    override fun hashCode(): Int {
        return Objects.hash(sessionJwt, refreshJwt, user)
    }
    
    /**
     * Returns a safe string representation of the session with the `userId`
     * and refresh token expiry time (i.e., no private or secret data).
     */
    override fun toString(): String {
        val expires = if (refreshToken.isExpired) "expired" else "expires"
        val date = DateFormat.format("yyyy-MM-dd HH:mm:ss", refreshToken.expiresAt)
        return "DescopeSession(userId=${user.userId}, $expires=$date)"
    }
}
