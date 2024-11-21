package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.internal.http.JwtServerResponse
import com.descope.internal.http.UserResponse
import com.descope.sdk.DescopeConfig
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.descope.types.UpdateOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.net.HttpCookie

@Suppress("UNCHECKED_CAST")
internal class MockClient : DescopeClient(DescopeConfig("p1")) {

    var calls = 0
    var assert: ((route: String, body: Map<String, Any?>, headers: Map<String, String>, params: Map<String, String?>) -> Unit)? = null
    var response: Any? = null
    var error: Exception? = null

    override suspend fun <T> post(route: String, decoder: (String, List<HttpCookie>) -> T, body: Map<String, Any?>, headers: Map<String, String>, params: Map<String, String?>): T {
        calls += 1
        assert?.invoke(route, body, headers, params)
        error?.run { throw this }
        response?.run { return this as T }
        throw Exception("Test did not configure response")
    }

    override suspend fun <T> get(route: String, decoder: (String, List<HttpCookie>) -> T, headers: Map<String, String>, params: Map<String, String?>): T {
        calls += 1
        assert?.invoke(route, emptyMap(), headers, params)
        error?.run { throw this }
        response?.run { return this as T }
        throw Exception("Test did not configure response")
    }
}

internal fun SignUpDetails.validate(body: Map<String, Any?>) {
    val user = body["user"] as Map<*, *>
    name?.run { assertEquals(this, user["name"]) }
    givenName?.run { assertEquals(this, user["givenName"]) }
    middleName?.run { assertEquals(this, user["middleName"]) }
    familyName?.run { assertEquals(this, user["familyName"]) }
    email?.run { assertEquals(this, user["email"]) }
    phone?.run { assertEquals(this, user["phone"]) }
}

internal fun List<SignInOptions>.validate(body: Map<String, Any?>) {
    val loginOptions = if (body.containsKey("loginOptions")) body["loginOptions"] as Map<*, *> else body
    forEach {
        when (it) {
            is SignInOptions.CustomClaims -> {
                val claims = loginOptions["customClaims"] as Map<*, *>
                assertEquals(it.claims, claims)
            }

            is SignInOptions.Mfa -> {
                assertTrue(loginOptions["mfa"] as Boolean)
            }

            is SignInOptions.StepUp -> {
                assertTrue(loginOptions["stepup"] as Boolean)
            }
            
            is SignInOptions.RevokeOtherSessions -> {
                assertTrue(loginOptions["revokeOtherSessions"] as Boolean)
            }
        }
    }
}

internal fun UpdateOptions.validate(body: Map<String, Any?>) {
    if (addToLoginIds) assertTrue(body["addToLoginIDs"] as Boolean)
    if (onMergeUseExisting) assertTrue(body["onMergeUseExisting"] as Boolean)
}

internal val jwt =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJodHRwczovL2Rlc2NvcGUuY29tL2JsYS9QMTIzIiwiZXhwIjoxNjAzMTc2NjE0LCJwZXJtaXNzaW9ucyI6WyJkIiwiZSJdLCJyb2xlcyI6WyJ1c2VyIl0sInRlbmFudHMiOnsidGVuYW50Ijp7InBlcm1pc3Npb25zIjpbImEiLCJiIiwiYyJdLCJyb2xlcyI6WyJhZG1pbiJdfX19.MCSE6ZTlD0oVS0FhXe5LwBpbUVG8H5RmwU7sk_L7Bbo"

internal val mockJwtResponse = JwtServerResponse(
    sessionJwt = jwt,
    refreshJwt = jwt,
    firstSeen = false,
    user = UserResponse(
        userId = "userId",
        loginIds = listOf("loginId"),
        createdTime = System.currentTimeMillis(),
        name = "name",
        email = "email",
        picture = null,
        verifiedEmail = true,
        phone = "phone",
        verifiedPhone = true,
        customAttributes = emptyMap(),
        givenName = "givenName",
        middleName = "middleName",
        familyName = "familyName",
    )
)
