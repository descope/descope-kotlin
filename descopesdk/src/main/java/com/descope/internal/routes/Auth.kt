package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopeAuth
import com.descope.types.DescopeUser
import com.descope.types.RevokeType
import com.descope.types.RefreshResponse
import com.descope.types.Result

internal class Auth(private val client: DescopeClient) : DescopeAuth {

    override suspend fun me(refreshJwt: String): DescopeUser =
        client.me(refreshJwt).convert()

    override fun me(refreshJwt: String, callback: (Result<DescopeUser>) -> Unit) = wrapCoroutine(callback) {
        me(refreshJwt)
    }

    override suspend fun refreshSession(refreshJwt: String): RefreshResponse =
        client.refresh(refreshJwt).toRefreshResponse()

    override suspend fun refreshSession(refreshJwt: String, callback: (Result<RefreshResponse>) -> Unit) = wrapCoroutine(callback) {
        refreshSession(refreshJwt)
    }

    override suspend fun revokeSessions(revokeType: RevokeType, refreshJwt: String) {
        client.logout(refreshJwt, revokeType)
    }

    override fun revokeSessions(revoke: RevokeType, refreshJwt: String, callback: (Result<Unit>) -> Unit) = wrapCoroutine(callback) {
        revokeSessions(revoke, refreshJwt)
    }
    
    // Deprecated 

    @Deprecated(message = "Use revokeSessions instead", replaceWith = ReplaceWith("revokeSessions(RevokeType.CurrentSession, refreshJwt)"))
    override suspend fun logout(refreshJwt: String) =
        revokeSessions(RevokeType.CurrentSession, refreshJwt)

    @Deprecated(message = "Use revokeSessions instead", replaceWith = ReplaceWith("revokeSessions(RevokeType.CurrentSession, refreshJwt, callback)"))
    override fun logout(refreshJwt: String, callback: (Result<Unit>) -> Unit) = 
        revokeSessions(RevokeType.CurrentSession, refreshJwt, callback)

}
