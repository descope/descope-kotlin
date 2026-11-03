package com.descope.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import androidx.core.net.toUri
import com.descope.internal.others.booleanOrNull
import com.descope.internal.others.decodeBase64
import com.descope.internal.others.error
import com.descope.internal.others.info
import com.descope.internal.others.optionalMap
import com.descope.internal.others.stringOrEmptyAsNull
import com.descope.internal.others.toBase64
import com.descope.internal.others.toJsonArray
import com.descope.internal.others.toJsonObject
import com.descope.internal.others.toStringList
import com.descope.internal.others.toStringSet
import com.descope.internal.others.tryOrNull
import com.descope.sdk.DescopeLogger
import com.descope.types.DescopeUser
import org.json.JSONObject
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

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
 * [KeystoreSharedPrefs]. This provides another layer of safety over
 * the fact that Android applications and all their data are sandboxed.
 * Sessions saved by older SDK versions with [EncryptedSharedPrefs] are moved
 * to the new storage the first time they are loaded.
 *
 * - **NOTE:** In order to take advantage of the [KeystoreSharedPrefs] based
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

    private val store: Store = store ?: createDefaultStore(context, projectId, logger)
    private var lastSaved: EncodedSession? = null

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
                put("user", user.serialize())
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
 * A [SessionStorage.Store] implementation using `EncryptedSharedPreferences` as
 * the backing store.
 */
@Deprecated(message = "Use KeystoreSharedPrefs instead. Existing values are not moved to it automatically.")
@Suppress("DEPRECATION")
class EncryptedSharedPrefs(name: String, context: Context) : SessionStorage.Store {
    private val masterKey = androidx.security.crypto.MasterKeys.getOrCreate(androidx.security.crypto.MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = androidx.security.crypto.EncryptedSharedPreferences.create(
        name,
        masterKey,
        context,
        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun loadItem(key: String): String? = sharedPreferences.getString(key, null)

    override fun saveItem(key: String, data: String) = sharedPreferences.edit {
        putString(key, data)
    }

    override fun removeItem(key: String) = sharedPreferences.edit {
        remove(key)
    }
}

/**
 * A [SessionStorage.Store] implementation that encrypts values with an AES-GCM key
 * held in the Android Keystore, and saves them in regular [android.content.SharedPreferences].
 *
 * Values that cannot be decrypted are removed and treated as missing. This can happen
 * when the preferences file is restored from a backup on another device.
 */
class KeystoreSharedPrefs(name: String, context: Context) : SessionStorage.Store {
    private val alias = "com.descope.session.$name"
    private val sharedPreferences = context.getSharedPreferences(alias, Context.MODE_PRIVATE)

    override fun loadItem(key: String): String? {
        val value = sharedPreferences.getString(key, null) ?: return null
        val payload = tryOrNull { value.decodeBase64() }
        val secretKey = loadKeystoreKey(alias)
        val decrypted = if (payload != null && secretKey != null) decryptItem(secretKey, payload, key) else null
        if (decrypted == null) {
            removeItem(key)
            return null
        }
        return decrypted
    }

    override fun saveItem(key: String, data: String) {
        val secretKey = synchronized(keystoreLock) { loadKeystoreKey(alias) ?: createKeystoreKey(alias) }
        val encrypted = encryptItem(secretKey, data, key).toBase64()
        sharedPreferences.edit { putString(key, encrypted) }
    }

    override fun removeItem(key: String) = sharedPreferences.edit {
        remove(key)
    }
}

// Encryption

private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val ITEM_CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
private const val ITEM_FORMAT_VERSION: Byte = 1
private const val ITEM_IV_LENGTH = 12
private const val ITEM_TAG_LENGTH = 128

private val keystoreLock = Any()

private fun loadKeystoreKey(alias: String): SecretKey? {
    val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    return keyStore.getKey(alias, null) as? SecretKey
}

private fun createKeystoreKey(alias: String): SecretKey {
    val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
        init(spec)
        generateKey()
    }
}

internal fun encryptItem(secretKey: SecretKey, data: String, key: String): ByteArray {
    val cipher = Cipher.getInstance(ITEM_CIPHER_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
    val iv = cipher.iv
    if (iv?.size != ITEM_IV_LENGTH) throw GeneralSecurityException("Unexpected IV length")
    val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
    return byteArrayOf(ITEM_FORMAT_VERSION) + iv + encrypted
}

internal fun decryptItem(secretKey: SecretKey, payload: ByteArray, key: String): String? {
    if (payload.size <= 1 + ITEM_IV_LENGTH || payload[0] != ITEM_FORMAT_VERSION) return null
    val cipher = Cipher.getInstance(ITEM_CIPHER_TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(ITEM_TAG_LENGTH, payload, 1, ITEM_IV_LENGTH))
    cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
    val decrypted = try {
        cipher.doFinal(payload, 1 + ITEM_IV_LENGTH, payload.size - 1 - ITEM_IV_LENGTH)
    } catch (_: AEADBadTagException) {
        return null
    }
    return String(decrypted, Charsets.UTF_8)
}

// Serialization

internal fun deserializeDescopeUser(json: JSONObject): DescopeUser = json.run {
    val authentication = optJSONObject("authentication").let { json ->
        DescopeUser.Authentication(
            password = json?.optBoolean("password") ?: false,
            passkey = json?.optBoolean("passkey") ?: false,
            totp = json?.optBoolean("totp") ?: false,
            oauth = json?.optJSONArray("oauth")?.toStringSet() ?: emptySet(),
            sso = json?.optBoolean("sso") ?: false,
            scim = json?.optBoolean("scim") ?: false,
        )
    }
    
    val authorization = optJSONObject("authorization").let { json ->
        DescopeUser.Authorization(
            roles = json?.optJSONArray("roles")?.toStringSet() ?: emptySet(),
            ssoAppIds = json?.optJSONArray("ssoAppIds")?.toStringSet() ?: emptySet(),
        )
    }
    
    DescopeUser(
        userId = getString("userId"),
        loginIds = getJSONArray("loginIds").toStringList(),
        createdAt = getLong("createdAt"),
        name = stringOrEmptyAsNull("name"),
        picture = stringOrEmptyAsNull("picture")?.run { this.toUri() },
        email = stringOrEmptyAsNull("email"),
        isVerifiedEmail = optBoolean("isVerifiedEmail"),
        phone = stringOrEmptyAsNull("phone"),
        isVerifiedPhone = optBoolean("isVerifiedPhone"),
        customAttributes = optionalMap("customAttributes"),
        givenName = stringOrEmptyAsNull("givenName"),
        middleName = stringOrEmptyAsNull("middleName"),
        familyName = stringOrEmptyAsNull("familyName"),
        status = DescopeUser.Status.deserialize(stringOrEmptyAsNull("status") ?: "enabled"),
        authentication = authentication,
        authorization = authorization,
        isUpdateRequired = booleanOrNull("isUpdateRequired") ?: true, // if the flag doesn't exist we've got old data without the new fields
    )
}

internal fun DescopeUser.serialize() = JSONObject().apply {
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
    put("authentication", authentication.toJsonObject())
    put("authorization", authorization.toJsonObject())
    put("status", status.serialize())
    put("isUpdateRequired", isUpdateRequired)
}

private fun DescopeUser.Authentication.toJsonObject() = JSONObject().apply {
        put("password", password)
        put("passkey", passkey)
        put("totp", totp)
        put("oauth", oauth.toJsonArray())
        put("sso", sso)
        put("scim", scim)
    }

private fun DescopeUser.Authorization.toJsonObject() = JSONObject().apply {
        put("roles", roles.toJsonArray())
        put("ssoAppIds", ssoAppIds.toJsonArray())
    }

// Migration

private const val LEGACY_KEYSET_PREFIX = "__androidx_security_crypto_encrypted_prefs_"

private fun createDefaultStore(context: Context, projectId: String, logger: DescopeLogger?): SessionStorage.Store {
    val store = KeystoreSharedPrefs(projectId, context)
    return MigratingStore(store, { openLegacyStore(context, projectId, logger) }, logger)
}

@Suppress("DEPRECATION")
private fun openLegacyStore(context: Context, name: String, logger: DescopeLogger?): SessionStorage.Store? {
    val keys = context.getSharedPreferences(name, Context.MODE_PRIVATE).all.keys
    if (keys.all { it.startsWith(LEGACY_KEYSET_PREFIX) }) return null
    return try {
        EncryptedSharedPrefs(name, context)
    } catch (e: Exception) {
        logger.error("Legacy storage key unusable", e)
        context.deleteSharedPreferences(name)
        null
    }
}

internal class MigratingStore(
    private val current: SessionStorage.Store,
    legacy: () -> SessionStorage.Store?,
    private val logger: DescopeLogger?,
) : SessionStorage.Store {
    private val legacy by lazy(legacy)

    override fun loadItem(key: String): String? {
        val value = try {
            current.loadItem(key)
        } catch (e: Exception) {
            logger.error("Unable to load from storage", e)
            return null
        }
        return value ?: migrateItem(key)
    }

    override fun saveItem(key: String, data: String) {
        try {
            current.saveItem(key, data)
        } catch (e: Exception) {
            logger.error("Unable to save to storage", e)
        }
    }

    override fun removeItem(key: String) {
        current.removeItem(key)
        legacy?.removeItem(key)
    }

    private fun migrateItem(key: String): String? {
        val legacy = legacy ?: return null
        val value = legacy.loadItem(key) ?: return null
        saveItem(key, value)
        if (current.loadItem(key) == value) {
            legacy.removeItem(key)
            logger.info("Migrated session from legacy storage")
        }
        return value
    }
}