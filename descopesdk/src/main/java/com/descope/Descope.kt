package com.descope

import android.content.Context
import com.descope.sdk.DescopeAuth
import com.descope.sdk.DescopeConfig
import com.descope.sdk.DescopeEnchantedLink
import com.descope.sdk.DescopeFlow
import com.descope.sdk.DescopeMagicLink
import com.descope.sdk.DescopeOAuth
import com.descope.sdk.DescopeOtp
import com.descope.sdk.DescopePassword
import com.descope.sdk.DescopeSdk
import com.descope.sdk.DescopeSso
import com.descope.sdk.DescopeTotp
import com.descope.session.DescopeSession
import com.descope.session.DescopeSessionManager
import com.descope.session.SessionStorage

/**
 * Provides functions for working with the Descope API.
 *
 * This singleton object is provided as a convenience that should be suitable for most
 * app architectures. If you prefer a different approach you can also create an instance
 * of the [DescopeSdk] class instead.
 */
object Descope {
    /**
     * The projectId of your Descope project.
     *
     * You will most likely want to set this value in your application's initialization code,
     * and in most cases you only need to set this to work with the `Descope` singleton.
     *
     * - **Note:** This is a shortcut for setting the [Descope.config] property.
     */
    var projectId: String = ""
        get() = config.projectId
        set(value) {
            config = DescopeConfig(projectId = value)
            field = value
        }

    /**
     * The configuration of the `Descope` singleton.
     *
     * Set this property **instead** of [Descope.projectId] in your application's initialization code
     * if you require additional configuration.
     *
     * - **Important:** To prevent accidental misuse only one of `config` and `projectId` can
     *     be set, and they can only be set once. If this isn't appropriate for your use
     *     case you can also use the [DescopeSdk] class directly instead.
     */
    var config: DescopeConfig = DescopeConfig.initial
        set(value) {
            assert(config.projectId == "") { "The config property must not be set more than once" }
            field = value
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

    /**
     * Provide Descope with the application context if you're using
     * [DescopeSessionManager] for automatic session management, and
     * want to enable the default secure persistence layer. Alternatively
     * you can provide your own [SessionStorage.Store]
     */
    var provideApplicationContext: (() -> Context)? = null

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

    /** Authentication with passwords. */
    val password: DescopePassword
        get() = sdk.password

    // The underlying `DescopeSDK` object used by the `Descope` singleton.
    private val sdk: DescopeSdk by lazy {
        DescopeSdk(config = config)
    }
}

// SDK information

/** The Descope SDK name */
val Descope.name: String
    get() = "DescopeAndroid"

/** The Descope SDK version */
val Descope.version: String
    get() = "0.9.2"
