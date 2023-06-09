package com.descope.session

import android.content.Context
import android.net.Uri
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.descope.Descope
import com.descope.internal.others.stringOrEmptyAsNull
import com.descope.internal.others.toJsonArray
import com.descope.internal.others.toStringList
import com.descope.internal.others.tryOrNull
import com.descope.types.DescopeUser
import org.json.JSONObject


/**
 * This protocol can be used to customize how a [DescopeSessionManager] object
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
 * The `SessionStorage` class ensures that the [DescopeSession] is kept in
 * a secure manner in the device's keychain.
 *
 * By default, the `SessionStorage` persists the [DescopeSession] securely using
 * [EncryptedSharedPreferences]. This another layer of precaution over the fact
 * that applications are sandboxed.
 *
 * - **NOTE:** In order to take advantage of the [EncryptedSharedPreferences] based
 * storage, you must make sure [Descope.provideApplicationContext] provides the application
 * context and is not `null`:
 *
 *
 *     Descope.provideApplicationContext = { applicationContext }
 *
 * For your convenience, you can implement the [SessionStorage.Store] class and
 * override the [SessionStorage.Store.loadItem], [SessionStorage.Store.saveItem]
 * and [SessionStorage.Store.removeItem] functions, then pass an
 * instance of that class to the constructor to create a [SessionStorage] object
 * that uses a different backing store.
 *
 * @property projectId the Descope projectId
 * @param store an optional implementation of [SessionStorage.Store]
 */
class SessionStorage(private val projectId: String, store: Store? = null) : DescopeSessionStorage {

    private val store: Store
    private var lastValue: Value? = null

    init {
        this.store = when {
            store != null -> store
            else -> Descope.provideApplicationContext?.run { EncryptedSharedPrefs(projectId, invoke()) }
                ?: Store.none
        }
    }

    override fun saveSession(session: DescopeSession) {
        val value = Value(
            sessionJwt = session.sessionJwt,
            refreshJwt = session.refreshJwt,
            user = session.user
        )
        if (value == lastValue) return
        store.saveItem(key = projectId, data = value.serialized)
        lastValue = value
    }

    override fun loadSession(): DescopeSession? =
        store.loadItem(projectId)?.run {
            val value = tryOrNull { Value.deserialize(this) } ?: return null // TODO: maybe print an error here?
            lastValue = value
            DescopeSession(
                sessionJwt = value.sessionJwt,
                refreshJwt = value.refreshJwt,
                user = value.user,
            )
        }

    override fun removeSession() {
        lastValue = null
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

    private data class Value(
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
            fun deserialize(string: String): Value = JSONObject(string).run {
                Value(
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
}


