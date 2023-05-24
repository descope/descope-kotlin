package com.descope.sdk

import com.descope.internal.routes.Flow
import com.descope.internal.http.DescopeClient
import com.descope.internal.routes.Auth
import com.descope.internal.routes.EnchantedLink
import com.descope.internal.routes.MagicLink
import com.descope.internal.routes.OAuth
import com.descope.internal.routes.Otp
import com.descope.internal.routes.Password
import com.descope.internal.routes.Sso
import com.descope.internal.routes.Totp
import com.descope.session.DescopeSessionManager
import com.descope.session.SessionLifecycle
import com.descope.session.SessionStorage

class DescopeSdk(val config: DescopeConfig) {

    val auth: DescopeAuth
    val otp: DescopeOtp
    val totp: DescopeTotp
    val magicLink: DescopeMagicLink
    val enchantedLink: DescopeEnchantedLink
    val oauth: DescopeOAuth
    val sso: DescopeSso
    val password: DescopePassword
    val flow: DescopeFlow

    var sessionManager: DescopeSessionManager
        // defer initialization to allow setting a custom manager without loading the current state
        get() = manager ?: initDefaultManager()
        set(value) {
            manager = value
        }

    constructor(projectId: String) : this(DescopeConfig(projectId))

    init {
        assert(config.projectId != "") { "The projectId value must not be an empty string" }
        val client = DescopeClient(config)
        auth = Auth(client)
        otp = Otp(client)
        totp = Totp(client)
        magicLink = MagicLink(client)
        enchantedLink = EnchantedLink(client)
        oauth = OAuth(client)
        sso = Sso(client)
        password = Password(client)
        flow = Flow(client)
    }

    // Internal

    private var manager: DescopeSessionManager? = null

    private fun initDefaultManager(): DescopeSessionManager {
        val storage = SessionStorage(config.projectId)
        val lifecycle = SessionLifecycle(auth)
        val manager = DescopeSessionManager(storage, lifecycle)
        this.manager = manager
        return manager
    }
}
