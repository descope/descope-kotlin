package com.descope.session

import com.descope.types.DescopeUser
import org.junit.Assert.assertEquals
import org.junit.Test

class SerializationTest {

    @Test
    fun user_serialization() {
        val originalUser = DescopeUser(
            userId = "testUserId",
            loginIds = listOf("test@descope.com"),
            createdAt = 1234567890,
            name = "Test User",
            picture = null,
            email = "test@descope.com",
            isVerifiedEmail = true,
            phone = "+15555555555",
            isVerifiedPhone = true,
            customAttributes = mapOf("tenantId" to "tenant-1"),
            givenName = "Test",
            middleName = "Middle",
            familyName = "User",
            status = DescopeUser.Status.Enabled,
            authentication = DescopeUser.Authentication(
                passkey = true,
                password = true,
                totp = true,
                oauth = setOf("google"),
                sso = false,
                scim = false
            ),
            authorization = DescopeUser.Authorization(
                roles = setOf("Admin", "Editor"),
                ssoAppIds = emptySet()
            ),
            isUpdateRequired = false
        )

        val serializedUser = originalUser.serialize()
        val loadedUser = deserializeDescopeUser(serializedUser)

        assertEquals(originalUser, loadedUser)
    }
}
