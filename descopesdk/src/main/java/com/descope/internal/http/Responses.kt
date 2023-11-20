package com.descope.internal.http

import com.descope.internal.others.optionalMap
import com.descope.internal.others.secToMs
import com.descope.internal.others.stringOrEmptyAsNull
import com.descope.internal.others.toStringList
import org.json.JSONObject
import java.net.HttpCookie

private const val SESSION_COOKIE_NAME = "DS"
private const val REFRESH_COOKIE_NAME = "DSR"

internal data class JwtServerResponse(
    val sessionJwt: String?,
    val refreshJwt: String?,
    val user: UserResponse?,
    val firstSeen: Boolean,
) {
    companion object {
        fun fromJson(json: String, cookies: List<HttpCookie>) = JSONObject(json).run {
            var sessionJwt: String? = null
            var refreshJwt: String? = null

            // check cookies for tokens
            cookies.forEach {
                when (it.name) {
                    SESSION_COOKIE_NAME -> sessionJwt = it.value
                    REFRESH_COOKIE_NAME -> refreshJwt = it.value
                }
            }

            JwtServerResponse(
                sessionJwt = stringOrEmptyAsNull("sessionJwt") ?: sessionJwt,
                refreshJwt = stringOrEmptyAsNull("refreshJwt") ?: refreshJwt,
                user = optJSONObject("user")?.run { UserResponse.fromJson(this) },
                firstSeen = optBoolean("firstSeen"),
            )
        }
    }
}

internal data class UserResponse(
    val userId: String,
    val loginIds: List<String>,
    val name: String?,
    val picture: String?,
    val email: String?,
    val verifiedEmail: Boolean,
    val phone: String?,
    val verifiedPhone: Boolean,
    val createdTime: Long,
    val customAttributes: Map<String, Any>,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromJson(json: String, cookies: List<HttpCookie>) = fromJson(JSONObject(json))

        fun fromJson(json: JSONObject) = json.run {
            UserResponse(
                userId = getString("userId"),
                loginIds = getJSONArray("loginIds").toStringList(),
                name = stringOrEmptyAsNull("name"),
                picture = stringOrEmptyAsNull("picture"),
                email = stringOrEmptyAsNull("email"),
                verifiedEmail = optBoolean("verifiedEmail"),
                phone = stringOrEmptyAsNull("phone"),
                verifiedPhone = optBoolean("verifiedPhone"),
                createdTime = getLong("createdTime").secToMs(),
                customAttributes = optionalMap("customAttributes"),
            )
        }
    }
}

internal data class MaskedAddressServerResponse(
    val maskedEmail: String? = null,
    val maskedPhone: String? = null,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromJson(json: String, cookies: List<HttpCookie>) = JSONObject(json).run {
            MaskedAddressServerResponse(
                maskedEmail = stringOrEmptyAsNull("maskedEmail"),
                maskedPhone = stringOrEmptyAsNull("maskedPhone"),
            )
        }
    }
}

internal data class PasswordPolicyServerResponse(
    val minLength: Int,
    val lowercase: Boolean,
    val uppercase: Boolean,
    val number: Boolean,
    val nonAlphanumeric: Boolean,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromJson(json: String, cookies: List<HttpCookie>) = JSONObject(json).run {
            PasswordPolicyServerResponse(
                minLength = getInt("minLength"),
                lowercase = optBoolean("lowercase"),
                uppercase = optBoolean("uppercase"),
                number = optBoolean("number"),
                nonAlphanumeric = optBoolean("nonAlphanumeric"),
            )
        }
    }
}

internal data class EnchantedLinkServerResponse(
    val linkId: String,
    val pendingRef: String,
    val maskedEmail: String,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromJson(json: String, cookies: List<HttpCookie>) = JSONObject(json).run {
            EnchantedLinkServerResponse(
                linkId = getString("linkId"),
                pendingRef = getString("pendingRef"),
                maskedEmail = getString("maskedEmail"),
            )
        }
    }
}

internal data class TotpServerResponse(
    val provisioningUrl: String,
    val image: ByteArray,
    val key: String,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromJson(json: String, cookies: List<HttpCookie>) = JSONObject(json).run {
            TotpServerResponse(
                provisioningUrl = getString("provisioningUrl"),
                image = getString("image").toByteArray(),
                key = getString("key"),
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TotpServerResponse

        if (provisioningUrl != other.provisioningUrl) return false
        if (!image.contentEquals(other.image)) return false
        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        var result = provisioningUrl.hashCode()
        result = 31 * result + image.contentHashCode()
        result = 31 * result + key.hashCode()
        return result
    }
}

internal data class OAuthServerResponse(
    val url: String,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromJson(json: String, cookies: List<HttpCookie>) = JSONObject(json).run {
            OAuthServerResponse(
                url = getString("url")
            )
        }
    }
}

internal data class SsoServerResponse(
    val url: String,
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromJson(json: String, cookies: List<HttpCookie>) = JSONObject(json).run {
            SsoServerResponse(
                url = getString("url")
            )
        }
    }
}
