package com.descope.internal.routes

import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopeLogger.Level.Info
import com.descope.sdk.DescopeSso
import com.descope.types.AuthenticationResponse
import com.descope.types.SignInOptions

internal class Sso(override val client: DescopeClient): Route, DescopeSso {

    override suspend fun start(emailOrTenantId: String, redirectUrl: String?, options: List<SignInOptions>?): String =
        client.ssoStart(emailOrTenantId, redirectUrl, options).url

    override suspend fun exchange(code: String): AuthenticationResponse =
        client.ssoExchange(code).convert()

    override suspend fun web(context: Context, emailOrTenantId: String, redirectUrl: String?, options: List<SignInOptions>?, customTabsIntent: CustomTabsIntent?): AuthenticationResponse {
        log(Info, "Starting SSO web authentication")
        val url = start(emailOrTenantId, redirectUrl, options)
        log(Info, "Showing SSO web authentication", url)
        val code = performWebAuthentication(context, url, customTabsIntent)
        log(Info, "Finishing SSO web authentication")
        return exchange(code)
    }

}
