package com.descope.types

import com.descope.session.DescopeSession

private const val API_DESC = "Descope API error"

/**
 * The concrete type of [Exception] thrown by all operations in the Descope SDK.
 *
 * There are several ways to catch and handle a [DescopeException] thrown by a Descope SDK
 * operation, and you can use whichever one is more appropriate in each specific use case.
 *
 *     try {
 *         val authResponse = Descope.otp.verify(DeliveryMethod.Email, "andy@example.com", "123456")
 *         showLoginSuccess(with: authResponse)
 *     } catch (e: DescopeException) {
 *         when(e) {
 *             // handle one or more kinds of errors where we don't
 *             // need to use the actual error object
 *             DescopeException.wrongOtpCode,
 *               DescopeException.invalidRequest -> showBadCodeAlert()
 *             
 *             // handle a specific kind of error and do something
 *             // with the [DescopeException] object
 *             DescopeException.networkError -> {
 *                 logError("A network error has occurred", e.desc, e.cause)
 *                 showNetworkErrorRetry()
 *             }
 *             
 *             // handle any other scenario
 *             else -> {
 *                 logError("Unexpected authentication failure: $e")
 *                 showUnexpectedErrorAlert(e)
 *             }
 *         }
 *     } 
 *
 * See the [DescopeException] companion object for specific error values. Note that not all API errors
 * are listed in the SDK yet. Please let us know via a Github issue or pull request if you
 * need us to add any entries to make your code simpler.
 *
 * @property code A string of 7 characters that represents a specific Descope error.
 * For example, the value of [code] is `"E011003"` when an API request fails validation.
 * @property desc A short description of the error message.
 * For example, the value of [desc] is `"Request is invalid"` when an API request fails validation.
 * @property message An optional message with more details about the error.
 * For example, the value of [message] might be `"The email field is required"` when
 * attempting to authenticate via enchanted link with an empty email address.
 * @property cause An optional underlying [Throwable] that caused this error.
 * For example, when a [DescopeException.networkError] is caught the [cause] property
 * will usually have the [Exception] object thrown by the internal `HttpsURLConnection` call.
 */
class DescopeException(
    val code: String,
    val desc: String,
    override val message: String? = null,
    override val cause: Throwable? = null,
) : Exception(), Comparable<DescopeException> {
    
    override fun compareTo(other: DescopeException): Int {
        return code.compareTo(other.code)
    }
    
    override fun equals(other: Any?): Boolean {
        val exception = other as? DescopeException ?: return false
        return code == exception.code
    }

    override fun hashCode(): Int {
        var result = code.hashCode()
        result = 31 * result + desc.hashCode()
        return result
    }

    override fun toString(): String {
        var str = """DescopeError(code: "$code", description: "$desc""""
        message?.run {
            str += """, message: "$this""""
        }
        cause?.run {
            str += ", cause: {$this}"
        }
        str += ")"
        return str
    }

    companion object {
        /**
         * Thrown when a call to the Descope API fails due to a network error.
         *
         * You can catch this kind of error to handle error cases such as the user being
         * offline or the network request timing out.
         */
        val networkError = DescopeException(code = "K010001", desc = "Network error")
        val browserError = DescopeException(code = "K010002", desc = "Unable to launch browser")

        val badRequest = DescopeException(code = "E011001", desc = API_DESC)
        val missingArguments = DescopeException(code = "E011002", desc = API_DESC)
        val invalidRequest = DescopeException(code = "E011003", desc = API_DESC)
        val invalidArguments = DescopeException(code = "E011004", desc = API_DESC)

        val wrongOtpCode = DescopeException(code = "E061102", desc = API_DESC)
        val tooManyOtpAttempts = DescopeException(code = "E061103", desc = API_DESC)

        val enchantedLinkPending = DescopeException(code = "E062503", desc = API_DESC)
        val enchantedLinkExpired = DescopeException(code = "K060001", desc = "Enchanted link expired")

        val flowFailed = DescopeException(code = "K100001", desc = "Flow failed to run")

        val passkeyFailed = DescopeException(code = "K110001", desc = "Passkey authentication failed")
        val passkeyCancelled = DescopeException(code = "K110002", desc = "Passkey authentication cancelled")
        val passkeyNoPasskeys = DescopeException(code = "K110003", desc = "No passkeys found")

        val oauthNativeFailed = DescopeException(code = "K120001", desc = "Sign in with Google failed")
        val oauthNativeCancelled = DescopeException(code = "K120002", desc = "Sign in with Google cancelled")
        
        val customTabFailed = DescopeException(code = "K130001", desc = "Custom Tab failed to open")

        // Internal

        // These errors are not expected to happen in common usage and there shouldn't be
        // a need to catch them specifically.

        /**
         * Thrown if a call to the Descope API fails in an unexpected manner.
         *
         * This should only be thrown when there's no error response body to parse or the body
         * isn't in the expected format. The value of [desc] is overwritten with a more specific
         * value when possible.
         */
        internal val httpError = DescopeException("K010002", "Server request failed")

        /** Thrown if a response from the Descope API can't be parsed for an unexpected reason. */
        internal val decodeError = DescopeException("K010003", "Failed to decode response")

        /** Thrown if a request to the Descope API fails to encode for an unexpected reason. */
        internal val encodeError = DescopeException("K010004", "Failed to encode request")

        /**
         * Thrown if a JWT string fails to decode.
         *
         * This might be thrown if the [DescopeSession] initializer is called with an invalid
         * `sessionJwt` or `refreshJwt` value.
         */
        internal val tokenError = DescopeException("K010005", "Failed to parse token")
    }
}

