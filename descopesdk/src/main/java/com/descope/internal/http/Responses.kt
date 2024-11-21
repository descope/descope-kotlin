package com.descope.internal.http

import com.descope.internal.others.optionalMap
import com.descope.internal.others.secToMs
import com.descope.internal.others.stringOrEmptyAsNull
import com.descope.internal.others.toObjectList
import com.descope.internal.others.toStringList
import org.json.JSONObject
import java.net.HttpCookie

internal const val SESSION_COOKIE_NAME = "DS"
internal const val REFRESH_COOKIE_NAME = "DSR"

internal data class JwtServerResponse(
    var sessionJwt: String?,
    var refreshJwt: String?,
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
    val givenName: String?,
    val middleName: String?,
    val familyName: String?,
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
                givenName = stringOrEmptyAsNull("givenName"),
                middleName = stringOrEmptyAsNull("middleName"),
                familyName = stringOrEmptyAsNull("familyName"),
            )
        }
    }
}

internal data class TenantsResponse(
    val tenants: List<Tenant>,
) {

    data class Tenant(
        val tenantId: String,
        val name: String,
        val customAttributes: Map<String, Any>,
    )

    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromJson(json: String, cookies: List<HttpCookie>) = JSONObject(json).run {
            TenantsResponse(tenants = getJSONArray("tenants").toObjectList().map {
                Tenant(
                    tenantId = it.getString("id"),
                    name = it.getString("name"),
                    customAttributes = it.optionalMap("customAttributes"),
                )
            })
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

@Suppress("ArrayInDataClass")
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

internal data class OAuthNativeStartServerResponse(
    var clientId: String,
    var stateId: String,
    var nonce: String?,
    var implicit: Boolean
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromJson(json: String, cookies: List<HttpCookie>) = fromJsonObject(JSONObject(json))

        fun fromJsonObject(json: JSONObject) = json.run {
            OAuthNativeStartServerResponse(
                clientId = getString("clientId"),
                stateId = getString("stateId"),
                nonce = stringOrEmptyAsNull("nonce"),
                implicit = getBoolean("implicit"),
            )
        }
    }
}

internal data class PasskeyStartServerResponse(
    var transactionId: String,
    var options: String,
    var create: Boolean
) {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        fun fromJson(json: String, cookies: List<HttpCookie>) = JSONObject(json).run {
            PasskeyStartServerResponse(
                transactionId = getString("transactionId"),
                options = getString("options"),
                create = getBoolean("create"),
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
