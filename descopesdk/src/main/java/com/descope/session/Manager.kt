package com.descope.session

import androidx.annotation.MainThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.descope.internal.others.debug
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.sdk.DescopeAuth
import com.descope.sdk.DescopeLogger
import com.descope.types.DescopeException
import com.descope.types.DescopeUser
import com.descope.types.RefreshResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.net.URLConnection
import java.util.Timer
import java.util.TimerTask
import kotlin.concurrent.timer

/**
 * The `DescopeSessionManager` class is used to manage an authenticated
 * user session for an application.
 *
 * The session manager takes care of loading and saving the session as well
 * as ensuring that it's refreshed when needed. For the default instances of
 * the `DescopeSessionManager` class this means using the [EncryptedSharedPrefs] for secure
 * storage of the session and refreshing it a short while before it expires.
 *
 * Once the user completes a sign in flow successfully you should set the
 * [DescopeSession] object as the active session of the session manager.
 *
 *     val authResponse = Descope.otp.verify(DeliverMethod.Email, "andy@example.com", "123456")
 *     val session = DescopeSession(authResponse)
 *     Descope.sessionManager.manageSession(session)
 *
 * The session manager can then be used at any time to ensure the session
 * is valid and to authenticate outgoing requests to your backend with a
 * bearer token authorization header.
 *
 *     val connection = url.openConnection() as HttpsURLConnection
 *     connection.setAuthorization(Descope.sessionManager)
 *
 * If your backend uses a different authorization mechanism you can of course
 * use the session JWT directly instead of the extension function. You can either
 * add another extension function on [URLConnection] such as the one above, or you
 * can do the following.
 *
 *     Descope.sessionManager.refreshSessionIfNeeded()
 *     Descope.sessionManager.session?.sessionJwt?.apply {
 *       connection.setRequestProperty("X-Auth-Token", this)
 *     } ?: throw ServerError.unauthorized
 *
 * The same principals can be used in the various networking libraries available,
 * if those are used in your application.
 *
 * When the application is relaunched the `DescopeSessionManager` loads any
 * existing session automatically, so you can check straight away if there's
 * an authenticated user.
 *
 *     // Application class onCreate
 *     override fun onCreate() {
 *         super.onCreate()
 *         Descope.setup(this, projectId = "<Your-Project-Id>")
 *         Descope.sessionManager.session?.run {
 *             print("User is logged in: $this")
 *         }
 *     }
 *
 * When the user wants to sign out of the application we revoke the active
 * session and clear it from the session manager:
 *
 *     Descope.sessionManager.session?.refreshJwt?.run {
 *         Descope.auth.logout(this)
 *         Descope.sessionManager.clearSession()
 *     }
 *
 * While the application is in the foreground the session manager periodically checks
 * if the session needs to be refreshed (every 30 seconds by default), and refreshes it
 * if it's about to expire (within 60 seconds by default) or if it's already expired.
 *
 * The session manager must be created and used on the main thread, and all updates
 * to its [session] are performed on the main thread.
 *
 * You can customize how the `DescopeSessionManager` stores the session by using
 * your own `storage` object. See the documentation for the constructor below for
 * more details.
 *
 * @property storage the [DescopeSessionStorage] enables the session manager to persist the session between app usages.
 * @property auth the [DescopeAuth] used to refresh the session.
 * @property logger the optional [DescopeLogger] used by the session manager.
 */
class DescopeSessionManager internal constructor(
    private val storage: DescopeSessionStorage,
    private val auth: DescopeAuth,
    private val logger: DescopeLogger?,
    appLifecycle: Lifecycle?,
) {

    @MainThread
    constructor(
        storage: DescopeSessionStorage,
        auth: DescopeAuth,
        logger: DescopeLogger? = null,
    ) : this(storage, auth, logger, ProcessLifecycleOwner.get().lifecycle)

    /**
     * A set of listener methods for events about the session managed by a [DescopeSessionManager].
     */
    interface Listener {
        /**
         * Called after the session tokens are updated due to a successful refresh or by
         * a call to [updateTokens].
         *
         * @param session the updated session
         */
        @MainThread
        fun onUpdateTokens(session: DescopeSession)

        /**
         * Called after the session is updated via [updateUser]
         *
         * @param session the updated session
         */
        @MainThread
        fun onUpdateUser(session: DescopeSession)
    }

    /** The [DescopeSession] managed by this session manager. */
    var session: DescopeSession? = storage.loadSession()
        private set(value) {
            val previous = field
            field = value
            if (value?.refreshJwt != previous?.refreshJwt) {
                resetTimer()
            }
            if (value != null && value.refreshToken.isExpired) {
                logger.debug("Session has an expired refresh token", value.refreshToken.expiresAt)
            }
        }

    /** How long before the session JWT expires the session is considered to need a refresh. */
    var refreshTriggerInterval: Long = 60 /* seconds */ * SECOND

    /** How often the session manager checks if the session needs to be refreshed, or 0 to disable. */
    var periodicCheckFrequency: Long = 30 /* seconds */ * SECOND
        set(value) {
            if (value != field) {
                field = value
                resetTimer()
            }
        }

    /**
     * Adds a listener to the session manager.
     *
     * The listener will be notified of session updates and user updates.
     *
     * @param listener the listener to add
     */
    @MainThread
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    /**
     * Removes a listener from the session manager.
     *
     * The listener will no longer receive updates.
     *
     * @param listener the listener to remove
     */
    @MainThread
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Set an active [DescopeSession] in this manager.
     *
     * You should call this function after a user finishes logging in to the
     * host application.
     *
     * The parameter is set as the value of the [session] property and is persisted
     * so it can be reloaded on the next application launch or
     * [DescopeSessionManager] instantiation.
     *
     * - **Important:** The default [DescopeSessionStorage] only keeps at most
     *     one session in the storage for simplicity. If for some reason you
     *     have multiple [DescopeSessionManager] objects then be aware that
     *     unless they use custom `storage` objects they might overwrite
     *     each other's saved sessions.
     *
     * @param session the session to manage
     */
    @MainThread
    fun manageSession(session: DescopeSession) {
        val current = this.session
        this.session = session
        storage.saveSession(session)
        // notify the listeners if the session has been updated
        if (current == null) return
        if (current.sessionJwt != session.sessionJwt || current.refreshJwt != session.refreshJwt) {
            listeners.forEach { it.onUpdateTokens(session) }
        }
        if (current.user != session.user) {
            listeners.forEach { it.onUpdateUser(session) }
        }
    }

    /**
     * Clears any active [DescopeSession] from this manager and removes it
     * from the storage.
     *
     * You should call this function as part of a logout flow in the host application.
     * The `session` property is set to `null` and the session won't be reloaded in
     * subsequent application launches.
     *
     * - **Important:** The default [DescopeSessionStorage] only keeps at most
     *     one session in the storage for simplicity. If for some reason you
     *     have multiple [DescopeSessionManager] objects then be aware that
     *     unless they use custom `storage` objects they might clear
     *     each other's saved sessions.
     */
    @MainThread
    fun clearSession() {
        session = null
        storage.removeSession()
    }

    /**
     * Ensures that the session is valid and refreshes it if needed.
     *
     * The session manager checks whether there's an active [DescopeSession] and if
     * its session JWT expires within the next 60 seconds. If that's the case then
     * the session is refreshed and persisted before returning.
     *
     * Concurrent calls share a single refresh, and all of them return or throw
     * with its result once it completes. The refresh and the resulting session update
     * are performed on the main thread, so don't block the main thread while waiting
     * for this function to return.
     *
     * - **Note:** When using a custom `storage` object the exact behavior of saving
     *     the refreshed session depends on its implementation.
     */
    suspend fun refreshSessionIfNeeded() {
        withContext(Dispatchers.Main.immediate) {
            val current = session
            if (current == null || !shouldRefresh(current)) return@withContext

            // join the refresh already in flight for this session or start a new one
            val refresh = refreshes[current.sessionJwt] ?: startRefresh(current)

            refresh.await().onFailure { error ->
                // the failure doesn't matter anymore if the session was replaced in the meantime
                if (session?.sessionJwt == current.sessionJwt) throw error
            }
        }
    }

    /**
     * Updates the active session's underlying JWTs.
     *
     * This function accepts a [RefreshResponse] value as a parameter which is returned
     * by calls to `Descope.auth.refreshSession`. The manager persists the updated session
     * before returning (by default).
     *
     * - **Important:** In most circumstances it's best to use `refreshSessionIfNeeded` and let
     *     it update the session unless you need to invoke `Descope.auth.refreshSession`
     *     manually.
     *
     * - **Note:** If the [DescopeSessionManager] object was created with a custom `storage`
     *     object then the exact behavior depends on the specific implementation of the
     *     `DescopeSessionStorage` interface.
     *
     * @param refreshResponse the response after calling `Descope.auth.refreshSession`
     */
    @MainThread
    fun updateTokens(refreshResponse: RefreshResponse) {
        session = session?.withUpdatedTokens(refreshResponse)
        onUpdateTokens()
    }

    /**
     * Updates the active session's user details.
     *
     * This function accepts a [DescopeUser] value as a parameter which is returned by
     * calls to `Descope.auth.me`. The manager saves the updated session to the
     * storage before returning.
     *
     *     val userResponse = Descope.auth.me(session.refreshJwt)
     *     Descope.sessionManager.updateUser(userResponse)
     *
     * By default, the manager persists the updated session to the [EncryptedSharedPrefs]
     * before returning, but this can be overridden with a custom `DescopeSessionStorage` object.
     *
     * @param user the [DescopeUser] to update.
     */
    @MainThread
    fun updateUser(user: DescopeUser) {
        session = session?.withUpdatedUser(user)
        onUpdateUser()
    }

    // Internal

    private val listeners = mutableSetOf<Listener>()

    private fun onUpdateTokens() {
        val session = session ?: return
        storage.saveSession(session)
        listeners.forEach { it.onUpdateTokens(session) }
    }

    private fun onUpdateUser() {
        val session = session ?: return
        storage.saveSession(session)
        listeners.forEach { it.onUpdateUser(session) }
    }

    // Refresh

    // dispatched rather than immediate so a refresh is always added to refreshes before it runs
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val refreshes = mutableMapOf<String, Deferred<Result<Unit>>>()

    private fun shouldRefresh(session: DescopeSession): Boolean {
        // don't bother trying to refresh if according to device time the refresh token is already expired
        if (session.refreshToken.isExpired) return false
        // only bother refreshing if we're close enough to the session token expiration
        if (session.sessionToken.expiresAt - System.currentTimeMillis() > refreshTriggerInterval) return false
        // don't bother trying to refresh if the new session token will just have the same expiration
        if (session.refreshToken.expiresAt - session.sessionToken.expiresAt < SECOND) return false
        return true
    }

    private fun startRefresh(current: DescopeSession): Deferred<Result<Unit>> {
        val refresh = refreshScope.async { performRefresh(current) }
        refreshes[current.sessionJwt] = refresh
        return refresh
    }

    private suspend fun performRefresh(current: DescopeSession): Result<Unit> {
        try {
            logger.info("Refreshing session that is about to expire", current.sessionToken.expiresAt)
            val response = auth.refreshSession(current.refreshJwt)
            if (session?.sessionJwt == current.sessionJwt) {
                session = session?.withUpdatedTokens(response)
                onUpdateTokens()
            } else {
                logger.info("Skipping refresh because session has changed in the meantime")
            }
            return Result.success(Unit)
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            refreshes.remove(current.sessionJwt)
        }
    }

    // Periodic refresh

    private var timer: Timer? = null

    private fun resetTimer(initialDelay: Long = periodicCheckFrequency) {
        val refreshToken = session?.refreshToken
        if (periodicCheckFrequency > 0 && refreshToken != null && !refreshToken.isExpired) {
            startTimer(initialDelay)
        } else {
            stopTimer()
        }
    }

    private fun startTimer(initialDelay: Long) {
        stopTimer()
        val ref = WeakReference(this)
        val action = createTimerAction(ref)
        timer = timer(name = "DescopeSessionManager", initialDelay = initialDelay, period = periodicCheckFrequency, action = action)
    }

    private fun stopTimer() {
        timer?.cancel()
        timer = null
    }

    internal fun onForeground() {
        // check right away since the session might have expired while the app was in the background
        resetTimer(initialDelay = 0)
    }

    internal fun onBackground() {
        stopTimer()
    }

    internal suspend fun periodicRefresh() {
        val refreshToken = session?.refreshToken
        if (refreshToken == null || refreshToken.isExpired) {
            logger.debug("Stopping periodic refresh for session with expired refresh token")
            stopTimer()
            return
        }

        try {
            refreshSessionIfNeeded()
        } catch (e: DescopeException) {
            if (e == DescopeException.networkError) {
                logger.debug("Ignoring network error in periodic refresh")
            } else {
                logger.error("Stopping periodic refresh after failure", e)
                stopTimer()
            }
        } catch (e: Exception) {
            logger.error("Stopping periodic refresh after unexpected failure", e)
            stopTimer()
        }
    }

    init {
        resetTimer()
        appLifecycle?.addObserver(createLifecycleObserver(WeakReference(this)))
    }
}

private const val SECOND = 1000L

@OptIn(DelicateCoroutinesApi::class)
private fun createTimerAction(ref: WeakReference<DescopeSessionManager>): (TimerTask.() -> Unit) {
    return {
        val manager = ref.get()
        if (manager == null) {
            cancel()
        } else {
            GlobalScope.launch(Dispatchers.Main) {
                manager.periodicRefresh()
            }
        }
    }
}

private fun createLifecycleObserver(ref: WeakReference<DescopeSessionManager>): DefaultLifecycleObserver {
    return object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            val manager = ref.get()
            if (manager == null) {
                owner.lifecycle.removeObserver(this)
            } else {
                manager.onForeground()
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            val manager = ref.get()
            if (manager == null) {
                owner.lifecycle.removeObserver(this)
            } else {
                manager.onBackground()
            }
        }
    }
}
