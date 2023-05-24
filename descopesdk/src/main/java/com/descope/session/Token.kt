package com.descope.session

import androidx.annotation.VisibleForTesting
import com.descope.internal.others.secToMs
import com.descope.internal.others.tryOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A `DescopeToken` is a utility wrapper around a single JWT value.
 *
 * The session and refresh JWTs in a [DescopeSession] are stored as
 * instances of [DescopeToken]. It's also returned directly when
 * exchanging an access key for a session JWT.
 */
interface DescopeToken {
    /** The underlying JWT value */
    val jwt: String

    /**
     * The value of the "sub" (subject) claim, which is the unique id
     * of the user or access key the JWT was generated for.
     */
    val id: String

    /**
     * The value of the "aud" (audience) claim which is the unique id
     * of the Descope project the JWT was generated for.
     */
    val projectId: String

    /**
     * The value of the "exp" (expiration time) claim which is the time
     * after which the JWT expires.
     */
    val expiresAt: Long?

    /** Whether the JWT expiry time (if any) has already passed. */
    val isExpired: Boolean

    /**
     * A map with all the custom claims in the JWT value. It includes
     * any claims whose values aren't already exposed by other accessors
     * or authorization functions.
     */
    val claims: Map<String, Any>

    /**
     * Returns the list of permissions granted in the JWT claims. Pass
     * a value of `nil` for the `tenant` parameter if the project
     * doesn't use multiple tenants.
     */
    fun permissions(tenant: String?): List<String>

    /**
     * Returns the list of roles granted in the JWT claims. Pass
     * a value of `nil` for the `tenant` parameter if the project
     * doesn't use multiple tenants.
     */
    fun roles(tenant: String?): List<String>
}

// Internal

internal class Token(
    override val jwt: String,
) : DescopeToken {

    override val id: String
    override val projectId: String
    override val expiresAt: Long?
    override val isExpired: Boolean
        get() = expiresAt?.run { this <= System.currentTimeMillis() } ?: false
    override val claims: Map<String, Any>

    private val allClaims: Map<String, Any>

    init {
        try {
            val map = decodeJwt(jwt)
            id = getClaim(Claim.Subject, map)
            projectId = decodeIssuer(getClaim(Claim.Issuer, map))
            expiresAt = tryOrNull { getClaim<Int>(Claim.Expiration, map).toLong().secToMs() }
            claims = map.filter { Claim.isCustom(it.key) }
            allClaims = map
        } catch (e: Exception) {
            throw IllegalArgumentException("Error decoding token", e) // TODO: replace with DescopeError
        }
    }

    override fun permissions(tenant: String?): List<String> = try {
        authorizationItems(tenant, Claim.Permissions)
    } catch (e: Exception) {
        emptyList()
    }

    override fun roles(tenant: String?): List<String> = try {
        authorizationItems(tenant, Claim.Roles)
    } catch (e: Exception) {
        emptyList()
    }

    private fun authorizationItems(tenant: String?, claim: Claim): List<String> =
        if (tenant != null) {
            try {
                getValueForTenant(tenant, claim.key)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            getClaim(claim, allClaims)
        }

    private inline fun <reified T> getValueForTenant(tenant: String, key: String): T {
        val foundTenant = getTenants()[tenant]
            ?: throw IllegalArgumentException("Missing tenant") // TODO: replace with DescopeError
        if (foundTenant is Map<*, *>) {
            foundTenant[key]?.run {
                if (this is T) return this
            }
        }
        throw IllegalArgumentException("Invalid tenant") // TODO: replace with DescopeError
    }

    private fun getTenants(): Map<String, Any> = getClaim(Claim.Tenants, allClaims)
}

// Claims

private enum class Claim(val key: String) {
    Audience("aud"),
    Subject("sub"),
    Issuer("iss"),
    IssuedAt("iat"),
    Expiration("exp"),
    Tenants("tenants"),
    Permissions("permissions"),
    Roles("roles");

    companion object {
        fun isCustom(name: String): Boolean = Claim.values().find { it.key == name } == null
    }
}

private inline fun <reified T> getClaim(claim: Claim, map: Map<String, Any>): T =
    getClaim(claim.key, map)

private inline fun <reified T> getClaim(claim: String, map: Map<String, Any>): T {
    val obj = map[claim] ?: throw IllegalArgumentException("Missing claim") // TODO: replace with DescopeError
    return obj as? T ?: throw IllegalArgumentException("Invalid claim") // TODO: replace with DescopeError
}

// JWT Decoding

@OptIn(ExperimentalEncodingApi::class)
private fun decodeFragment(string: String): Map<String, Any> {
    val data = Base64.UrlSafe.decode(string)
    val json = JSONObject(String(data))
    return json.toMap()
}

@VisibleForTesting
fun decodeJwt(jwt: String): Map<String, Any> {
    val fragments = jwt.split(".")
    if (fragments.size != 3) throw IllegalArgumentException("Invalid format") // TODO: replace with DescopeError
    return decodeFragment(fragments[1])
}

private fun decodeIssuer(issuer: String): String =
    issuer.split("/").lastOrNull()
        ?: throw IllegalArgumentException("Invalid format") // TODO: replace with DescopeError

// JSON Transformations

private fun JSONObject.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        map[key] = when (val value = get(key)) {
            is JSONArray -> value.toList()
            is JSONObject -> value.toMap()
            else -> value
        }
    }
    return map
}

private fun JSONArray.toList(): List<Any> {
    val list = mutableListOf<Any>()
    for (i in 0 until this.length()) {
        val value = when (val value = this[i]) {
            is JSONArray -> value.toList()
            is JSONObject -> value.toMap()
            else -> value
        }
        list.add(value)
    }
    return list
}
