package com.descope.session

import com.descope.internal.routes.convert
import com.descope.internal.routes.mockJwtResponse
import com.descope.sdk.DescopeAuth
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
import com.descope.types.DescopeTenant
import com.descope.types.DescopeUser
import com.descope.types.RefreshResponse
import com.descope.types.RevokeType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class ManagerTest {

    private val user = DescopeSession(mockJwtResponse.convert()).user
    private val session = DescopeSession(makeJwt(expiresIn = 30), makeJwt(expiresIn = 3600), user)
    private val otherSession = DescopeSession(makeJwt(expiresIn = 20), makeJwt(expiresIn = 3600), user)
    private val refreshed = RefreshResponse(Token(makeJwt(expiresIn = 600)), null)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refresh_notNeeded() = runTest {
        var calls = 0
        val fresh = DescopeSession(makeJwt(expiresIn = 600), makeJwt(expiresIn = 3600), user)
        val manager = makeManager(MockAuth { calls++; refreshed }, MockStorage(fresh))

        manager.refreshSessionIfNeeded()

        assertEquals(0, calls)
    }

    @Test
    fun refresh_singleFlight() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val storage = MockStorage(session)
        val manager = makeManager(MockAuth { calls++; gate.await(); refreshed }, storage)
        val listener = CountingListener()
        manager.addListener(listener)

        val callers = List(10) { async { manager.refreshSessionIfNeeded() } }
        runCurrent()
        gate.complete(Unit)
        callers.awaitAll()

        assertEquals(1, calls)
        assertEquals(1, storage.saves)
        assertEquals(1, listener.tokenUpdates)
        assertEquals(refreshed.sessionToken.jwt, manager.session?.sessionJwt)
    }

    @Test
    fun refresh_sharedFailure() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val manager = makeManager(MockAuth { calls++; gate.await(); throw DescopeException.networkError })

        val callers = List(10) { async { runCatching { manager.refreshSessionIfNeeded() } } }
        runCurrent()
        gate.complete(Unit)
        val results = callers.awaitAll()

        assertEquals(1, calls)
        assertTrue(results.all { it.exceptionOrNull() == DescopeException.networkError })
    }

    @Test
    fun refresh_callerCanceled() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val manager = makeManager(MockAuth { calls++; gate.await(); refreshed })

        val first = async { manager.refreshSessionIfNeeded() }
        runCurrent()
        val second = async { manager.refreshSessionIfNeeded() }
        runCurrent()
        first.cancel()
        gate.complete(Unit)
        second.await()

        assertEquals(1, calls)
        assertEquals(refreshed.sessionToken.jwt, manager.session?.sessionJwt)
    }

    @Test
    fun refresh_allCallersCanceled() = runTest {
        val gate = CompletableDeferred<Unit>()
        val storage = MockStorage(session)
        val manager = makeManager(MockAuth { gate.await(); refreshed }, storage)

        val caller = async { manager.refreshSessionIfNeeded() }
        runCurrent()
        caller.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, storage.saves)
        assertEquals(refreshed.sessionToken.jwt, manager.session?.sessionJwt)
    }

    @Test
    fun refresh_sessionReplacedDuringSuccess() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val manager = makeManager(MockAuth { calls++; if (calls == 1) gate.await(); refreshed })

        val first = async { manager.refreshSessionIfNeeded() }
        runCurrent()
        manager.manageSession(otherSession)
        val second = async { manager.refreshSessionIfNeeded() }
        runCurrent()
        gate.complete(Unit)
        first.await()
        second.await()

        assertEquals(2, calls)
        assertEquals(refreshed.sessionToken.jwt, manager.session?.sessionJwt)
    }

    @Test
    fun refresh_sessionReplacedDuringFailure() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val manager = makeManager(MockAuth { calls++; if (calls == 1) { gate.await(); throw DescopeException.networkError }; refreshed })

        val first = async { runCatching { manager.refreshSessionIfNeeded() } }
        runCurrent()
        manager.manageSession(otherSession)
        val second = async { runCatching { manager.refreshSessionIfNeeded() } }
        runCurrent()
        gate.complete(Unit)

        assertTrue(first.await().isSuccess)
        assertTrue(second.await().isSuccess)
        assertEquals(2, calls)
    }

    @Test
    fun refresh_keepsUserUpdatedDuringRefresh() = runTest {
        val gate = CompletableDeferred<Unit>()
        val manager = makeManager(MockAuth { gate.await(); refreshed })
        val updatedUser = user.copy(name = "Updated")

        val caller = async { manager.refreshSessionIfNeeded() }
        runCurrent()
        manager.updateUser(updatedUser)
        gate.complete(Unit)
        caller.await()

        assertEquals(refreshed.sessionToken.jwt, manager.session?.sessionJwt)
        assertEquals(updatedUser, manager.session?.user)
    }

    private fun makeManager(auth: DescopeAuth, storage: MockStorage = MockStorage(session)): DescopeSessionManager {
        return DescopeSessionManager(storage, auth, null, null).apply { periodicCheckFrequency = 0 }
    }
}

private fun makeJwt(expiresIn: Long): String {
    val now = System.currentTimeMillis() / 1000
    val header = """{"alg":"HS256","typ":"JWT"}"""
    val payload = """{"sub":"user","iss":"https://descope.com/bla/P123","iat":$now,"exp":${now + expiresIn}}"""
    val encoder = Base64.getEncoder().withoutPadding()
    return listOf(header, payload, "signature").joinToString(".") { encoder.encodeToString(it.toByteArray()) }
}

private class MockAuth(private val refresh: suspend () -> RefreshResponse) : DescopeAuth {
    override suspend fun refreshSession(refreshJwt: String): RefreshResponse = refresh()
    override suspend fun me(refreshJwt: String): DescopeUser = throw NotImplementedError()
    override suspend fun tenants(dct: Boolean, tenantIds: List<String>, refreshJwt: String): List<DescopeTenant> = throw NotImplementedError()
    override suspend fun migrateSession(externalToken: String): AuthenticationResponse = throw NotImplementedError()
    override suspend fun revokeSessions(revokeType: RevokeType, refreshJwt: String) = throw NotImplementedError()
    @Deprecated("Use revokeSessions instead")
    override suspend fun logout(refreshJwt: String) = throw NotImplementedError()
}

private class MockStorage(private val session: DescopeSession) : DescopeSessionStorage {
    var saves = 0
    override fun saveSession(session: DescopeSession) { saves++ }
    override fun loadSession(): DescopeSession = session
    override fun removeSession() {}
}

private class CountingListener : DescopeSessionManager.Listener {
    var tokenUpdates = 0
    override fun onUpdateTokens(session: DescopeSession) { tokenUpdates++ }
    override fun onUpdateUser(session: DescopeSession) {}
}
