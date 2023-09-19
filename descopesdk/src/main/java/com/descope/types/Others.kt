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
 */
data class SignUpDetails (
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
)
