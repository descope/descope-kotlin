@file:Suppress("DEPRECATION")

package com.descope.sdk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Looper
import com.descope.android.DescopeSystemInfo
import com.descope.internal.http.DescopeClient
import com.descope.internal.others.ConsoleLogger
import com.descope.internal.routes.Auth
import com.descope.internal.routes.EnchantedLink
import com.descope.internal.routes.Flow
import com.descope.internal.routes.MagicLink
import com.descope.internal.routes.OAuth
import com.descope.internal.routes.Otp
import com.descope.internal.routes.Passkey
import com.descope.internal.routes.Password
import com.descope.internal.routes.Push
import com.descope.internal.routes.Sso
import com.descope.internal.routes.Totp
import com.descope.internal.others.with
import com.descope.session.DescopeSessionManager
import com.descope.session.SessionLifecycle
import com.descope.session.SessionStorage
import com.descope.types.DescopeException

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
    val push: DescopePush
    @Deprecated(message = "Use DescopeFlowView instead")
    val flow: DescopeFlow

    internal val config: DescopeConfig
    internal val client: DescopeClient

    init {
        if (Looper.getMainLooper().thread != Thread.currentThread()) {
            throw IllegalStateException("Descope SDK must be initialized on the main thread")
        }
        // init config
        config = DescopeConfig(projectId = projectId)
        configure(config)
        // init logging debug flag
        ConsoleLogger.isApplicationDebuggable = context.applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        // init auth methods
        client = DescopeClient(config, DescopeSystemInfo.getInstance(context))
        auth = Auth(client)
        otp = Otp(client)
        totp = Totp(client)
        magicLink = MagicLink(client)
        enchantedLink = EnchantedLink(client)
        oauth = OAuth(client)
        sso = Sso(client)
        passkey = Passkey(client)
        password = Password(client)
        push = Push(client)
        flow = Flow(client)
        // init session manager
        sessionManager = initDefaultManager(context, config)
    }

    /**
     * Resumes an ongoing authentication that's waiting for an external authentication step.
     *
     * - **Important**: This function must be called on the main thread, or it will throw
     * a [DescopeException.flowSetup] error.
     */
    fun handleUri(uri: Uri): Boolean {
        if (Looper.getMainLooper().thread != Thread.currentThread()) {
            throw DescopeException.flowSetup.with(message = "Descope.handleUri must be called on the main thread")
        }
        return resume(uri)
    }

    // Internal

    /**
     * While the flow is running this is set to a closure with a weak reference to
     * the [DescopeFlowCoordinator] to provide it with the resume URI.
     */
    internal var resume: (Uri) -> Boolean = { false }

    private fun initDefaultManager(context: Context, config: DescopeConfig): DescopeSessionManager {
        val storage = SessionStorage(context.applicationContext, config.projectId, config.logger)
        val lifecycle = SessionLifecycle(auth, config.logger)
        return DescopeSessionManager(storage, lifecycle)
    }

    // SDK information

    companion object {
        /** The Descope SDK name */
        const val NAME = "DescopeAndroid"

        /** The Descope SDK version */
        const val VERSION = "0.19.0"
    }
}
