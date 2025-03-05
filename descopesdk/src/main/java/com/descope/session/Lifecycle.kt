package com.descope.session

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.descope.sdk.DescopeAuth
import com.descope.sdk.DescopeLogger
import com.descope.sdk.DescopeLogger.Level.Debug
import com.descope.sdk.DescopeLogger.Level.Error
import com.descope.sdk.DescopeLogger.Level.Info
import com.descope.types.DescopeException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.timer

/**
 * This interface can be used to customize how a [DescopeSessionManager] object
 * manages its [DescopeSession] while the application is running.
 */
interface DescopeSessionLifecycle {
    /** Holds the latest session value for the session manager. */
    var session: DescopeSession?

    /** Called by the session manager to conditionally refresh the active session. */
    suspend fun refreshSessionIfNeeded(): Boolean
}

/**
 * The default implementation of the `DescopeSessionLifecycle` interface.
 *
 * The `SessionLifecycle` class periodically checks if the session needs to be
 * refreshed (every 30 seconds by default). The [refreshSessionIfNeeded] function
 * will refresh the session if it's about to expire (within 60 seconds by default)
 * or if it's already expired.
 */
class SessionLifecycle(
    private val auth: DescopeAuth,
    private val storage: DescopeSessionStorage,
    private val logger: DescopeLogger?,
) : DescopeSessionLifecycle {

    var shouldSaveAfterPeriodicRefresh: Boolean = true
    var refreshTriggerInterval: Long = 60 /* seconds */ * SECOND
    var periodicCheckFrequency: Long = 30 /* seconds */ * SECOND

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                resetTimer()
            }

            override fun onStop(owner: LifecycleOwner) {
                stopTimer()
            }
        })
    }

    override var session: DescopeSession? = null
        set(value) {
            if (value?.refreshToken == field?.refreshToken) {
                field = value
                return
            }

            field = value
            if (value != null && value.refreshToken.isExpired) {
                logger?.log(Info, "Session has an expired refresh token", value.refreshToken.expiresAt)
            }
            resetTimer()
        }

    override suspend fun refreshSessionIfNeeded(): Boolean {
        val current = session
        if (current == null || !shouldRefresh(current)) {
            return false
        }
        
        logger?.log(Info, "Refreshing session that is about to expire", current.sessionToken.expiresAt)
        val response = auth.refreshSession(current.refreshJwt)
        if (session?.sessionJwt != current.sessionJwt) {
            logger?.log(Info, "Skipping refresh because session has changed in the meantime")
            return false
        }
        
        session = session?.withUpdatedTokens(response)
        return true
    }

    // Internal

    private fun shouldRefresh(session: DescopeSession): Boolean {
        val isRefreshValid = !session.refreshToken.isExpired  
        val isSessionAlmostExpired = session.sessionToken.expiresAt - System.currentTimeMillis() <= refreshTriggerInterval
        return isRefreshValid && isSessionAlmostExpired
    }

    // Timer

    private var timer: Timer? = null
    
    private fun resetTimer() {
        val refreshToken = session?.refreshToken
        if (periodicCheckFrequency > 0 && refreshToken != null && !refreshToken.isExpired) {
            startTimer()
        } else {
            stopTimer()
        }
    }

    private fun startTimer() {
        stopTimer()
        
        val ref = WeakReference(this)
        val action = createTimerAction(ref)
        timer = timer(name = "DescopeSessionLifecycle", period = periodicCheckFrequency, action = action)
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }
    
    // Periodic Refresh
    
    internal suspend fun periodicRefresh() {
        val refreshToken = session?.refreshToken

        if (refreshToken == null || refreshToken.isExpired) {
            logger?.log(Debug, "Stopping periodic refresh for session with expired refresh token")
            stopTimer()
            return
        }
        
        try {
            val refreshed = refreshSessionIfNeeded()
            if (refreshed && shouldSaveAfterPeriodicRefresh) {
                logger?.log(Debug, "Saving refresh session after periodic refresh")
                session?.let { storage.saveSession(it) }
            }
        } catch (e: DescopeException) {
            if (e == DescopeException.networkError) {
                logger?.log(Debug, "Ignoring network error in periodic refresh")
            } else {
                logger?.log(Error, "Stopping periodic refresh after failure", e)
                stopTimer()
            }
        } catch (e: Exception) {
            logger?.log(Error, "Stopping periodic refresh after unexpected failure", e)
            stopTimer()
        }
    }
}

private const val SECOND = 1000L

@OptIn(DelicateCoroutinesApi::class)
private fun createTimerAction(ref: WeakReference<SessionLifecycle>): (TimerTask.() -> Unit) {
    return {
        val lifecycle = ref.get()
        if (lifecycle == null) {
            cancel()
        } else {
            GlobalScope.launch(Dispatchers.Main) {
                lifecycle.periodicRefresh()
            }
        }
    }
}
