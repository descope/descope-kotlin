package com.descope.session

import com.descope.types.DescopeUser
import com.descope.types.RefreshResponse
import java.net.URLConnection

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
 * You can customize how the `DescopeSessionManager` behaves by using your own
 * `storage` and `lifecycle` objects. See the documentation for the initializer
 * below for more details.
 *
 * @property storage the [DescopeSessionStorage] enables the session manager to persist the session between app usages.
 * @property lifecycle the [DescopeSessionLifecycle] makes sure the session is valid during app usage.
 */
@Suppress("unused")
class DescopeSessionManager(
    private val storage: DescopeSessionStorage,
    private val lifecycle: DescopeSessionLifecycle,
) {

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
        fun onUpdateTokens(session: DescopeSession)

        /**
         * Called after the session is updated via [updateUser]
         *
         * @param session the updated session
         */
        fun onUpdateUser(session: DescopeSession)
    }

    /** The [DescopeSession] managed by this session manager. */
    val session: DescopeSession?
        get() = lifecycle.session

    init {
        lifecycle.session = storage.loadSession()
        lifecycle.onPeriodicRefresh = {
            onUpdateTokens()
        }
    }

    /**
     * Adds a listener to the session manager.
     *
     * The listener will be notified of session updates and user updates.
     *
     * @param listener the listener to add
     */
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
    fun manageSession(session: DescopeSession) {
        val current = lifecycle.session
        lifecycle.session = session
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
    fun clearSession() {
        lifecycle.session = null
        storage.removeSession()
    }

    /**
     * Ensures that the session is valid and refreshes it if needed.
     *
     * The session manager checks whether there's an active [DescopeSession] and if
     * its session JWT expires within the next 60 seconds. If that's the case then
     * the session is refreshed and persisted before returning.
     *
     * - **Note:** When using a custom [DescopeSessionManager] object the exact behavior
     *     here depends on the `storage` and `lifecycle` objects.
     */
    suspend fun refreshSessionIfNeeded() {
        val refreshed = lifecycle.refreshSessionIfNeeded()
        if (refreshed) {
            onUpdateTokens()
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
    fun updateTokens(refreshResponse: RefreshResponse) {
        lifecycle.session = session?.withUpdatedTokens(refreshResponse)
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
    fun updateUser(user: DescopeUser) {
        lifecycle.session = session?.withUpdatedUser(user)
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
}
