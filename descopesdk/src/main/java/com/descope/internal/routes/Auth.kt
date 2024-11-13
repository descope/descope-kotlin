package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopeAuth
import com.descope.types.DescopeUser
import com.descope.types.LogoutRevoke
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

    override suspend fun logout(refreshJwt: String, revoke: LogoutRevoke) =
        client.logout(refreshJwt, revoke)

    override suspend fun logout(refreshJwt: String, revoke: LogoutRevoke, callback: (Result<Unit>) -> Unit) = wrapCoroutine(callback) {
        logout(refreshJwt, revoke)
    }

}
