@file:Suppress("unused")

package com.descope.extensions

import com.descope.session.DescopeSession
import com.descope.session.DescopeSessionManager
import com.descope.session.DescopeToken
import java.net.URLConnection

/**
 * Ensures that the active session in a [DescopeSessionManager] is valid and
 * then sets its session JWT as the Bearer Token value of the Authorization
 * header field in the [URLConnection].
 *
 * @param sessionManager The [DescopeSessionManager] to user for authorization.
 */
suspend fun URLConnection.setAuthorization(sessionManager: DescopeSessionManager) {
    sessionManager.refreshSessionIfNeeded()
    sessionManager.session?.run {
        setAuthorizationFromSession(this)
    }
}

/**
 * Sets the session JWT from a [DescopeSession] as the Bearer Token value of
 * the Authorization header field in the [URLConnection].
 *
 * @param session the [DescopeSession] to use for authorization.
 */
fun URLConnection.setAuthorizationFromSession(session: DescopeSession) {
    setAuthorizationFromToken(session.sessionToken)
}

/**
 * Sets the JWT from a [DescopeToken] as the Bearer Token value of
 * the Authorization header field in the [URLConnection].
 *
 * @param token the token to use for authorization.
 */
fun URLConnection.setAuthorizationFromToken(token: DescopeToken) {
    setRequestProperty("Authorization", "Bearer ${token.jwt}")
}
