package com.descope.internal.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.HttpCookie

class ResponsesTest {

    private val sessionJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJodHRwczovL2Rlc2NvcGUuY29tL2JsYS9QMTIzIn0.x"
    private val refreshJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwiaWF0IjoxNTE2MjM5MDIyLCJpc3MiOiJodHRwczovL2Rlc2NvcGUuY29tL2JsYS9QMTIzIn0.y"

    @Test
    fun jwtServerResponse_fromJson_defaultCookieNames() {
        val json = """{"firstSeen":false,"cookieDomain":"example.com","cookiePath":"/"}"""
        val cookies = listOf(
            HttpCookie(SESSION_COOKIE_NAME, sessionJwt),
            HttpCookie(REFRESH_COOKIE_NAME, refreshJwt),
        )
        val response = JwtServerResponse.fromJson(json, cookies)
        assertEquals(sessionJwt, response.sessionJwt)
        assertEquals(refreshJwt, response.refreshJwt)
    }

    @Test
    fun jwtServerResponse_fromJson_customCookieNames() {
        val customSessionName = "my_session"
        val customRefreshName = "my_refresh"
        val json = """{"firstSeen":false,"cookieDomain":"example.com","cookiePath":"/"}"""
        val cookies = listOf(
            HttpCookie(customSessionName, sessionJwt),
            HttpCookie(customRefreshName, refreshJwt),
        )
        val response = JwtServerResponse.fromJson(json, cookies, customSessionName, customRefreshName)
        assertEquals(sessionJwt, response.sessionJwt)
        assertEquals(refreshJwt, response.refreshJwt)
    }

    @Test
    fun jwtServerResponse_fromJson_customCookieNames_ignoresDefaultNames() {
        val customSessionName = "my_session"
        val customRefreshName = "my_refresh"
        val json = """{"firstSeen":false,"cookieDomain":"example.com","cookiePath":"/"}"""
        val cookies = listOf(
            HttpCookie(SESSION_COOKIE_NAME, "wrong-session"),
            HttpCookie(REFRESH_COOKIE_NAME, "wrong-refresh"),
            HttpCookie(customSessionName, sessionJwt),
            HttpCookie(customRefreshName, refreshJwt),
        )
        val response = JwtServerResponse.fromJson(json, cookies, customSessionName, customRefreshName)
        assertEquals(sessionJwt, response.sessionJwt)
        assertEquals(refreshJwt, response.refreshJwt)
    }

    @Test
    fun jwtServerResponse_fromJson_defaultNames_notFoundWithCustomNames() {
        val customSessionName = "my_session"
        val customRefreshName = "my_refresh"
        val json = """{"firstSeen":false,"cookieDomain":"example.com","cookiePath":"/"}"""
        val cookies = listOf(
            HttpCookie(customSessionName, sessionJwt),
            HttpCookie(customRefreshName, refreshJwt),
        )
        val response = JwtServerResponse.fromJson(json, cookies)
        assertNull(response.sessionJwt)
        assertNull(response.refreshJwt)
    }
}
