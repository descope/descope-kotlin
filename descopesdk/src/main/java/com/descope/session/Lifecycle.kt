package com.descope.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.descope.sdk.DescopeAuth
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Timer
import kotlin.concurrent.timerTask

const val SECOND = 1000L

/**
 * This interface can be used to customize how a [DescopeSessionManager] object
 * manages its [DescopeSession] while the application is running.
 */
interface DescopeSessionLifecycle {
    /** Set by the session manager whenever the current active session changes. */
    var session: DescopeSession?

    /** Called the session manager to conditionally refresh the active session. */
    suspend fun refreshSessionIfNeeded()
}

/**
 * The default implementation of the `DescopeSessionLifecycle` protocol.
 *
 * The `SessionLifecycle` class periodically checks if the session needs to be
 * refreshed (every 30 seconds by default). The [refreshSessionIfNeeded] function
 * will refresh the session if it's about to expire (within 60 seconds by default)
 * or if it's already expired.
 *
 * @property auth used to refresh the session when needed
 */
class SessionLifecycle(private val auth: DescopeAuth) : DescopeSessionLifecycle {

    var stalenessAllowedInterval: Long = 60L /* seconds */ * SECOND
    var stalenessCheckFrequency: Long = 30L /* seconds */ * SECOND

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                // application in foreground
                if (session != null) startTimer(runImmediately = true)
            }

            override fun onStop(owner: LifecycleOwner) {
                // application in background
                stopTimer()
            }
        })
    }

    override var session: DescopeSession? = null
        set(value) {
            if (field == value) return
            field = value
            if (value == null) {
                stopTimer()
            } else {
                startTimer()
            }
        }

    override suspend fun refreshSessionIfNeeded() {
        session?.run {
            if (shouldRefresh(this)) {
                val response = auth.refreshSession(refreshJwt) // TODO check for refresh failure to not try again and again after expiry
                updateTokens(response)
            }
        }
    }

    // Internal

    private fun shouldRefresh(session: DescopeSession): Boolean =
        session.sessionToken.expiresAt?.run { this - System.currentTimeMillis() <= stalenessAllowedInterval } ?: false

    // Timer

    private var timer: Timer? = null

    @OptIn(DelicateCoroutinesApi::class)
    private fun startTimer(runImmediately: Boolean = false) {
        val weakRef = WeakReference(this)
        val delay = if (runImmediately) 0L else stalenessCheckFrequency
        timer?.run { cancel(); purge() }
        timer = Timer().apply {
            scheduleAtFixedRate(timerTask {
                val ref = weakRef.get()
                if (ref == null) {
                    stopTimer()
                    return@timerTask
                }
                GlobalScope.launch(Dispatchers.Main) {
                    try {
                        ref.refreshSessionIfNeeded()
                    } catch (ignored: Exception) {
                    }
                }
            }, delay, stalenessCheckFrequency)
        }
    }

    private fun stopTimer() {
        timer?.run { cancel(); purge() }
        timer = null
    }

}
