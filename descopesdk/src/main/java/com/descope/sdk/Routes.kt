package com.descope.sdk

import android.content.Context
import android.net.Uri
import com.descope.session.DescopeSession
import com.descope.types.AuthenticationResponse
import com.descope.types.DeliveryMethod
import com.descope.types.DescopeUser
import com.descope.types.EnchantedLinkResponse
import com.descope.types.OAuthProvider
import com.descope.types.PasswordPolicy
import com.descope.types.RefreshResponse
import com.descope.types.Result
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.descope.types.TotpResponse
import com.descope.types.UpdateOptions


/**
 * General authentication functions
 * */
interface DescopeAuth {
    /**
     *  Returns details about the user.
     *
     * The user must have an active [DescopeSession] whose [refreshJwt] should be
     * passed as a parameter to this function.
     *
     * @param refreshJwt the refreshJwt from an active [DescopeSession].
     * @return a [DescopeUser] containing details about the user.
     */
    suspend fun me(refreshJwt: String): DescopeUser

    /** @see me */
    fun me(refreshJwt: String, callback: (Result<DescopeUser>) -> Unit)

    /**
     * Refreshes a [DescopeSession].
     *
     * This can be called at any time as long as the [refreshJwt] is still
     * valid. Typically called when a `DescopeSession.sessionJwt` is expired
     * or is about expire.
     *
     * @param refreshJwt the refreshJwt from an active [DescopeSession].
     * @return a [RefreshResponse] with a refreshed `sessionJwt`.
     */
    suspend fun refreshSession(refreshJwt: String): RefreshResponse

    /** @see refreshSession */
    suspend fun refreshSession(refreshJwt: String, callback: (Result<RefreshResponse>) -> Unit)

    /**
     * Logs out from an active [DescopeSession].
     *
     * @param refreshJwt the refreshJwt from an active [DescopeSession].
     */
    suspend fun logout(refreshJwt: String)

    /** @see logout */
    suspend fun logout(refreshJwt: String, callback: (Result<Unit>) -> Unit)
}

/**
 * Authenticate users using a one time password (OTP) code, sent via
 * a delivery method of choice. The code then needs to be verified using
 * the [verify] function. It is also possible to add an email or phone to
 * an existing user after validating it via OTP.
 */
interface DescopeOtp {
    /**
     * Authenticates a new user using an OTP
     *
     * - **Important:** Make sure the delivery information corresponding with
     * the delivery [method] is given either in the optional [details] parameter or as
     * the [loginId] itself, i.e., the email address, phone number, etc.
     *
     * @param method deliver the code using this method.
     * @param loginId the identifier of the user to authenticate.
     * @param details optional user information. Should contain the necessary delivery information if not provided in [loginId].
     * @return masked version of the delivery method used, i.e. a masked email address or phone number.
     */
    suspend fun signUp(method: DeliveryMethod, loginId: String, details: SignUpDetails? = null): String

    /** @see signUp */
    fun signUp(method: DeliveryMethod, loginId: String, details: SignUpDetails? = null, callback: (Result<String>) -> Unit)

    /**
     * Authenticates an existing user using an OTP
     *
     * - **Important:** Make sure the delivery information corresponding with
     * the delivery [method] already exists on the user trying to log in,
     * i.e., the email address, phone number, etc.
     *
     * @param method deliver the code using this method.
     * @param loginId the identifier of the user to authenticate.
     * @param options additional behaviors to perform during authentication.
     * @return masked version of the delivery method used, i.e. a masked email address or phone number.
     */
    suspend fun signIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>? = null): String

    /** @see signIn */
    fun signIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>? = null, callback: (Result<String>) -> Unit)

    /**
     * Authenticates an existing user if one exists, or creates a new user
     * using an OTP
     *
     * - **Important:** Make sure the delivery information corresponding with
     * the delivery [method] already exists on the user trying to log in,
     * i.e., the email address, phone number, etc.
     *
     * @param method deliver the code using this method.
     * @param loginId the identifier of the user to authenticate.
     * @param options additional behaviors to perform during authentication.
     * @return masked version of the delivery method used, i.e. a masked email address or phone number.
     */
    suspend fun signUpOrIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>? = null): String

    /** @see signUpOrIn*/
    fun signUpOrIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>? = null, callback: (Result<String>) -> Unit)

    /**
     * Verifies an OTP [code] sent to the user.
     *
     * @param method the method used to deliver the code.
     * @param loginId the identifier of the user to authenticate.
     * @param code the code to verify.
     * @return an [AuthenticationResponse] upon successful verification.
     */
    suspend fun verify(method: DeliveryMethod, loginId: String, code: String): AuthenticationResponse

    /** @see verify */
    fun verify(method: DeliveryMethod, loginId: String, code: String, callback: (Result<AuthenticationResponse>) -> Unit)

    /**
     * Updates an existing user by adding an email address.
     *
     * The [email] will be updated for the user identified by [loginId]
     * after it is verified via OTP. In order to do this,
     * the user must have an active [DescopeSession] whose [refreshJwt] should
     * be passed as a parameter to this function.
     *
     * @param email the email address to add to the user.
     * @param loginId the identifier of the user to authenticate.
     * @param refreshJwt the refreshJwt from an active [DescopeSession].
     * @param options Whether to add the new email address as a loginId for the updated user, and
     * in that case, if another user already has the same email address as a loginId how to
     * merge the two users. See the documentation for [UpdateOptions] for more details.
     * @return masked email address the OTP was sent to.
     */
    suspend fun updateEmail(email: String, loginId: String, refreshJwt: String, options: UpdateOptions? = null): String

    /** @see updateEmail */
    fun updateEmail(email: String, loginId: String, refreshJwt: String, options: UpdateOptions? = null, callback: (Result<String>) -> Unit)

    /**
     * Updates an existing user by adding a phone number.
     *
     * The [phone] number will be updated for the user identified by [loginId]
     * after it is verified via OTP. In order to do this,
     * the user must have an active [DescopeSession] whose [refreshJwt] should
     * be passed as a parameter to this function.
     *
     * - **Important:** Make sure delivery [method] is appropriate for using a phone number.
     *
     * @param phone the phone number to update.
     * @param method the delivery method to send the code through.
     * @param loginId the identifier of the user to authenticate.
     * @param refreshJwt the refreshJwt from an active [DescopeSession].
     * @param options Whether to add the new phone number as a loginId for the updated user, and
     * in that case, if another user already has the same phone number as a loginId how to
     * merge the two users. See the documentation for [UpdateOptions] for more details.
     * @return masked phone number the OTP was sent to.
     */
    suspend fun updatePhone(phone: String, method: DeliveryMethod, loginId: String, refreshJwt: String, options: UpdateOptions? = null): String

    /** @see updatePhone */
    fun updatePhone(phone: String, method: DeliveryMethod, loginId: String, refreshJwt: String, options: UpdateOptions? = null, callback: (Result<String>) -> Unit)
}

/**
 * Authenticate users using Timed One-time Passwords (TOTP) codes.
 *
 * This authentication method is geared towards using an authenticator app which
 * can produce TOTP codes.
 */
interface DescopeTotp {

    /**
     * Authenticates a new user using a TOTP.
     *
     * This function creates a new user identified by [loginId] and
     * the optional information provided on via the [details] object.
     * It returns a [TotpResponse.key] (seed) that allows
     * authenticator apps to generate TOTP codes. The same information
     * is returned in multiple formats.
     *
     * @param loginId the identifier of the user to authenticate.
     * @param details optional user information. Should contain the necessary delivery information if not provided in [loginId].
     * @return a [TotpResponse] to be used by authenticator apps.
     */
    suspend fun signUp(loginId: String, details: SignUpDetails? = null): TotpResponse

    /** @see signUp */
    fun signUp(loginId: String, details: SignUpDetails? = null, callback: (Result<TotpResponse>) -> Unit)

    /**
     * Updates an existing user by adding TOTP as an authentication method.
     *
     * In order to do this, the user identified by [loginId] must have an active
     * [DescopeSession] whose [refreshJwt] should be passed as a parameter to this function.
     * This function returns a [TotpResponse.key] (seed) that allows
     * authenticator apps to generate TOTP codes. The same information
     * is returned in multiple formats.
     *
     * @param loginId the identifier of the user to authenticate.
     * @param refreshJwt the refreshJwt from an active [DescopeSession].
     * @return a [TotpResponse] to be used by authenticator apps.
     */
    suspend fun update(loginId: String, refreshJwt: String): TotpResponse

    /** @see update */
    suspend fun update(loginId: String, refreshJwt: String, callback: (Result<TotpResponse>) -> Unit)

    /**
     * Verifies a TOTP code that was generated by an authenticator app.
     *
     * Returns an [AuthenticationResponse] if the provided [loginId] and the [code]
     * generated by an authenticator app match.
     *
     * @param loginId the identifier of the user to authenticate.
     * @param code generated by the authenticator app.
     * @param options additional behaviors to perform during authentication.
     * @return an [AuthenticationResponse] upon successful verification.
     */
    suspend fun verify(loginId: String, code: String, options: List<SignInOptions>? = null): AuthenticationResponse

    /** @see verify */
    fun verify(loginId: String, code: String, options: List<SignInOptions>? = null, callback: (Result<AuthenticationResponse>) -> Unit)
}

/**
 * Authenticates users using a special link that once clicked, can authenticate the user.
 *
 * In order to correctly implement, the app must make sure the link redirects back
 * to the app. Read more on [app links](https://developer.android.com/training/app-links)
 * to learn more. Once redirected back to the app, call the [verify] function
 * on the appended token URL parameter.
 */
interface DescopeMagicLink {

    /**
     * Authenticates a new user using a magic link.
     *
     * The magic link will be sent to the user identified by [loginId]
     * via a delivery [method] of choice.
     *
     * @param method the delivery method to send the magic link.
     * @param loginId the identifier of the user to authenticate.
     * @param details optional user information. Should contain the necessary delivery information if not provided in [loginId].
     * @param uri optional magic link URI. If null, the default URI in Descope console will be used.
     * @return masked version of the delivery method used, i.e. a masked email address or phone number.
     */
    suspend fun signUp(method: DeliveryMethod, loginId: String, details: SignUpDetails? = null, uri: String? = null): String

    /** @see signUp */
    fun signUp(method: DeliveryMethod, loginId: String, details: SignUpDetails? = null, uri: String? = null, callback: (Result<String>) -> Unit)

    /**
     * Authenticates an existing user using a magic link.
     *
     * The magic link will be sent to the user identified by [loginId]
     * via a delivery [method] of choice.
     *
     * @param method the delivery method to send the magic link.
     * @param loginId the identifier of the user to authenticate.
     * @param uri optional magic link URI. If null, the default URI in Descope console will be used.
     * @param options additional behaviors to perform during authentication.
     * @return masked version of the delivery method used, i.e. a masked email address or phone number.
     */
    suspend fun signIn(method: DeliveryMethod, loginId: String, uri: String? = null, options: List<SignInOptions>? = null): String


    /** @see signIn */
    fun signIn(method: DeliveryMethod, loginId: String, uri: String? = null, options: List<SignInOptions>? = null, callback: (Result<String>) -> Unit)

    /**
     * Authenticates an existing user if one exists, or creates a new user
     * using a magic link.
     *
     * The magic link will be sent to the user identified by [loginId]
     * via a delivery [method] of choice.
     *
     * @param method the delivery method to send the magic link.
     * @param loginId the identifier of the user to authenticate.
     * @param uri optional magic link URI. If null, the default URI in Descope console will be used.
     * @param options additional behaviors to perform during authentication.
     * @return masked version of the delivery method used, i.e. a masked email address or phone number.
     */
    suspend fun signUpOrIn(method: DeliveryMethod, loginId: String, uri: String? = null, options: List<SignInOptions>? = null): String

    /** @see signUpOrIn */
    fun signUpOrIn(method: DeliveryMethod, loginId: String, uri: String? = null, options: List<SignInOptions>? = null, callback: (Result<String>) -> Unit)

    /**
     * Updates an existing user by adding an [email] address.
     *
     * The [email] will be updated for the user identified by [loginId]
     * after it is verified via magic link. In order to do this,
     * the user must have an active [DescopeSession] whose [refreshJwt] should
     * be passed as a parameter to this function.
     *
     * @param email the email address to add to the user profile.
     * @param loginId the identifier of the user to update.
     * @param uri the magic link URI. If null, the default URI in Descope console will be used.
     * @param refreshJwt the refreshJwt from an active [DescopeSession].
     * @param options Whether to add the new email address as a loginId for the updated user, and
     * in that case, if another user already has the same email address as a loginId how to
     * merge the two users. See the documentation for [UpdateOptions] for more details.
     * @return masked email address the magic link was sent to.
     */
    suspend fun updateEmail(email: String, loginId: String, uri: String, refreshJwt: String, options: UpdateOptions? = null): String

    /** @see updateEmail */
    fun updateEmail(email: String, loginId: String, uri: String, refreshJwt: String, options: UpdateOptions? = null, callback: (Result<String>) -> Unit)

    /**
     * Updates an existing user by adding a [phone] number.
     *
     * The [phone] number will be updated for the user identified by [loginId]
     * after it is verified via magic link. In order to do this,
     * the user must have an active [DescopeSession] whose [refreshJwt] should
     * be passed as a parameter to this function.
     *
     * @param phone the phone number to add to the user.
     * @param method the delivery method used for this operation.
     * @param loginId the identifier of the user to authenticate.
     * @param uri the default magic link URI to use, if not configured.
     * @param refreshJwt the refreshJwt from an active [DescopeSession].
     * @param options Whether to add the new phone number as a loginId for the updated user, and
     * in that case, if another user already has the same phone number as a loginId how to
     * merge the two users. See the documentation for [UpdateOptions] for more details.
     * @return masked phone number the OTP was sent to.
     */
    suspend fun updatePhone(phone: String, method: DeliveryMethod, loginId: String, uri: String? = null, refreshJwt: String, options: UpdateOptions? = null): String

    /** @see updatePhone */
    fun updatePhone(phone: String, method: DeliveryMethod, loginId: String, uri: String? = null, refreshJwt: String, options: UpdateOptions? = null, callback: (Result<String>) -> Unit)

    /**
     * Verifies a magic link [token].
     *
     * In order to effectively do this, the link generated should refer back to
     * the app, then the `t` URL parameter should be extracted and sent to this
     * function. Upon successful authentication an [AuthenticationResponse] is returned.
     *
     * @param token the magic link token to verify.
     * @return an [AuthenticationResponse] upon successful verification
     */
    suspend fun verify(token: String): AuthenticationResponse

    /** @see verify */
    fun verify(token: String, callback: (Result<AuthenticationResponse>) -> Unit)
}

/**
 * Authenticate users using one of three special links that once clicked,
 * can authenticate the user.
 *
 * This method is geared towards cross-device authentication. In order to
 * correctly implement, the app must make sure the uri redirects to a webpage
 * which will verify the link for them. The app will poll for a valid session
 * in the meantime, and will authenticate the user as soon as they are
 * verified via said webpage. To learn more consult the
 * official Descope docs.
 */
interface DescopeEnchantedLink {

    /**
     * Authenticates a new user using an enchanted link, sent via email.
     *
     * A new user identified by [loginId] and the optional [details] details will be added
     * upon successful authentication.
     *
     * The caller should use the returned [EnchantedLinkResponse.linkId] to show the
     * user which link they need to press in the enchanted link email, and then use
     * the [EnchantedLinkResponse.pendingRef] value to poll until the authentication is verified.
     *
     * @param loginId the identifier of the user to authenticate.
     * @param details optional user information. Should contain an email address if not provided in [loginId].
     * @param uri optional enchanted link URI. If not given, the default URI from the Descope console will be used.
     * @return an [EnchantedLinkResponse] with the details necessary for polling and authenticating the user.
     */
    suspend fun signUp(loginId: String, details: SignUpDetails? = null, uri: String? = null): EnchantedLinkResponse

    /** @see signUp */
    fun signUp(loginId: String, details: SignUpDetails? = null, uri: String? = null, callback: (Result<EnchantedLinkResponse>) -> Unit)

    /**
     * Authenticates an existing user using an enchanted link, sent via email.
     *
     * An enchanted link will be sent to the user identified by [loginId].
     * The caller should use the returned [EnchantedLinkResponse.linkId] to show the
     * user which link they need to press in the enchanted link email, and then use
     * the [EnchantedLinkResponse.pendingRef] value to poll until the authentication is verified.
     *
     * @param loginId the identifier of the user to authenticate.
     * @param uri optional enchanted link URI. If not given, the default URI from the Descope console will be used.
     * @param options additional behaviors to perform during authentication.
     * @return an [EnchantedLinkResponse] with the details necessary for polling and authenticating the user.
     */
    suspend fun signIn(loginId: String, uri: String? = null, options: List<SignInOptions>? = null): EnchantedLinkResponse

    /** @see signIn */
    fun signIn(loginId: String, uri: String? = null, options: List<SignInOptions>? = null, callback: (Result<EnchantedLinkResponse>) -> Unit)

    /**
     * Authenticates an existing user if one exists, or create a new user using an
     * enchanted link, sent via email.
     * 
     * The caller should use the returned [EnchantedLinkResponse.linkId] to show the
     * user which link they need to press in the enchanted link email, and then use
     * the [EnchantedLinkResponse.pendingRef] value to poll until the authentication is verified.
     * 
     * @param loginId the identifier of the user to authenticate.
     * @param uri optional enchanted link URI. If not given, the default URI from the Descope console will be used.
     * @param options additional behaviors to perform during authentication.
     * @return an [EnchantedLinkResponse] with the details necessary for polling and authenticating the user.
     */
    suspend fun signUpOrIn(loginId: String, uri: String? = null, options: List<SignInOptions>? = null): EnchantedLinkResponse

    /** @see signUpOrIn */
    fun signUpOrIn(loginId: String, uri: String? = null, options: List<SignInOptions>? = null, callback: (Result<EnchantedLinkResponse>) -> Unit)

    /**
     * Updates an existing user by adding an [email] address.
     *
     * The [email] will be updated after it is verified via enchanted link. In order to
     * do this, the user must have an active [DescopeSession] whose [refreshJwt] should
     * be passed as a parameter to this function.
     *
     * The caller should use the returned [EnchantedLinkResponse.linkId] to show the
     * user which link they need to press in the enchanted link email, and then use
     * the [EnchantedLinkResponse.pendingRef] value to poll until the authentication is verified.
     *
     * @param email the email address to add to the user profile.
     * @param loginId the identifier of the user to update.
     * @param uri optional enchanted link URI. If not given, the default URI from the Descope console will be used.
     * @param refreshJwt the refreshJwt from an active [DescopeSession].
     * @param options Whether to add the new email address as a loginId for the updated user, and
     * in that case, if another user already has the same email address as a loginId how to
     * merge the two users. See the documentation for [UpdateOptions] for more details.
     * @return masked email address the magic link was sent to.
     */
    suspend fun updateEmail(email: String, loginId: String, uri: String? = null, refreshJwt: String, options: UpdateOptions? = null): EnchantedLinkResponse

    /** @see updateEmail */
    fun updateEmail(email: String, loginId: String, uri: String? = null, refreshJwt: String, options: UpdateOptions? = null, callback: (Result<EnchantedLinkResponse>) -> Unit)

    /**
     * Checks if an enchanted link authentication has been verified by the user.
     *
     * Provide this function with a [pendingRef] received by [signUp], [signIn], [signUpOrIn] or [u
     * This function will only return an [AuthenticationResponse] successfully after the user
     * presses the enchanted link in the authentication email.
     *
     * - **Important:** This function doesn't perform any polling or waiting, so calling code
     * should expect to catch any thrown exceptions and
     * handle them appropriately. For most use cases it might be more convenient to
     * use [pollForSession] instead.
     *
     * @param pendingRef from the returned [EnchantedLinkResponse.pendingRef].
     * @return an [AuthenticationResponse] upon successful verification.
     */
    suspend fun checkForSession(pendingRef: String): AuthenticationResponse

    /** @see checkForSession */
    fun checkForSession(pendingRef: String, callback: (Result<AuthenticationResponse>) -> Unit)

    /**
     * Waits until an enchanted link authentication has been verified by the user.
     *
     * Provide this function with a [pendingRef] received by [signUp], [signIn], [signUpOrIn] or [updateEmail].
     * This function will only return an [AuthenticationResponse] successfully after the user
     * presses the enchanted link in the authentication email.
     *
     * This function calls [checkForSession] periodically until the authentication
     * is verified. It will keep polling even if it encounters network errors, but
     * any other unexpected errors will be rethrown. If the timeout expires a
     * `DescopeError.enchantedLinkExpired` error is thrown.
     * [timeoutMilliseconds] is an optional duration to poll for until giving up. If not
     * given a default value of 2 minutes is used.
     *
     * To cancel use a `Job` to wrap the call. Read more here:
     * https://kotlinlang.org/docs/cancellation-and-timeouts.html#cancelling-coroutine-execution
     *
     * @param pendingRef from the returned [EnchantedLinkResponse.pendingRef].
     * @param timeoutMilliseconds how long to poll for in milliseconds. Defaults to 2 minutes.
     * @return an [AuthenticationResponse] upon successful verification.
     */
    suspend fun pollForSession(pendingRef: String, timeoutMilliseconds: Long? = null): AuthenticationResponse

    /** @see pollForSession */
    fun pollForSession(pendingRef: String, timeoutMilliseconds: Long? = null, callback: (Result<AuthenticationResponse>) -> Unit)
}

/**
 * Authenticate a user using an OAuth provider.
 *
 * Use the Descope console to configure which authentication provider you'd like to support.
 *
 * The OAuth protocol is based on creating redirect chain. In order to redirect back to the
 * app it's required to set up deep links or app links. See more here:
 * https://developer.android.com/training/app-links
 *
 * See examples for more information on how to handle deep links.
 */
interface DescopeOAuth {

    /**
     * Starts an OAuth redirect chain to authenticate a user.
     *
     * This function returns a URL to redirect to in order to
     * authenticate the user against the chosen [provider].
     *
     *     // use one of the built in constants for the OAuth provider
     *     val authUrl = Descope.oauth.start(OAuthProvider.Github, redirectUrl = "exampleauthschema://my-app.com/handle-oauth")
     *
     *     // or pass a string with the name of a custom provider
     *     val authUrl = Descope.oauth.start(OAuthProvider("myprovider"), redirectUrl = "exampleauthschema://my-app.com/handle-oauth")
     *
     * - **Important:** Make sure a default OAuth redirect URL is configured
     * in the Descope console, or provided by this call via [redirectUrl]. It should
     * redirect back to this app using a deep link. See examples for more information.
     *
     * @param provider which provider to authenticate against
     * @param redirectUrl optional redirect URL. If null, the default redirect URL in Descope console will be used.
     * @param options additional behaviors to perform during authentication.
     * @return a URL that starts the OAuth redirect chain
     */
    suspend fun start(provider: OAuthProvider, redirectUrl: String? = null, options: List<SignInOptions>? = null): String

    /** @see start */
    fun start(provider: OAuthProvider, redirectUrl: String? = null, options: List<SignInOptions>? = null, callback: (Result<String>) -> Unit)

    /**
     * Completes an OAuth redirect chain.
     *
     * This function exchanges the [code] received in the `code` URL
     * parameter for an [AuthenticationResponse].
     *
     * @param code received in the final redirect as a url parameter named `code`
     * @return an [AuthenticationResponse] upon successful verification.
     */
    suspend fun exchange(code: String): AuthenticationResponse

    /** @see exchange */
    fun exchange(code: String, callback: (Result<AuthenticationResponse>) -> Unit)

    /**
     * Authenticates the user using the native Sign in with Google dialog.
     *
     * This API enables a more streamlined user experience than the equivalent browser
     * based OAuth authentication, when using the `Google` provider or a custom provider
     * that's configured for Google. The authentication presents a native dialog that lets
     * the user sign in with the Google account they're already using on their device.
     *
     * If you haven't already configured your app to support Sign in with Google you'll
     * probably need to set up your [Google APIs console project](https://developers.google.com/identity/one-tap/android/get-started#api-console)
     * for this. You should also configure an OAuth provider for Google in the in the [Descope console](https://app.descope.com/settings/authentication/social),
     * with its `Grant Type` set to `Implicit`. Also note that the `Client ID` and
     * `Client Secret` should be set to the values of your `Web application` OAuth client,
     * rather than those from the `Android` OAuth client.
     * 
     * For more details about configuring your app see the [Credential Manager documentation](https://developer.android.com/training/sign-in/credential-manager).
     *
     * Note: This is an asynchronous operation that performs network requests before and
     * after displaying the modal authentication view. It is thus recommended to switch the
     * user interface to a loading state before calling this function, otherwise the user
     * might accidentally interact with the app when the authentication view is not
     * being displayed.
     *
     * @param context the Activity context used to launch any UI needed.
     * @param provider the provider the user wishes to authenticate with, this will usually
     * either be `Google` or the name of a custom provider that's configured for Google.
     * @param options additional behaviors to perform during authentication.
     * @return an [AuthenticationResponse] upon successful authentication.
     */
    suspend fun native(context: Context, provider: OAuthProvider, options: List<SignInOptions>? = null): AuthenticationResponse

    /** @see native */
    fun native(context: Context, provider: OAuthProvider, options: List<SignInOptions>? = null, callback: (Result<AuthenticationResponse>) -> Unit)
}

/**
 * Authenticate a user using SSO.
 *
 * Use the Descope console to configure your SSO details in order for this method to work properly.
 *
 * The SSO protocol is based on creating redirect chain. In order to redirect back to the
 * app it's required to set up deep links or app links. See more here:
 * https://developer.android.com/training/app-links
 *
 * See examples for more information on how to handle deep links.
 */
interface DescopeSso {

    /**
     * Starts an SSO redirect chain to authenticate a user.
     *
     * This function returns a URL to redirect to in order to
     * uthenticate the user according to the provided [emailOrTenantId].
     *
     * - **Important:** Make sure a SSO is set up correctly and a redirect URL is configured
     * in the Descope console, or provided by this call via [redirectUrl]. It should
     * redirect back to this app using a deep link. See examples for more information.
     *
     * @param emailOrTenantId an email address that belongs to a tenant or a tenant ID
     * @param redirectUrl optional redirect URL. If null, the default redirect URL in Descope console will be used.
     * @param options additional behaviors to perform during authentication.
     * @return a URL that starts the SSO redirect chain
     */
    suspend fun start(emailOrTenantId: String, redirectUrl: String?, options: List<SignInOptions>? = null): String

    /** @see start */
    fun start(emailOrTenantId: String, redirectUrl: String?, options: List<SignInOptions>? = null, callback: (Result<String>) -> Unit)

    /**
     * Completes an SSO redirect chain.
     *
     * This function exchanges the [code] received in the `code` URL
     * parameter for an [AuthenticationResponse].
     *
     * @param code received in the final redirect as a url parameter named `code`
     * @return an [AuthenticationResponse] upon successful verification.
     */
    suspend fun exchange(code: String): AuthenticationResponse

    /** @see exchange */
    fun exchange(code: String, callback: (Result<AuthenticationResponse>) -> Unit)

}

/**
 * Authenticate users using passkeys.
 *
 * The authentication operations in this interface are all suspending functions that
 * perform network requests before and after displaying the modal authentication view.
 * It is thus recommended to switch the user interface to a loading state before calling
 * this function, otherwise the user might accidentally interact with the app when the
 * authentication view is not being displayed.
 * 
 * - **Important**: Before authentication via passkeys is possible, some set up is required.
 * Please follow the [Add support for Digital Asset Links](https://developer.android.com/training/sign-in/passkeys#add-support-dal)
 * setup, as described in the official Google docs.
 */
interface DescopePasskey {
    /**
     * Authenticates a new user by creating a new passkey.
     *
     * @param context The Android context being run - should be an Activity context.
     * @param loginId What identifies the user when logging in.
     * @param details Optional details about the user signing up.
     * @return An [AuthenticationResponse] value upon successful authentication.
     */
    suspend fun signUp(context: Context, loginId: String, details: SignUpDetails? = null): AuthenticationResponse

    /**
     * Authenticates an existing user by prompting for an existing passkey.
     *
     * @param context The Android context being run - should be an Activity context.
     * @param loginId What identifies the user when logging in.
     * @param options Additional behaviors to perform during authentication.
     * @return An [AuthenticationResponse] value upon successful authentication.
     */
    suspend fun signIn(context: Context, loginId: String, options: List<SignInOptions>? = null): AuthenticationResponse

    /**
     * Authenticates an existing user if one exists or creates a new one.
     * 
     * A new passkey will be created if the user doesn't already exist, otherwise a passkey
     * must be available on their device to authenticate with.
     *
     * @param context The Android context being run - should be an Activity context
     * @param loginId What identifies the user when logging in
     * @param options Additional behaviors to perform during authentication.
     * @return An [AuthenticationResponse] value upon successful authentication.
     */
    suspend fun signUpOrIn(context: Context, loginId: String, options: List<SignInOptions>? = null): AuthenticationResponse

    /**
     * Updates an existing user by adding a new passkey as an authentication method.
     *
     * @param context The Android context being run - should be an Activity context
     * @param loginId What identifies the user when logging in
     * @param refreshJwt The `refreshJwt` from an active [DescopeSession].
     */
    suspend fun add(context: Context, loginId: String, refreshJwt: String)
}

/**
 * Authenticate users using a password.
 */
interface DescopePassword {

    /**
     * Creates a new user that can later sign in with a password.
     *
     * Uses [loginId] to identify the user, typically an email, phone,
     * or any other unique identifier. The provided [password] will allow
     * the user to sign in in the future and must conform to the password policy
     * defined in the password settings in the Descope console.
     * The optional [details] provides additional details about the user signing up.
     * Returns an [AuthenticationResponse] upon successful authentication.
     *
     * @param loginId the identifier of the user to authenticate.
     * @param password the user's password in cleartext.
     * @param details optional user information. Should contain the necessary delivery information if not provided in [loginId].
     * @return an [AuthenticationResponse] upon successful verification.
     */
    suspend fun signUp(loginId: String, password: String, details: SignUpDetails? = null): AuthenticationResponse

    /** @see signUp */
    fun signUp(loginId: String, password: String, details: SignUpDetails? = null, callback: (Result<AuthenticationResponse>) -> Unit)

    /**
     * Authenticates an existing user using a password.
     *
     * @param loginId the identifier of the user to authenticate.
     * @param password the password to verify.
     * @return an [AuthenticationResponse] upon successful authentication.
     */
    suspend fun signIn(loginId: String, password: String): AuthenticationResponse

    /** @see signIn */
    fun signIn(loginId: String, password: String, callback: (Result<AuthenticationResponse>) -> Unit)

    /**
     * Updates a user's password.
     *
     * In order to do this, the user must have an active [DescopeSession] whose
     * [refreshJwt] should be passed as a parameter to this function.
     *
     * Updates the user identified by [loginId] with [newPassword].
     * [newPassword] must conform to the password policy defined in the
     * password settings in the Descope console
     *
     * @param loginId the identifier of the user to authenticate.
     * @param newPassword the new password.
     * @param refreshJwt an [AuthenticationResponse] upon successful verification.
     */
    suspend fun update(loginId: String, newPassword: String, refreshJwt: String)

    fun update(loginId: String, newPassword: String, refreshJwt: String, callback: (Result<Unit>) -> Unit)

    /**
     * Replaces a user's password by providing their current password.
     *
     * Updates the user identified by [loginId] and [oldPassword] with [newPassword].
     * [newPassword] must conform to the password policy defined in the
     * password settings in the Descope console
     *
     * @param loginId the identifier of the user to authenticate.
     * @param oldPassword the old (current) password.
     * @param newPassword the new password to set.
     * @return an [AuthenticationResponse] upon successful replacement and verification.
     */
    suspend fun replace(loginId: String, oldPassword: String, newPassword: String): AuthenticationResponse

    /** @see replace */
    fun replace(loginId: String, oldPassword: String, newPassword: String, callback: (Result<AuthenticationResponse>) -> Unit)

    /**
     * Sends a password reset email to the user.
     *
     * This operation starts a Magic Link flow for the user identified by
     * [loginId] depending on the configuration in the Descope console. An optional
     * [redirectUrl] can be provided to the magic link method.
     * After the authentication flow is finished
     * use the refreshJwt to call [update] and change the user's password.
     *
     * - **Important:** The user must be verified according to the configured
     * password reset method.
     *
     * @param loginId the identifier of the user to authenticate.
     * @param redirectUrl an optional URL to have the magic link redirect to.
     */
    suspend fun sendReset(loginId: String, redirectUrl: String? = null)

    /** @see sendReset */
    fun sendReset(loginId: String, redirectUrl: String? = null, callback: (Result<Unit>) -> Unit)

    /**
     * Fetches the rules for valid passwords.
     *
     * The [PasswordPolicy] is configured in the password settings in the Descope console, and
     * these values can be used to implement client-side validation of new user passwords
     * for a better user experience.
     *
     * In any case, all password rules are enforced by Descope on the server side as well.
     *
     * @return the [PasswordPolicy] for this project.
     */
    suspend fun getPolicy(): PasswordPolicy

    /** @see getPolicy */
    fun getPolicy(callback: (Result<PasswordPolicy>) -> Unit)
}

/**
 * Authenticate a user using the Descope Flows.
 * Flows are run using Chrome Custom Tabs, and upon completion, redirect
 * back to the app.
 *
 * In order to redirect back to the
 * app it's required to set up app links. See more here:
 * https://developer.android.com/training/app-links
 *
 * See examples for more information on how to handle deep links.
 */
interface DescopeFlow {

    /** The current (last created) runner. Will be `null` before creation and after completion of a flow */
    val currentRunner: Runner?

    /**
     * Create a new [DescopeFlow.Runner] that is able to run user authentication flows.
     *
     * Once created, the runner will be available for access via [currentRunner].
     *
     * @property flowUrl the URL where the flow is hosted.
     * @property deepLinkUrl a deep link back to the app that will handle the exchange.
     * @property backupCustomScheme a backup custom scheme deep link for Opera browser users,
     * which blocks app links by default.
     */
    fun create(flowUrl: String, deepLinkUrl: String, backupCustomScheme: String? = null): Runner

    /**
     * A helper interface that encapsulates a single flow run.
     *
     * First create a new `Runner` using the [create] method.
     * Then [start] the flow where needed.
     *
     * In case the flow uses `Magic Link Authentication` call [resume] when on the captured
     * incoming URI. For your convenience, the [currentRunner] is available to access the current
     * flow runner.
     */
    interface Runner {
        /**
         * Start a user authentication flow from the current [context].
         *
         * @param context the context launching the authentication flow.
         */
        fun start(context: Context)

        /**
         * Start a flow for a currently authenticated user from the current [context].
         * 
         * Use this version of start if the user has an **active session**.
         * Note: This is an asynchronous operation that performs network requests before and
         * opening the browser. It is thus recommended to switch the
         * user interface to a loading state before calling this function, otherwise the user
         * might accidentally interact with the app when the authentication view is not
         * being displayed.
         *
         * @param context the context launching the authentication flow.
         * @param flowId the ID of the flow to run
         * @param refreshJwt the refreshJwt from an active [DescopeSession]. 
         */
        suspend fun start(context: Context, flowId: String, refreshJwt: String)

        /** @see start (async version) */
        fun start(context: Context, flowId: String, refreshJwt: String, callback: (Result<Unit>) -> Unit)

        /**
         * Resumes an ongoing flow after a redirect back to the app.
         * This is required for *Magic Link only* at this stage.
         *
         * - **Note:** This requires additional setup on the application side.
         *  See the examples for more details.
         *
         * @param context the context launching the authentication flow.
         * @param incomingUriString the URI received when redirecting back to the app.
         */
        fun resume(context: Context, incomingUriString: String)

        /**
         * Handles the final flow redirect response and exchanges it for an [AuthenticationResponse].
         *
         * Provide this function the [incomingUri] from the handling activity, e.g.
         *
         *     val incomingUri: Uri = intent?.data ?: return
         *
         * @param incomingUri the URI passed to the deep link handling Activity.
         * @return an [AuthenticationResponse] upon successful verification.
         */
        suspend fun exchange(incomingUri: Uri): AuthenticationResponse

        /** @see exchange */
        fun exchange(incomingUri: Uri, callback: (Result<AuthenticationResponse>) -> Unit)
    }
}
