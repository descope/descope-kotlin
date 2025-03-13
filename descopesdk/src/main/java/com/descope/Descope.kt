@file:Suppress("MemberVisibilityCanBePrivate", "unused")

package com.descope

import android.content.Context
import com.descope.sdk.DescopeAuth
import com.descope.sdk.DescopeConfig
import com.descope.sdk.DescopeEnchantedLink
import com.descope.sdk.DescopeFlow
import com.descope.sdk.DescopeMagicLink
import com.descope.sdk.DescopeOAuth
import com.descope.sdk.DescopeOtp
import com.descope.sdk.DescopePasskey
import com.descope.sdk.DescopePassword
import com.descope.sdk.DescopeSdk
import com.descope.sdk.DescopeSso
import com.descope.sdk.DescopeTotp
import com.descope.session.DescopeSession
import com.descope.session.DescopeSessionManager

/**
 * Provides functions for working with the Descope API.
 *
 * This singleton object is provided as a convenience that should be suitable for most
 * app architectures. If you prefer a different approach you can also create an instance
 * of the [DescopeSdk] class instead.
 * 
 * - **Important**: Make sure to call the [setup] function when initializing your application.
 */
object Descope {
    /**
     * The setup of the `Descope` singleton.
     * 
     * Call this function when initializing you application.
     * **This function must be called before the [Descope] object can be used**
     *
     * For example:
     *
     *     Descope.setup(applicationContext, projectId = "DESCOPE_PROJECT_ID") {
     *         baseUrl = "https://my.app.com"
     *         if (BuildConfig.DEBUG) {
     *             logger = DescopeLogger()
     *         }
     *     }
     *      
     * @param context The application context
     * @param projectId The Descope project ID
     * @param configure An optional closure that allows to finely configure the Descope SDK
     */
    fun setup(
        context: Context,
        projectId: String,
        configure: DescopeConfig.() -> Unit = {},
    ) {
        sdk = DescopeSdk(context, projectId, configure)
    }
    
    /**
     *  Manages the storage and lifetime of a [DescopeSession].
     *
     *  You can use this `DescopeSessionManager` object as a shared instance to manage
     *  authenticated sessions in your application.
     *
     *      val authResponse = Descope.otp.verify(DeliveryMethod.Email, "andy@example.com", "123456")
     *      val session = DescopeSession(authResponse)
     *      Descope.sessionManager.manageSession(session)
     *
     *  See the documentation for [DescopeSessionManager] for more details.
     */
    var sessionManager: DescopeSessionManager
        get() = sdk.sessionManager
        set(value) {
            sdk.sessionManager = value
        }

    // Authentication functions that call the Descope API.

    /** Authenticate using an authentication flow */
    val flow: DescopeFlow
        get() = sdk.flow

    /** General functions. */
    val auth: DescopeAuth
        get() = sdk.auth

    /** Authentication with OTP codes via email or phone. */
    val otp: DescopeOtp
        get() = sdk.otp

    /** Authentication with TOTP codes. */
    val totp: DescopeTotp
        get() = sdk.totp

    /** Authentication with magic links. */
    val magicLink: DescopeMagicLink
        get() = sdk.magicLink

    /** Authentication with enchanted links. */
    val enchantedLink: DescopeEnchantedLink
        get() = sdk.enchantedLink

    /** Authentication with OAuth. */
    val oauth: DescopeOAuth
        get() = sdk.oauth

    /** Authentication with SSO. */
    val sso: DescopeSso
        get() = sdk.sso

    /** Authentication with passkeys. */
    val passkey: DescopePasskey
        get() = sdk.passkey

    /** Authentication with passwords. */
    val password: DescopePassword
        get() = sdk.password
    
    // The underlying `DescopeSdk` object used by the `Descope` singleton.
    internal lateinit var sdk: DescopeSdk
    
    // Used internally to check if the singleton object has an actual sdk value.
    internal val isInitialized
        get() = this::sdk.isInitialized
}
