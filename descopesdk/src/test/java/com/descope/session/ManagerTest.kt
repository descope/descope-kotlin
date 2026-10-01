package com.descope.session

import com.descope.internal.routes.convert
import com.descope.internal.routes.mockJwtResponse
import com.descope.types.DescopeException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManagerTest {

    private val session = DescopeSession(mockJwtResponse.convert())
    private val otherSession = DescopeSession(otherJwt, session.refreshJwt, session.user)

    @Test
    fun listeners_failureIsolated() {
        val lifecycle = MockLifecycle(session)
        val manager = DescopeSessionManager(MockStorage(session), lifecycle)
        val listener = CountingListener()
        manager.addListener(FailingListener())
        manager.addListener(listener)

        lifecycle.onPeriodicRefresh?.invoke()
        manager.updateUser(session.user)

        assertEquals(1, listener.tokenUpdates)
        assertEquals(1, listener.userUpdates)
    }

    @Test
    fun refresh_singleFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        var refreshes = 0
        val lifecycle = MockLifecycle(session) {
            if (refreshes > 0) return@MockLifecycle false
            refreshes++
            gate.await()
            true
        }
        val storage = MockStorage(session)
        val manager = DescopeSessionManager(storage, lifecycle)
        val listener = CountingListener()
        manager.addListener(listener)

        val callers = List(10) { async { manager.refreshSessionIfNeeded() } }
        runCurrent()
        gate.complete(Unit)
        callers.awaitAll()

        assertEquals(1, refreshes)
        assertEquals(1, storage.saves)
        assertEquals(1, listener.tokenUpdates)
    }

    @Test
    fun refresh_sharedFailure() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val lifecycle = MockLifecycle(session) { calls++; gate.await(); throw DescopeException.networkError }
        val manager = DescopeSessionManager(MockStorage(session), lifecycle)

        val callers = List(10) { async { runCatching { manager.refreshSessionIfNeeded() } } }
        runCurrent()
        gate.complete(Unit)
        val results = callers.awaitAll()

        assertEquals(1, calls)
        assertTrue(results.all { it.exceptionOrNull() == DescopeException.networkError })
    }

    @Test
    fun refresh_abandonedTakenOver() = runTest {
        var calls = 0
        val lifecycle = MockLifecycle(session) { calls++; if (calls == 1) awaitCancellation(); true }
        val manager = DescopeSessionManager(MockStorage(session), lifecycle)
        val listener = CountingListener()
        manager.addListener(listener)

        val owner = launch { manager.refreshSessionIfNeeded() }
        runCurrent()
        val waiter = async { manager.refreshSessionIfNeeded() }
        runCurrent()
        owner.cancel()
        waiter.await()

        assertEquals(2, calls)
        assertEquals(1, listener.tokenUpdates)
    }

    @Test
    fun refresh_sessionReplacedDuringSuccess() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val lifecycle = MockLifecycle(session) { calls++; if (calls == 1) { gate.await(); false } else true }
        val manager = DescopeSessionManager(MockStorage(session), lifecycle)

        val owner = async { manager.refreshSessionIfNeeded() }
        runCurrent()
        manager.manageSession(otherSession)
        val waiter = async { manager.refreshSessionIfNeeded() }
        runCurrent()
        gate.complete(Unit)
        owner.await()
        waiter.await()

        assertEquals(2, calls)
    }

    @Test
    fun refresh_sessionReplacedDuringFailure() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val lifecycle = MockLifecycle(session) { calls++; if (calls == 1) { gate.await(); throw DescopeException.networkError } else true }
        val manager = DescopeSessionManager(MockStorage(session), lifecycle)

        val owner = async { runCatching { manager.refreshSessionIfNeeded() } }
        runCurrent()
        manager.manageSession(otherSession)
        val waiter = async { runCatching { manager.refreshSessionIfNeeded() } }
        runCurrent()
        gate.complete(Unit)

        assertTrue(owner.await().isFailure)
        assertTrue(waiter.await().isSuccess)
        assertEquals(2, calls)
    }
}

private const val otherJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNzI4OTk1ODc1LCJpc3MiOiJodHRwczovL2Rlc2NvcGUuY29tL2JsYS9QMTIzIiwiZXhwIjoxNjAzMTc2NjE0LCJwZXJtaXNzaW9ucyI6WyJkIiwiZSJdLCJyb2xlcyI6WyJ1c2VyIl0sInRlbmFudHMiOnsidGVuYW50Ijp7InBlcm1pc3Npb25zIjpbImEiLCJiIiwiYyJdLCJyb2xlcyI6WyJhZG1pbiJdfX19.XKZku4wncwDMtaWJp_-ZBC5TliB4Gci_UiGJnLcDOqk"

private class MockLifecycle(
    override var session: DescopeSession?,
    private val refresh: suspend () -> Boolean = { false },
) : DescopeSessionLifecycle {
    override var onPeriodicRefresh: (() -> Unit)? = null
    override suspend fun refreshSessionIfNeeded(): Boolean = refresh()
}

private class MockStorage(private val session: DescopeSession) : DescopeSessionStorage {
    var saves = 0
    override fun saveSession(session: DescopeSession) { saves++ }
    override fun loadSession(): DescopeSession = session
    override fun removeSession() {}
}

private class FailingListener : DescopeSessionManager.Listener {
    override fun onUpdateTokens(session: DescopeSession) = throw IllegalStateException()
    override fun onUpdateUser(session: DescopeSession) = throw IllegalStateException()
}

private class CountingListener : DescopeSessionManager.Listener {
    var tokenUpdates = 0
    var userUpdates = 0
    override fun onUpdateTokens(session: DescopeSession) { tokenUpdates++ }
    override fun onUpdateUser(session: DescopeSession) { userUpdates++ }
}
