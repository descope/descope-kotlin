package com.descope.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.security.InvalidKeyException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class StorageTest {

    private val secretKey = makeKey()

    @Test
    fun encryption_roundTrip() {
        val payload = encryptItem(secretKey, "data", "key")
        assertEquals("data", decryptItem(secretKey, payload, "key"))
    }

    @Test
    fun encryption_randomIv() {
        val first = encryptItem(secretKey, "data", "key")
        val second = encryptItem(secretKey, "data", "key")
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun decryption_wrongKey() {
        val payload = encryptItem(secretKey, "data", "key")
        assertNull(decryptItem(makeKey(), payload, "key"))
    }

    @Test
    fun decryption_wrongItemKey() {
        val payload = encryptItem(secretKey, "data", "key")
        assertNull(decryptItem(secretKey, payload, "other"))
    }

    @Test
    fun decryption_tampered() {
        val payload = encryptItem(secretKey, "data", "key")
        payload[payload.size - 1] = (payload.last() + 1).toByte()
        assertNull(decryptItem(secretKey, payload, "key"))
    }

    @Test
    fun decryption_malformed() {
        val payload = encryptItem(secretKey, "data", "key")
        assertNull(decryptItem(secretKey, byteArrayOf(), "key"))
        assertNull(decryptItem(secretKey, payload.copyOf(13), "key"))
        assertNull(decryptItem(secretKey, payload.copyOf().apply { this[0] = 2 }, "key"))
    }

    @Test
    fun decryption_keyFailure() {
        val payload = encryptItem(secretKey, "data", "key")
        assertThrows(InvalidKeyException::class.java) {
            decryptItem(brokenKey, payload, "key")
        }
    }

    @Test
    fun migration_freshInstall() {
        val current = MemoryStore()
        val store = MigratingStore(current, { null }, null)
        assertNull(store.loadItem("key"))
        store.saveItem("key", "data")
        assertEquals("data", current.items["key"])
    }

    @Test
    fun migration_upgrade() {
        val current = MemoryStore()
        val legacy = MemoryStore().apply { items["key"] = "data" }
        val store = MigratingStore(current, { legacy }, null)
        assertEquals("data", store.loadItem("key"))
        assertEquals("data", current.items["key"])
        assertTrue(legacy.items.isEmpty())
    }

    @Test
    fun migration_alreadyMigrated() {
        val current = MemoryStore().apply { items["key"] = "data" }
        val store = MigratingStore(current, { throw AssertionError("legacy store opened") }, null)
        assertEquals("data", store.loadItem("key"))
    }

    @Test
    fun migration_failedWrite() {
        val current = object : MemoryStore() {
            override fun saveItem(key: String, data: String) = throw IOException()
        }
        val legacy = MemoryStore().apply { items["key"] = "data" }
        val store = MigratingStore(current, { legacy }, null)
        assertEquals("data", store.loadItem("key"))
        assertEquals("data", legacy.items["key"])
        store.saveItem("key", "other")
    }

    @Test
    fun migration_unverifiedWrite() {
        val current = object : MemoryStore() {
            override fun saveItem(key: String, data: String) {}
        }
        val legacy = MemoryStore().apply { items["key"] = "data" }
        val store = MigratingStore(current, { legacy }, null)
        assertEquals("data", store.loadItem("key"))
        assertEquals("data", legacy.items["key"])
    }

    @Test
    fun migration_failedLoad() {
        val current = object : MemoryStore() {
            override fun loadItem(key: String): String = throw IOException()
        }
        val legacy = MemoryStore().apply { items["key"] = "data" }
        val store = MigratingStore(current, { legacy }, null)
        assertNull(store.loadItem("key"))
        assertEquals("data", legacy.items["key"])
    }

    @Test
    fun migration_signOut() {
        val current = MemoryStore().apply { items["key"] = "data" }
        val legacy = MemoryStore().apply { items["key"] = "data" }
        val store = MigratingStore(current, { legacy }, null)
        store.removeItem("key")
        assertTrue(current.items.isEmpty())
        assertTrue(legacy.items.isEmpty())
    }
}

private open class MemoryStore : SessionStorage.Store {
    val items = mutableMapOf<String, String>()

    override fun loadItem(key: String): String? = items[key]

    override fun saveItem(key: String, data: String) {
        items[key] = data
    }

    override fun removeItem(key: String) {
        items.remove(key)
    }
}

private val brokenKey = object : SecretKey {
    override fun getAlgorithm() = "AES"
    override fun getFormat() = "RAW"
    override fun getEncoded(): ByteArray? = null
}

private fun makeKey(): SecretKey = KeyGenerator.getInstance("AES").run {
    init(256)
    generateKey()
}
