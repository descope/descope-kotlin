package com.descope.internal.http

import org.junit.Assert.*
import org.junit.Test

class DescopeClientTest {

    @Test
    fun baseUrlForProjectId_variousInputs() {
        assertEquals("https://api.descope.com", baseUrlForProjectId(""))
        assertEquals("https://api.descope.com", baseUrlForProjectId("Puse"))
        assertEquals("https://api.descope.com", baseUrlForProjectId("Puse1ar"))
        assertEquals("https://api.use1.descope.com", baseUrlForProjectId("Puse12aAc4T2V93bddihGEx2Ryhc8e5Z"))
        assertEquals("https://api.use1.descope.com", baseUrlForProjectId("Puse12aAc4T2V93bddihGEx2Ryhc8e5Zfoobar"))
    }
    
}
