package com.descope.internal.http

import com.descope.sdk.DescopeConfig
import com.descope.types.DeliveryMethod
import com.descope.types.OAuthProvider
import com.descope.types.SignUpDetails

internal class DescopeClient(private val config: DescopeConfig) : HttpClient(config.baseUrl) {

    // OTP

    suspend fun otpSignUp(method: DeliveryMethod, loginId: String, details: SignUpDetails?): MaskedAddressServerResponse = post(
        route = "auth/otp/signup/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "user" to details?.toMap(),
        ),
    )

    suspend fun otpSignIn(method: DeliveryMethod, loginId: String): MaskedAddressServerResponse = post(
        route = "auth/otp/signin/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
        ),
    )

    suspend fun otpSignUpIn(method: DeliveryMethod, loginId: String): MaskedAddressServerResponse = post(
        route = "auth/otp/signup-in/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
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

    suspend fun totpVerify(loginId: String, code: String): JwtServerResponse = post(
        route = "auth/totp/verify",
        decoder = JwtServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "code" to code,
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

    suspend fun magicLinkSignIn(method: DeliveryMethod, loginId: String, uri: String?): MaskedAddressServerResponse = post(
        route = "auth/magiclink/signin/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "uri" to uri,
        ),
    )

    suspend fun magicLinkSignUpOrIn(method: DeliveryMethod, loginId: String, uri: String?): MaskedAddressServerResponse = post(
        route = "auth/magiclink/signup-in/${method.route()}",
        decoder = MaskedAddressServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "uri" to uri,
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

    suspend fun magicLinkUpdatePhone(phone: String, method: DeliveryMethod, loginId: String, uri: String? = null, refreshJwt: String): MaskedAddressServerResponse = post(
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

    suspend fun enchantedLinkSignUp(loginId: String, details: SignUpDetails? = null, uri: String? = null): EnchantedLinkServerResponse = post(
        route = "auth/enchantedlink/signup/email",
        decoder = EnchantedLinkServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "user" to details?.toMap(),
            "uri" to uri,
        ),
    )

    suspend fun enchantedLinkSignIn(loginId: String, uri: String? = null): EnchantedLinkServerResponse = post(
        route = "auth/enchantedlink/signin/email",
        decoder = EnchantedLinkServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "uri" to uri,
        ),
    )

    suspend fun enchantedLinkSignUpOrIn(loginId: String, uri: String? = null): EnchantedLinkServerResponse = post(
        route = "auth/enchantedlink/signup-in/email",
        decoder = EnchantedLinkServerResponse::fromJson,
        body = mapOf(
            "loginId" to loginId,
            "uri" to uri,
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

    suspend fun oauthStart(provider: OAuthProvider, redirectUrl: String?): OAuthServerResponse = post(
        route="auth/oauth/authorize",
        decoder = OAuthServerResponse::fromJson,
        params = mapOf(
            "provider" to provider.name,
            "redirectURL" to redirectUrl,
        ),
    )

    suspend fun oauthExchange(code: String): JwtServerResponse = post(
        route="auth/oauth/exchange",
        decoder = JwtServerResponse::fromJson,
        body = mapOf(
            "code" to code,
        ),
    )

    // SSO

    suspend fun ssoStart(emailOrTenantId: String, redirectUrl: String?): SsoServerResponse = post(
        route="auth/saml/authorize",
        decoder = SsoServerResponse::fromJson,
        params = mapOf(
            "tenant" to emailOrTenantId,
            "redirectURL" to redirectUrl,
        ),
    )

    suspend fun ssoExchange(code: String): JwtServerResponse = post(
        route="auth/saml/exchange",
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
        "x-descope-sdk-version" to "0.1.0",
    )

    // Internal

    private fun authorization(value: String) = mapOf(
        "Authorization" to "Bearer ${config.projectId}:$value"
    )
}

// Extensions

private fun SignUpDetails.toMap() = mapOf(
    "email" to email,
    "phone" to phone,
    "name" to name,
)

private fun DeliveryMethod.route() = this.name.lowercase()

// Utilities

private val emptyResponse: (String) -> Unit = {}
