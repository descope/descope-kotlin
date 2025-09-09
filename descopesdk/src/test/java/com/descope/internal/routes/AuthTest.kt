package com.descope.internal.routes

import com.descope.internal.http.TenantsResponse
import com.descope.internal.http.UserResponse
import com.descope.types.RevokeType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthTest {
    @Test
    fun me() = runTest {
        val client = MockClient()
        val auth = Auth(client)
        client.assert = { route: String, _: Map<String, Any?>, headers: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/me", route)
            val authorizationHeader = headers["Authorization"]
            assertNotNull(authorizationHeader)
            assertTrue(authorizationHeader!!.contains("refreshJwt"))
        }
        client.response = UserResponse(
            userId = "userId",
            loginIds = listOf("loginId"),
            name = "name",
            picture = null,
            email = null,
            verifiedEmail = false,
            phone = null,
            verifiedPhone = false,
            createdTime = 0L,
            customAttributes = emptyMap(),
            givenName = null,
            middleName = null,
            familyName = null,
            password = false,
            status = "enabled",
            roleNames = emptyList(),
            ssoAppIds = emptyList(),
            oauthProviders = emptyMap(),
        )
        val response = auth.me("refreshJwt")
        assertEquals("name", response.name)
        assertEquals(1, client.calls)
    }

    @Test
    fun tenants() = runTest {
        val client = MockClient()
        val auth = Auth(client)
        client.assert = { route: String, body: Map<String, Any?>, headers: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/me/tenants", route)
            val authorizationHeader = headers["Authorization"]
            assertNotNull(authorizationHeader)
            assertTrue(authorizationHeader!!.contains("refreshJwt"))
            assertEquals(true, body["dct"])
        }
        client.response = TenantsResponse(
            tenants = listOf(
                TenantsResponse.Tenant("id1", "t1", emptyMap()),
                TenantsResponse.Tenant("id2", "t2", mapOf("a" to "b", "c" to "d"))
            ),
        )
        val response = auth.tenants(true, emptyList(), "refreshJwt")
        assertEquals(2, response.size)
        assertEquals("id1", response[0].tenantId)
        assertEquals("t1", response[0].name)
        assertEquals("id2", response[1].tenantId)
        assertEquals("t2", response[1].name)
        assertEquals(2, response[1].customAttributes.size)
        assertEquals(1, client.calls)
    }

    @Test
    fun refreshSession() = runTest {
        val client = MockClient()
        val auth = Auth(client)
        client.assert = { route: String, _: Map<String, Any?>, headers: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/refresh", route)
            val authorizationHeader = headers["Authorization"]
            assertNotNull(authorizationHeader)
            assertTrue(authorizationHeader!!.contains("refreshJwt"))
        }
        client.response = mockJwtResponse
        val response = auth.refreshSession("refreshJwt")
        assertEquals(jwt, response.sessionToken.jwt)
        assertEquals(jwt, response.refreshToken!!.jwt)
        assertEquals(1, client.calls)
    }

    @Test
    fun revokeSession_current() = runTest {
        val client = MockClient()
        val auth = Auth(client)
        client.assert = { route: String, _: Map<String, Any?>, headers: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/logout", route)
            val authorizationHeader = headers["Authorization"]
            assertNotNull(authorizationHeader)
            assertTrue(authorizationHeader!!.contains("refreshJwt"))
        }
        client.response = Unit
        auth.revokeSessions(RevokeType.CurrentSession, "refreshJwt")
        assertEquals(1, client.calls)
    }

    @Test
    fun revokeSession_all() = runTest {
        val client = MockClient()
        val auth = Auth(client)
        client.assert = { route: String, _: Map<String, Any?>, headers: Map<String, String>, _: Map<String, String?> ->
            assertEquals("auth/logoutall", route)
            val authorizationHeader = headers["Authorization"]
            assertNotNull(authorizationHeader)
            assertTrue(authorizationHeader!!.contains("refreshJwt"))
        }
        client.response = Unit
        auth.revokeSessions(RevokeType.AllSessions, "refreshJwt")
        assertEquals(1, client.calls)
    }

}
