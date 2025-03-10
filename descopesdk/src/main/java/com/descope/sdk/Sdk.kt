package com.descope.sdk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Looper
import com.descope.internal.http.DescopeClient
import com.descope.internal.routes.Auth
import com.descope.internal.routes.EnchantedLink
import com.descope.internal.routes.Flow
import com.descope.internal.routes.MagicLink
import com.descope.internal.routes.OAuth
import com.descope.internal.routes.Otp
import com.descope.internal.routes.Passkey
import com.descope.internal.routes.Password
import com.descope.internal.routes.Sso
import com.descope.internal.routes.Totp
import com.descope.session.DescopeSessionManager
import com.descope.session.SessionLifecycle
import com.descope.session.SessionStorage

class DescopeSdk(context: Context, projectId: String, configure: DescopeConfig.() -> Unit) {
    var sessionManager: DescopeSessionManager
    val auth: DescopeAuth
    val otp: DescopeOtp
    val totp: DescopeTotp
    val magicLink: DescopeMagicLink
    val enchantedLink: DescopeEnchantedLink
    val oauth: DescopeOAuth
    val sso: DescopeSso
    val passkey: DescopePasskey
    val password: DescopePassword
    @Deprecated(message = "Use DescopeFlowView instead")
    val flow: DescopeFlow

    internal val client: DescopeClient

    init {
        if (Looper.getMainLooper().thread != Thread.currentThread()) {
            throw IllegalStateException("Descope SDK must be initialized on the main thread")
        }
        // init config
        val config = DescopeConfig(projectId = projectId)
        configure(config)
        config.logger?.isDebug = context.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // init auth methods
        client = DescopeClient(config)
        auth = Auth(client)
        otp = Otp(client)
        totp = Totp(client)
        magicLink = MagicLink(client)
        enchantedLink = EnchantedLink(client)
        oauth = OAuth(client)
        sso = Sso(client)
        passkey = Passkey(client)
        password = Password(client)
        flow = Flow(client)
        // init session manager
        sessionManager = initDefaultManager(context, config)
    }

    // Internal

    private fun initDefaultManager(context: Context, config: DescopeConfig): DescopeSessionManager {
        val storage = SessionStorage(context.applicationContext, config.projectId, config.logger)
        val lifecycle = SessionLifecycle(auth, storage, config.logger)
        return DescopeSessionManager(storage, lifecycle)
    }

    // SDK information

    companion object {
        /** The Descope SDK name */
        const val NAME = "DescopeAndroid"

        /** The Descope SDK version */
        const val VERSION = "0.13.2"
    }
}
