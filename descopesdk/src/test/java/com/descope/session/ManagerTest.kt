package com.descope.session

import com.descope.internal.routes.convert
import com.descope.internal.routes.mockJwtResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class ManagerTest {

    private val session = DescopeSession(mockJwtResponse.convert())

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
}

private class MockLifecycle(override var session: DescopeSession?) : DescopeSessionLifecycle {
    override var onPeriodicRefresh: (() -> Unit)? = null
    override suspend fun refreshSessionIfNeeded(): Boolean = false
}

private class MockStorage(private val session: DescopeSession) : DescopeSessionStorage {
    override fun saveSession(session: DescopeSession) {}
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
