package com.descope.internal.routes

import com.descope.types.DescopeException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WebAuthTest {
    private class Harness {
        var onRedirect: ((String?) -> Boolean)? = null
        var onCancel: (() -> Unit)? = null
        var opened = 0
        var closed = 0
        var closeError: DescopeException? = null

        suspend fun run(openError: DescopeException? = null): String = awaitWebAuthentication(
            register = { handler -> onRedirect = handler },
            open = { cancel ->
                opened += 1
                openError?.let { throw it }
                onCancel = cancel
            },
            close = {
                closed += 1
                closeError?.let { throw it }
            },
        )
    }

    @Test
    fun success() = runTest {
        val harness = Harness()
        val result = async(start = CoroutineStart.UNDISPATCHED) { harness.run() }
        assertEquals(1, harness.opened)
        assertTrue(harness.onRedirect!!("code123"))
        assertEquals("code123", result.await())
        assertEquals(1, harness.closed)
        assertFalse(harness.onRedirect!!("other"))
    }

    @Test
    fun closeFailed() = runTest {
        val harness = Harness()
        harness.closeError = DescopeException.customTabFailed
        val result = async(start = CoroutineStart.UNDISPATCHED) { harness.run() }
        assertTrue(harness.onRedirect!!("code123"))
        assertEquals("code123", result.await())
    }

    @Test
    fun cancelled() = runTest {
        val harness = Harness()
        val result = async(start = CoroutineStart.UNDISPATCHED) { runCatching { harness.run() } }
        harness.onCancel!!()
        assertEquals(DescopeException.customTabCancelled, result.await().exceptionOrNull())
        assertFalse(harness.onRedirect!!("code123"))
    }

    @Test
    fun missingCode() = runTest {
        val harness = Harness()
        val result = async(start = CoroutineStart.UNDISPATCHED) { runCatching { harness.run() } }
        assertTrue(harness.onRedirect!!(null))
        assertEquals(DescopeException.customTabFailed, result.await().exceptionOrNull())
        assertEquals(1, harness.closed)
    }

    @Test
    fun openFailed() = runTest {
        val harness = Harness()
        try {
            harness.run(openError = DescopeException.customTabFailed)
            fail("Expected open error")
        } catch (e: DescopeException) {
            assertEquals(DescopeException.customTabFailed, e)
        }
    }

    @Test
    fun coroutineCancelled() = runTest {
        val harness = Harness()
        val result = async(start = CoroutineStart.UNDISPATCHED) { harness.run() }
        result.cancel()
        result.join()
        assertTrue(result.isCancelled)
        assertEquals(1, harness.closed)
        assertFalse(harness.onRedirect!!("code123"))
    }
}
