package com.descope.session

import com.descope.android.findJwtInCookies
import com.descope.internal.http.REFRESH_COOKIE_NAME
import com.descope.internal.http.SESSION_COOKIE_NAME
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.HttpCookie

class TokenTest {

    private val jwtForP123 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJodHRwczovL2Rlc2NvcGUuY29tL2JsYS9QMTIzIiwiZXhwIjoxNjAzMTc2NjE0LCJwZXJtaXNzaW9ucyI6WyJkIiwiZSJdLCJyb2xlcyI6WyJ1c2VyIl0sInRlbmFudHMiOnsidGVuYW50Ijp7InBlcm1pc3Npb25zIjpbImEiLCJiIiwiYyJdLCJyb2xlcyI6WyJhZG1pbiJdfX19.MCSE6ZTlD0oVS0FhXe5LwBpbUVG8H5RmwU7sk_L7Bbo"
    private val laterJwtForP123 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNzI4OTk1ODc1LCJpc3MiOiJodHRwczovL2Rlc2NvcGUuY29tL2JsYS9QMTIzIiwiZXhwIjoxNjAzMTc2NjE0LCJwZXJtaXNzaW9ucyI6WyJkIiwiZSJdLCJyb2xlcyI6WyJ1c2VyIl0sInRlbmFudHMiOnsidGVuYW50Ijp7InBlcm1pc3Npb25zIjpbImEiLCJiIiwiYyJdLCJyb2xlcyI6WyJhZG1pbiJdfX19.XKZku4wncwDMtaWJp_-ZBC5TliB4Gci_UiGJnLcDOqk"
    private val jwtForP456 = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJodHRwczovL2Rlc2NvcGUuY29tL2JsYS9QNDU2IiwiZXhwIjoxNjAzMTc2NjE0LCJwZXJtaXNzaW9ucyI6WyJkIiwiZSJdLCJyb2xlcyI6WyJ1c2VyIl0sInRlbmFudHMiOnsidGVuYW50Ijp7InBlcm1pc3Npb25zIjpbImEiLCJiIiwiYyJdLCJyb2xlcyI6WyJhZG1pbiJdfX19.Xiq0lrwmfvpCF6XIhMbgbqcoRaemjljcJ6j_DXvKibw"

    @Test
    fun jwt_decode() {
        val token = Token(jwtForP123)

        assertEquals(jwtForP123, token.jwt)

        // Basic Fields
        assertEquals("1234567890", token.entityId)
        assertEquals("P123", token.projectId)
        assertEquals(1603176614000, token.expiresAt)

        // Custom Claims
        assertEquals("John Doe", token.claims["name"])
        assertEquals(1, token.claims.size)

        // Authorization
        assertEquals(listOf("d", "e"), token.permissions(tenant = null))
        assertEquals(listOf("user"), token.roles(tenant = null))

        // Tenant Authorization
        assertEquals(listOf("a", "b", "c"), token.permissions(tenant = "tenant"))
        assertEquals(listOf("admin"), token.roles(tenant = "tenant"))
        assertEquals(emptyList<String>(), token.permissions(tenant = "no-such-tenant"))
    }

    @Test
    fun cookies_oneDsr() {
        val cookies = mutableListOf<HttpCookie>()
        for (i in 0 until 10) {
            cookies.add(HttpCookie("name$i", "value$i"))
        }
        cookies.add(HttpCookie(REFRESH_COOKIE_NAME, jwtForP123))
        val refreshJwt = findJwtInCookies(name = REFRESH_COOKIE_NAME, cookies.joinToString(separator = "; "))
        assertEquals(jwtForP123, refreshJwt)
    }

    @Test
    fun cookies_differentProjectDsr() {
        val cookies = mutableListOf<HttpCookie>()
        cookies.add(HttpCookie(REFRESH_COOKIE_NAME, jwtForP456))
        for (i in 0 until 10) {
            cookies.add(HttpCookie("name$i", "value$i"))
        }
        cookies.add(HttpCookie(REFRESH_COOKIE_NAME, jwtForP123))

        val refreshJwt = findJwtInCookies(name = REFRESH_COOKIE_NAME, cookies.joinToString(separator = "; "))
        assertEquals(jwtForP456, refreshJwt)
    }

    @Test
    fun cookies_multipleDs() {
        val cookies = mutableListOf<HttpCookie>()
        cookies.add(HttpCookie(SESSION_COOKIE_NAME, laterJwtForP123))
        for (i in 0 until 10) {
            cookies.add(HttpCookie("name$i", "value$i"))
        }
        cookies.add(HttpCookie(SESSION_COOKIE_NAME, jwtForP123))

        var refreshJwt = findJwtInCookies(name = SESSION_COOKIE_NAME, cookies.joinToString(separator = "; "))
        assertEquals(laterJwtForP123, refreshJwt)

        // try again with a different order
        cookies.clear()
        cookies.add(HttpCookie(SESSION_COOKIE_NAME, jwtForP123))
        for (i in 0 until 10) {
            cookies.add(HttpCookie("name$i", "value$i"))
        }
        cookies.add(HttpCookie(SESSION_COOKIE_NAME, laterJwtForP123))

        refreshJwt = findJwtInCookies(name = SESSION_COOKIE_NAME, cookies.joinToString(separator = "; "))
        assertEquals(laterJwtForP123, refreshJwt)
    }

    @Test
    fun cookies_oneDsr_multipleStrings() {
        val cookies = mutableListOf<HttpCookie>()
        for (i in 0 until 10) {
            cookies.add(HttpCookie("name$i", "value$i"))
        }
        cookies.add(HttpCookie(REFRESH_COOKIE_NAME, jwtForP123))
        val moreCookies = mutableListOf<HttpCookie>()
        for (i in 10 until 20) {
            moreCookies.add(HttpCookie("name$i", "value$i"))
        }
        val refreshJwt = findJwtInCookies(name = REFRESH_COOKIE_NAME, moreCookies.joinToString(separator = "; "), cookies.joinToString(separator = "; "))
        assertEquals(jwtForP123, refreshJwt)
    }

    @Test
    fun cookies_multipleDs_multipleStrings() {
        val cookies = mutableListOf<HttpCookie>()
        cookies.add(HttpCookie(SESSION_COOKIE_NAME, laterJwtForP123))
        for (i in 0 until 10) {
            cookies.add(HttpCookie("name$i", "value$i"))
        }

        val moreCookies = mutableListOf<HttpCookie>()
        moreCookies.add(HttpCookie(SESSION_COOKIE_NAME, laterJwtForP123))
        for (i in 0 until 10) {
            moreCookies.add(HttpCookie("name$i", "value$i"))
        }
        moreCookies.add(HttpCookie(SESSION_COOKIE_NAME, jwtForP123))
        
        

        var refreshJwt = findJwtInCookies(name = SESSION_COOKIE_NAME, cookies.joinToString(separator = "; "), moreCookies.joinToString(separator = "; "))
        assertEquals(laterJwtForP123, refreshJwt)

        // try again with a different order
        refreshJwt = findJwtInCookies(name = SESSION_COOKIE_NAME, moreCookies.joinToString(separator = "; "), cookies.joinToString(separator = "; "))
        assertEquals(laterJwtForP123, refreshJwt)
    }
}
