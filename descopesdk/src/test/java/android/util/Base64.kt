@file:Suppress("unused", "UNUSED_PARAMETER")

package android.util

import java.util.Base64

object Base64 {
    @JvmStatic
    fun encodeToString(input: ByteArray?, flags: Int): String {
        return Base64.getEncoder().encodeToString(input)
    }

    @JvmStatic
    fun decode(str: String?, flags: Int): ByteArray {
        return Base64.getDecoder().decode(str)
    }
}
