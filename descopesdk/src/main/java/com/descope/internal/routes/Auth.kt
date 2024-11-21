package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.internal.http.TenantsResponse
import com.descope.sdk.DescopeAuth
import com.descope.types.DescopeTenant
import com.descope.types.DescopeUser
import com.descope.types.RevokeType
import com.descope.types.RefreshResponse

internal class Auth(private val client: DescopeClient) : DescopeAuth {

    override suspend fun me(refreshJwt: String): DescopeUser =
        client.me(refreshJwt).convert()

    override suspend fun tenants(dct: Boolean, tenantIds: List<String>, refreshJwt: String): List<DescopeTenant> =
        client.tenants(dct, tenantIds, refreshJwt).convert()

    override suspend fun refreshSession(refreshJwt: String): RefreshResponse =
        client.refresh(refreshJwt).toRefreshResponse()

    override suspend fun revokeSessions(revokeType: RevokeType, refreshJwt: String) {
        client.logout(refreshJwt, revokeType)
    }

    // Deprecated 

    @Deprecated(message = "Use revokeSessions instead", replaceWith = ReplaceWith("revokeSessions(RevokeType.CurrentSession, refreshJwt)"))
    override suspend fun logout(refreshJwt: String) =
        revokeSessions(RevokeType.CurrentSession, refreshJwt)

}

internal fun TenantsResponse.convert(): List<DescopeTenant> {
    return tenants.map { 
        DescopeTenant(
            tenantId = it.tenantId,
            name = it.name,
            customAttributes = it.customAttributes,
        )
    }
}
