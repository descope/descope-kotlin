package com.descope.session

import android.annotation.SuppressLint
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
import kotlin.concurrent.timerTask

const val SECOND = 1000L

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
 *
 * @property auth used to refresh the session when needed
 */
class SessionLifecycle(
    private val auth: DescopeAuth,
    private val storage: DescopeSessionStorage,
    private val logger: DescopeLogger?,
) : DescopeSessionLifecycle {

    var shouldSaveAfterPeriodicRefresh: Boolean = true
    var stalenessAllowedInterval: Long = 60L /* seconds */ * SECOND
    var periodicCheckFrequency: Long = 30L /* seconds */ * SECOND

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
            if (value?.refreshToken?.isExpired == true) {
                logger?.log(Info, "Session has an expired refresh token", session?.refreshToken?.expiresAt)
            }
        }

    override suspend fun refreshSessionIfNeeded(): Boolean {
        val session = this.session ?: return false
        return if (shouldRefresh(session)) {
            logger?.log(Info, "Refreshing session that is about to expire", session.sessionToken.expiresAt)
            val response = auth.refreshSession(session.refreshJwt)
            if (this.session?.sessionJwt != session.sessionJwt) {
                logger?.log(Info, "Skipping refresh because session has changed in the meantime")
                return false
            }
            session.updateTokens(response)
            true
        } else false
    }

    // Internal

    private fun shouldRefresh(session: DescopeSession): Boolean {
        return session.sessionToken.expiresAt - System.currentTimeMillis() <= stalenessAllowedInterval
    }

    // Timer

    private var timer: Timer? = null

    @SuppressLint("DiscouragedApi")
    @OptIn(DelicateCoroutinesApi::class)
    private fun startTimer(runImmediately: Boolean = false) {
        val weakRef = WeakReference(this)
        val delay = if (runImmediately) 0L else periodicCheckFrequency
        timer?.run { cancel(); purge() }
        timer = Timer().apply {
            scheduleAtFixedRate(timerTask {
                val ref = weakRef.get()
                if (ref == null) {
                    stopTimer()
                    return@timerTask
                }
                if (session?.refreshToken?.isExpired != false) {
                    logger?.log(Debug, "Stopping periodic refresh for session with expired refresh token")
                    stopTimer()
                    return@timerTask
                }
                GlobalScope.launch(Dispatchers.Main) {
                    try {
                        val refreshed = ref.refreshSessionIfNeeded()
                        val session = session
                        if (refreshed && shouldSaveAfterPeriodicRefresh && session != null) {
                            logger?.log(Debug, "Saving refresh session after periodic refresh")
                            storage.saveSession(session)
                        }
                    } catch (descopeException: DescopeException) {
                        // allow retries on network errors
                        if (descopeException != DescopeException.networkError) {
                            logger?.log(Error, "Stopping periodic refresh after failure", descopeException)
                            stopTimer()
                        } else {
                            logger?.log(Debug, "Ignoring network error in periodic refresh")
                        }
                    } catch (e: Exception) {
                        logger?.log(Error, "Stopping periodic refresh after unexpected failure", e)
                        stopTimer()
                    }
                }
            }, delay, periodicCheckFrequency)
        }
    }

    private fun stopTimer() {
        timer?.run { cancel(); purge() }
        timer = null
    }

}
