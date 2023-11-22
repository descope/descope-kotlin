package com.descope.internal.routes

import android.net.Uri
import com.descope.internal.http.DescopeClient
import com.descope.internal.http.JwtServerResponse
import com.descope.internal.http.MaskedAddressServerResponse
import com.descope.internal.http.UserResponse
import com.descope.internal.others.with
import com.descope.sdk.DescopeLogger
import com.descope.session.Token
import com.descope.types.AuthenticationResponse
import com.descope.types.DeliveryMethod
import com.descope.types.DescopeException
import com.descope.types.DescopeUser
import com.descope.types.RefreshResponse
import com.descope.types.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

internal interface Route {
    val client: DescopeClient
    
    fun log(level: DescopeLogger.Level, message: String, vararg values: Any) {
        client.config.logger?.log(level, message, *values)
    }
}

internal fun UserResponse.convert(): DescopeUser = DescopeUser(
    userId = userId,
    loginIds = loginIds,
    createdAt = createdTime,
    name = name,
    picture = picture?.run { Uri.parse(this) },
    email = email,
    isVerifiedEmail = verifiedEmail,
    phone = phone,
    isVerifiedPhone = verifiedPhone,
    customAttributes = customAttributes,
)

internal fun JwtServerResponse.convert(): AuthenticationResponse {
    val sessionJwt = sessionJwt ?: throw DescopeException.decodeError.with(message = "Missing session JWT")
    val refreshJwt = refreshJwt ?: throw DescopeException.decodeError.with(message = "Missing refresh JWT")
    val user = user ?: throw DescopeException.decodeError.with(message = "Missing user details")
    return AuthenticationResponse(
        refreshToken = Token(refreshJwt),
        sessionToken = Token(sessionJwt),
        user = user.convert(),
        isFirstAuthentication = firstSeen,
    )
}

internal fun JwtServerResponse.toRefreshResponse(): RefreshResponse {
    val sessionJwt = sessionJwt ?: throw DescopeException.decodeError.with(message = "Missing session JWT")
    return RefreshResponse(
        refreshToken = refreshJwt?.run { Token(this) },
        sessionToken = Token(sessionJwt),
    )
}

internal fun MaskedAddressServerResponse.convert(method: DeliveryMethod) = when (method) {
    DeliveryMethod.Email -> maskedEmail ?: throw DescopeException.decodeError.with(message = "masked email not received")
    DeliveryMethod.Sms, DeliveryMethod.Whatsapp -> maskedPhone ?: throw DescopeException.decodeError.with(message = "masked phone not received")
}

@Suppress("OPT_IN_USAGE")
internal fun <T> wrapCoroutine(callback: (Result<T>) -> Unit, coroutine: suspend () -> T) {
    GlobalScope.launch(Dispatchers.Main) {
        val result = try {
            val result = coroutine()
            Result.Success(result)
        } catch (e: Exception) {
            Result.Failure(e)
        }
        callback(result)
    }
}
