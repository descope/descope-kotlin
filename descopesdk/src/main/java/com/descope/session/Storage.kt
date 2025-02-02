package com.descope.session

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.descope.internal.others.optionalMap
import com.descope.internal.others.stringOrEmptyAsNull
import com.descope.internal.others.toJsonArray
import com.descope.internal.others.toJsonObject
import com.descope.internal.others.toStringList
import com.descope.internal.others.tryOrNull
import com.descope.sdk.DescopeLogger
import com.descope.sdk.DescopeLogger.Level.Debug
import com.descope.sdk.DescopeLogger.Level.Error
import com.descope.types.DescopeUser
import org.json.JSONObject

/**
 * This interface can be used to customize how a [DescopeSessionManager] object
 * stores the active [DescopeSession] between application launches.
 */
interface DescopeSessionStorage {
    /**
     * Called by the session manager when a new session is set or an
     * existing session is updated.
     *
     * @param session The [DescopeSession] to persist.
     */
    fun saveSession(session: DescopeSession)

    /**
     * Called by the session manager when it's initialized to load any
     * existing session.
     *
     * @return a [DescopeSession] if one is available from the storage
     */
    fun loadSession(): DescopeSession?

    /**
     * Called by the session manager when the `clearSession` function
     * is called.
     */
    fun removeSession()
}

/**
 * The default implementation of the [DescopeSessionStorage] interface.
 *
 * By default, the `SessionStorage` persists the [DescopeSession] securely using
 * [EncryptedSharedPreferences]. This provides another layer of safety over
 * the fact that Android applications and all their data are sandboxed.
 *
 * - **NOTE:** In order to take advantage of the [EncryptedSharedPreferences] based
 * storage, the Descope SDK initializers require a [Context] argument, and you should
 * always make sure to pass the `applicationContext` there:
 *
 *     Descope.setup(applicationContext, projectId = "DESCOPE_PROJECT_ID")
 *
 * For your convenience, you can implement the [SessionStorage.Store] class and
 * override the [SessionStorage.Store.loadItem], [SessionStorage.Store.saveItem]
 * and [SessionStorage.Store.removeItem] functions, then pass an
 * instance of that class to the constructor to create a [SessionStorage] object
 * that uses a different backing store.
 *
 * @param context the application context
 * @property projectId the Descope projectId
 * @param logger an optional [DescopeLogger] for logging during development
 * @param store an optional implementation of [SessionStorage.Store]
 */
class SessionStorage(context: Context, private val projectId: String, logger: DescopeLogger? = null, store: Store? = null) : DescopeSessionStorage {

    private val store: Store
    private var lastSaved: EncodedSession? = null

    init {
        this.store = store ?: createEncryptedStore(context, projectId, logger)
    }

    override fun saveSession(session: DescopeSession) {
        val encodedSession = EncodedSession(
            sessionJwt = session.sessionJwt,
            refreshJwt = session.refreshJwt,
            user = session.user
        )
        if (encodedSession == lastSaved) return
        store.saveItem(key = projectId, data = encodedSession.serialized)
        lastSaved = encodedSession
    }

    override fun loadSession(): DescopeSession? =
        store.loadItem(projectId)?.run {
            val encodedSession = tryOrNull { EncodedSession.deserialize(this) } ?: return null
            lastSaved = encodedSession
            DescopeSession(
                sessionJwt = encodedSession.sessionJwt,
                refreshJwt = encodedSession.refreshJwt,
                user = encodedSession.user,
            )
        }

    override fun removeSession() {
        lastSaved = null
        store.removeItem(projectId)
    }

    /**
     * A helper interface that takes care of the actual storage of session data.
     *
     * The default function implementations in this interface do nothing or return `null`.
     */
    interface Store {
        fun saveItem(key: String, data: String) {}

        fun loadItem(key: String): String? = null

        fun removeItem(key: String) {}

        companion object {
            /** A store that does nothing */
            val none = object : Store {}
        }
    }

    private data class EncodedSession(
        val sessionJwt: String,
        val refreshJwt: String,
        val user: DescopeUser,
    ) {
        val serialized: String
            get() = JSONObject().apply {
                put("sessionJwt", sessionJwt)
                put("refreshJwt", refreshJwt)
                put("user", user.toJson())
            }.toString()

        companion object {
            fun deserialize(string: String): EncodedSession = JSONObject(string).run {
                EncodedSession(
                    sessionJwt = getString("sessionJwt"),
                    refreshJwt = getString("refreshJwt"),
                    user = deserializeDescopeUser(getJSONObject("user"))
                )
            }
        }
    }

}

/**
 * A [SessionStorage.Store] implementation using [EncryptedSharedPreferences] as
 * the backing store.
 */
class EncryptedSharedPrefs(name: String, context: Context) : SessionStorage.Store {
    private val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = EncryptedSharedPreferences.create(
        name,
        masterKey,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun loadItem(key: String): String? = sharedPreferences.getString(key, null)

    override fun saveItem(key: String, data: String) = sharedPreferences.edit()
        .putString(key, data)
        .apply()

    override fun removeItem(key: String) = sharedPreferences.edit()
        .remove(key)
        .apply()
}

// Serialization

private fun deserializeDescopeUser(json: JSONObject): DescopeUser = json.run {
    DescopeUser(
        userId = getString("userId"),
        loginIds = getJSONArray("loginIds").toStringList(),
        createdAt = getLong("createdAt"),
        name = stringOrEmptyAsNull("name"),
        picture = stringOrEmptyAsNull("picture")?.run { Uri.parse(this) },
        email = stringOrEmptyAsNull("email"),
        isVerifiedEmail = optBoolean("isVerifiedEmail"),
        phone = stringOrEmptyAsNull("phone"),
        isVerifiedPhone = optBoolean("isVerifiedPhone"),
        customAttributes = optionalMap("customAttributes"),
        givenName = stringOrEmptyAsNull("givenName"),
        middleName = stringOrEmptyAsNull("middleName"),
        familyName = stringOrEmptyAsNull("familyName"),
    )
}

private fun DescopeUser.toJson() = JSONObject().apply {
    put("userId", userId)
    put("loginIds", loginIds.toJsonArray())
    put("createdAt", createdAt)
    put("name", name)
    put("picture", picture?.toString())
    put("email", email)
    put("isVerifiedEmail", isVerifiedEmail)
    put("phone", phone)
    put("isVerifiedPhone", isVerifiedPhone)
    put("customAttributes", customAttributes.toJsonObject())
    put("givenName", givenName)
    put("middleName", middleName)
    put("familyName", familyName)
}

private fun createEncryptedStore(context: Context, projectId: String, logger: DescopeLogger?): SessionStorage.Store {
    try {
        val storage = EncryptedSharedPrefs(projectId, context)
        logger?.log(Debug, "Encrypted storage initialized successfully")
        return storage
    } catch (e: Exception) {
        try {
            logger?.log(Error, "Encrypted storage key unusable")
            context.deleteSharedPreferences(projectId)
            return EncryptedSharedPrefs(projectId, context)
        } catch (e: Exception) {
            logger?.log(Error, "Unable to initialize encrypted storage", e)
            return SessionStorage.Store.none
        }
    }
}