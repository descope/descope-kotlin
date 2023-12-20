package com.descope.types

// Enums

/** The delivery method for an OTP or Magic Link message. */
enum class DeliveryMethod {
    Email,
    Sms,
    Whatsapp,
}

/** The provider to use in an OAuth flow. */
data class OAuthProvider(val name: String) {
    companion object {
        val Facebook = OAuthProvider("facebook")
        val Github = OAuthProvider("github")
        val Google = OAuthProvider("google")
        val Microsoft = OAuthProvider("microsoft")
        val Gitlab = OAuthProvider("gitlab")
        val Apple = OAuthProvider("apple")
        val Slack = OAuthProvider("slack")
        val Discord = OAuthProvider("discord")
    }
}

// Classes

/**
 * Used to provide additional details about a user in sign up calls.
 *
 * @property name the user's name
 * @property email the user's email
 * @property phone the user's phone
 * @property givenName the user's given name
 * @property middleName the user's middle name
 * @property familyName the user's family name
 */
data class SignUpDetails(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val givenName: String? = null,
    val middleName: String? = null,
    val familyName: String? = null,
)

/**
 * Used to require additional behaviors when authenticating a user.
 */
sealed class SignInOptions {
    /**
     * Adds additional custom claims to the user's JWT during authentication.
     *
     * For example, the following code starts an OTP sign in and requests a custom claim
     * with the authenticated user's full name:
     *
     *     val claims = mapOf("cc1" to "yes", "cc2" to true)
     *     val options = listOf(SignInOptions.CustomClaims(claims))
     *     Descope.otp.signUp(method = DeliveryMethod.Email, loginId = "andy@example.com", options = options)
     *
     * - Important: Any custom claims added via this method are considered insecure and will
     * be nested under the `nsec` custom claim.
     */
    class CustomClaims(val claims: Map<String, Any>) : SignInOptions()

    /** 
     * Used to add layered security to your app by implementing Step-up authentication.
     *
     *     val session = Descope.sessionManager.session ?: return
     *     val options = listOf(SignInOptions.StepUp(session.refreshJwt))
     *     Descope.otp.signUp(method = DeliveryMethod.Email, loginId = "andy@example.com", options = options)
     *
     * After the Step-up authentication completes successfully the returned session JWT will
     * have an `su` claim with a value of `true`.
     *
     * - Note: The `su` claim is not set on the refresh JWT.
     */
    class StepUp(val refreshJwt: String) : SignInOptions()

    /** 
     * Used to add layered security to your app by implementing Multi-factor authentication.
     *
     * Assuming the user has already signed in successfully with one authentication method,
     * we can take the `refreshJwt` and pass it as an `mfa` option to another authentication
     * method.
     *
     *     val session = Descope.sessionManager.session ?: return
     *     val options = listOf(SignInOptions.Mfa(session.refreshJwt))
     *     Descope.otp.signUp(method = DeliveryMethod.Email, loginId = "andy@example.com", options = options)
     *
     * After the MFA authentication completes successfully the `amr` claim in both the session
     * and refresh JWTs will be an array with an entry for each authentication method used.
     */ 
    class Mfa(val refreshJwt: String) : SignInOptions()
}
