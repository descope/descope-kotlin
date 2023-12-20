package com.descope.internal.http

import com.descope.sdk.DescopeConfig
import com.descope.sdk.DescopeSdk
import com.descope.types.DeliveryMethod
import com.descope.types.OAuthProvider
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import java.net.HttpCookie

internal class DescopeClient(internal val config: DescopeConfig) : HttpClient(config.baseUrl, config.logger) {

    // OTP

    suspend fun otpSignUp(method: DeliveryMethod, loginId: String, details: SignUpDetails?): MaskedAddressServerResponse = post(
        route = "auth/otp/signup/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "user" to details?.toMap(),
        ),
    )

    suspend fun otpSignIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>?): MaskedAddressServerResponse = post(
        route = "auth/otp/signin/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        headers = authorization(options?.refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "loginOptions" to options?.toMap(),
        ),
    )

    suspend fun otpSignUpIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>?): MaskedAddressServerResponse = post(
        route = "auth/otp/signup-in/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        headers = authorization(options?.refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "loginOptions" to options?.toMap(),
        ),
    )

    suspend fun otpVerify(method: DeliveryMethod, loginId: String, code: String): JwtServerResponse = post(
        route = "auth/otp/verify/${method.route()}",
        decoder = JwtServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "code" to code,
        ),
    )

    suspend fun otpUpdateEmail(email: String, loginId: String, refreshJwt: String): MaskedAddressServerResponse = post(
        route = "auth/otp/update/email",
        decoder = MaskedAddressServerResponse::fromJson,
        headers = authorization(refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "email" to email,
        ),
    )

    suspend fun otpUpdatePhone(phone: String, method: DeliveryMethod, loginId: String, refreshJwt: String): MaskedAddressServerResponse = post(
        route = "auth/otp/update/phone/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        headers = authorization(refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "phone" to phone,
        ),
    )

    // TOTP

    suspend fun totpSignUp(loginId: String, details: SignUpDetails?): TotpServerResponse = post(
        route = "auth/totp/signup",
        decoder = TotpServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "user" to details?.toMap(),
        ),
    )

    suspend fun totpUpdate(loginId: String, refreshJwt: String): TotpServerResponse = post(
        route = "auth/totp/update",
        decoder = TotpServerResponse::fromJson,
        headers = authorization(refreshJwt),
        body = mapOf(
            "loginId" to loginId,
        ),
    )

    suspend fun totpVerify(loginId: String, code: String, options: List<SignInOptions>?): JwtServerResponse = post(
        route = "auth/totp/verify",
        decoder = JwtServerResponse::fromJson,
        headers = authorization(options?.refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "code" to code,
            "loginOptions" to options?.toMap(),
        ),
    )

    // Password

    suspend fun passwordSignUp(loginId: String, password: String, details: SignUpDetails?): JwtServerResponse = post(
        route = "auth/password/signup",
        decoder = JwtServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "password" to password,
            "user" to details?.toMap(),
        ),
    )

    suspend fun passwordSignIn(loginId: String, password: String): JwtServerResponse = post(
        route = "auth/password/signin",
        decoder = JwtServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "password" to password,
        ),
    )

    suspend fun passwordUpdate(loginId: String, newPassword: String, refreshJwt: String) = post(
        route = "auth/password/update",
        decoder = emptyResponse,
        headers = authorization(refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "newPassword" to newPassword,
        ),
    )

    suspend fun passwordReplace(loginId: String, oldPassword: String, newPassword: String) = post(
        route = "auth/password/replace",
        decoder = JwtServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "oldPassword" to oldPassword,
            "newPassword" to newPassword,
        ),
    )

    suspend fun passwordSendReset(loginId: String, redirectUrl: String?) = post(
        route = "auth/password/reset",
        decoder = emptyResponse,
        body = mapOf(
            "loginId" to loginId,
            "redirectUrl" to redirectUrl,
        ),
    )

    suspend fun passwordGetPolicy(): PasswordPolicyServerResponse = get(
        route = "auth/password/reset",
        decoder = PasswordPolicyServerResponse::fromJson,
    )

    // Magic Link

    suspend fun magicLinkSignUp(method: DeliveryMethod, loginId: String, details: SignUpDetails?, uri: String?): MaskedAddressServerResponse = post(
        route = "auth/magiclink/signup/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "user" to details?.toMap(),
            "uri" to uri,
        ),
    )

    suspend fun magicLinkSignIn(method: DeliveryMethod, loginId: String, uri: String?, options: List<SignInOptions>?): MaskedAddressServerResponse = post(
        route = "auth/magiclink/signin/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        headers = authorization(options?.refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "uri" to uri,
            "loginOptions" to options?.toMap(),
        ),
    )

    suspend fun magicLinkSignUpOrIn(method: DeliveryMethod, loginId: String, uri: String?, options: List<SignInOptions>?): MaskedAddressServerResponse = post(
        route = "auth/magiclink/signup-in/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        headers = authorization(options?.refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "uri" to uri,
            "loginOptions" to options?.toMap(),
        ),
    )

    suspend fun magicLinkUpdateEmail(email: String, loginId: String, uri: String, refreshJwt: String): MaskedAddressServerResponse = post(
        route = "auth/magiclink/update/email",
        decoder = MaskedAddressServerResponse::fromJson,
        headers = authorization(refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "email" to email,
            "uri" to uri,
        ),
    )

    suspend fun magicLinkUpdatePhone(phone: String, method: DeliveryMethod, loginId: String, uri: String?, refreshJwt: String): MaskedAddressServerResponse = post(
        route = "auth/magiclink/update/phone/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        headers = authorization(refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "phone" to phone,
            "uri" to uri,
        ),
    )

    suspend fun magicLinkVerify(token: String): JwtServerResponse = post(
        route = "auth/magiclink/verify",
        decoder = JwtServerResponse::fromJson,
        body = mapOf(
            "token" to token,
        ),
    )

    // Enchanted Link

    suspend fun enchantedLinkSignUp(loginId: String, details: SignUpDetails?, uri: String?): EnchantedLinkServerResponse = post(
        route = "auth/enchantedlink/signup/email",
        decoder = EnchantedLinkServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "user" to details?.toMap(),
            "uri" to uri,
        ),
    )

    suspend fun enchantedLinkSignIn(loginId: String, uri: String?, options: List<SignInOptions>?): EnchantedLinkServerResponse = post(
        route = "auth/enchantedlink/signin/email",
        decoder = EnchantedLinkServerResponse::fromJson,
        headers = authorization(options?.refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "uri" to uri,
            "loginOptions" to options?.toMap(),
        ),
    )

    suspend fun enchantedLinkSignUpOrIn(loginId: String, uri: String?, options: List<SignInOptions>?): EnchantedLinkServerResponse = post(
        route = "auth/enchantedlink/signup-in/email",
        decoder = EnchantedLinkServerResponse::fromJson,
        headers = authorization(options?.refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "uri" to uri,
            "loginOptions" to options?.toMap(),
        ),
    )

    suspend fun enchantedLinkUpdateEmail(email: String, loginId: String, uri: String?, refreshJwt: String): EnchantedLinkServerResponse = post(
        route = "auth/enchantedlink/update/email",
        decoder = EnchantedLinkServerResponse::fromJson,
        headers = authorization(refreshJwt),
        body = mapOf(
            "loginId" to loginId,
            "email" to email,
            "uri" to uri,
        ),
    )

    suspend fun enchantedLinkCheckForSession(pendingRef: String): JwtServerResponse = post(
        route = "auth/enchantedlink/pending-session",
        decoder = JwtServerResponse::fromJson,
        body = mapOf(
            "pendingRef" to pendingRef,
        ),
    )

    // OAuth

    suspend fun oauthStart(provider: OAuthProvider, redirectUrl: String?, options: List<SignInOptions>?): OAuthServerResponse = post(
        route = "auth/oauth/authorize",
        decoder = OAuthServerResponse::fromJson,
        headers = authorization(options?.refreshJwt),
        params = mapOf(
            "provider" to provider.name,
            "redirectURL" to redirectUrl,
        ),
        body = mapOf(
            "loginOptions" to options?.toMap(),
        ),
    )

    suspend fun oauthExchange(code: String): JwtServerResponse = post(
        route = "auth/oauth/exchange",
        decoder = JwtServerResponse::fromJson,
        body = mapOf(
            "code" to code,
        ),
    )

    // SSO

    suspend fun ssoStart(emailOrTenantId: String, redirectUrl: String?, options: List<SignInOptions>?): SsoServerResponse = post(
        route = "auth/saml/authorize",
        decoder = SsoServerResponse::fromJson,
        headers = authorization(options?.refreshJwt),
        params = mapOf(
            "tenant" to emailOrTenantId,
            "redirectURL" to redirectUrl,
        ),
        body = mapOf(
            "loginOptions" to options?.toMap(),
        ),
    )

    suspend fun ssoExchange(code: String): JwtServerResponse = post(
        route = "auth/saml/exchange",
        decoder = JwtServerResponse::fromJson,
        body = mapOf(
            "code" to code,
        ),
    )

    // Flow

    suspend fun flowExchange(authorizationCode: String, codeVerifier: String): JwtServerResponse = post(
        route = "flow/exchange",
        decoder = JwtServerResponse::fromJson,
        body = mapOf(
            "authorizationCode" to authorizationCode,
            "codeVerifier" to codeVerifier,
        ),
    )

    // Others

    suspend fun me(refreshJwt: String): UserResponse = get(
        route = "auth/me",
        decoder = UserResponse::fromJson,
        headers = authorization(refreshJwt),
    )

    suspend fun refresh(refreshJwt: String): JwtServerResponse = post(
        route = "auth/refresh",
        decoder = JwtServerResponse::fromJson,
        headers = authorization(refreshJwt),
    )

    suspend fun logout(refreshJwt: String) = post(
        route = "auth/logout",
        decoder = emptyResponse,
        headers = authorization(refreshJwt),
    )

    // Overrides

    override val basePath = "/v1/"

    override val defaultHeaders: Map<String, String> = mapOf(
        "Authorization" to "Bearer ${config.projectId}",
        "x-descope-sdk-name" to "android",
        "x-descope-sdk-version" to DescopeSdk.version,
    )

    override fun exceptionFromResponse(response: String): Exception? = parseServerError(response)

    // Internal

    private fun authorization(value: String?) =
        if (value != null) mapOf("Authorization" to "Bearer ${config.projectId}:$value")
        else emptyMap()
}

// Extensions

private fun SignUpDetails.toMap() = mapOf(
    "email" to email,
    "phone" to phone,
    "name" to name,
    "givenName" to givenName,
    "middleName" to middleName,
    "familyName" to familyName,
)

private val List<SignInOptions>.refreshJwt: String?
    get() {
        forEach {
            if (it is SignInOptions.Mfa) return it.refreshJwt
            if (it is SignInOptions.StepUp) return it.refreshJwt
        }
        return null
    }

private fun List<SignInOptions>.toMap(): Map<String, Any> {
    val map = mutableMapOf<String, Any>()
    forEach {
        when (it) {
            is SignInOptions.CustomClaims -> map["customClaims"] = it.claims
            is SignInOptions.Mfa -> map["mfa"] = true
            is SignInOptions.StepUp -> map["stepup"] = true
        }
    }
    return map.toMap()
}

private fun DeliveryMethod.route() = this.name.lowercase()

// Utilities

private val emptyResponse: (String, List<HttpCookie>) -> Unit = { _, _ -> }
