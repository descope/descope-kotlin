package com.descope.internal.routes

import android.net.Uri
import com.descope.internal.http.JwtServerResponse
import com.descope.internal.http.MaskedAddressServerResponse
import com.descope.internal.http.UserResponse
import com.descope.session.Token
import com.descope.types.AuthenticationResponse
import com.descope.types.DeliveryMethod
import com.descope.types.DescopeUser
import com.descope.types.RefreshResponse
import com.descope.types.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
)

internal fun JwtServerResponse.convert(): AuthenticationResponse {
    val refreshJwt = refreshJwt ?: throw Exception("Missing refresh JWT") // TODO replace with DescopeError
    val user = user ?: throw Exception("Missing user details") // TODO replace with DescopeError
    return AuthenticationResponse(
        refreshToken = Token(refreshJwt),
        sessionToken = Token(sessionJwt),
        user = user.convert(),
        isFirstAuthentication = firstSeen,
    )
}

internal fun JwtServerResponse.toRefreshResponse(): RefreshResponse = RefreshResponse(
    refreshToken = refreshJwt?.run { Token(this) },
    sessionToken = Token(sessionJwt),
)

internal fun MaskedAddressServerResponse.convert(method: DeliveryMethod) = when (method) {
    DeliveryMethod.Email -> maskedEmail ?: throw Exception("masked email not received")
    DeliveryMethod.Sms, DeliveryMethod.Whatsapp -> maskedPhone ?: throw Exception("masked phone not received")
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
