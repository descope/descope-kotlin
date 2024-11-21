package com.descope.internal.routes

import android.content.Context
import android.net.Uri
import com.descope.android.defaultCustomTabIntent
import com.descope.internal.http.DescopeClient
import com.descope.internal.others.toBase64
import com.descope.internal.others.with
import com.descope.sdk.DescopeFlow
import com.descope.sdk.DescopeLogger.Level.Info
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
import java.security.MessageDigest
import kotlin.random.Random

internal class Flow(
    override val client: DescopeClient
) : Route, DescopeFlow {

    override var currentRunner: DescopeFlow.Runner? = null
        private set

    override fun create(flowUrl: String, deepLinkUrl: String, backupCustomScheme: String?): DescopeFlow.Runner {
        val runner = FlowRunner(flowUrl, deepLinkUrl, backupCustomScheme)
        currentRunner = runner
        return runner
    }

    inner class FlowRunner(
        private val flowUrl: String,
        private val deepLinkUrl: String,
        private val backupCustomScheme: String?,
    ) : DescopeFlow.Runner {

        private lateinit var codeVerifier: String

        override var flowPresentation: DescopeFlow.Presentation? = null
        override var flowAuthentication: DescopeFlow.Authentication? = null

        override suspend fun start(context: Context) {
            log(Info, "Starting flow", flowUrl)
            val codeChallenge = initVerifierAndChallenge()
            flowAuthentication?.run { client.flowPrime(codeChallenge, flowId, refreshJwt) }
            startFlowViaBrowser(codeChallenge, context)
        }

        override fun resume(context: Context, incomingUriString: String) {
            // create the redirect flow URL by copying all url parameters received from the incoming URI
            val incomingUri = Uri.parse(incomingUriString)
            val uriBuilder = Uri.parse(flowUrl).buildUpon()
            incomingUri.queryParameterNames.forEach { uriBuilder.appendQueryParameter(it, incomingUri.getQueryParameter(it)) }
            val uri = uriBuilder.build()

            // launch via chrome custom tabs
            launchUri(context, uri)
        }

        override suspend fun exchange(incomingUri: Uri): AuthenticationResponse {
            // make sure start has been called
            if (!this::codeVerifier.isInitialized) throw DescopeException.flowFailed.with(desc = "`start(context)` must be called before exchange")

            // get the `code` url param from the incoming uri and exchange it
            val authorizationCode = incomingUri.getQueryParameter("code") ?: throw DescopeException.flowFailed.with(desc = "No code parameter on incoming URI")
            log(Info, "Exchanging flow authorization code for session", authorizationCode)
            if (currentRunner === this) currentRunner = null
            return client.flowExchange(authorizationCode, codeVerifier).convert()
        }

        // Internal

        private fun initVerifierAndChallenge(): String {
            // create some random bytes
            val randomBytes = ByteArray(32)
            Random.nextBytes(randomBytes)

            // codeVerifier == base64(randomBytes)
            codeVerifier = randomBytes.toBase64()

            // hash bytes using sha256
            val md = MessageDigest.getInstance("SHA-256")
            val hashed = md.digest(randomBytes)

            // codeChallenge == base64(sha256(randomBytes))
            return hashed.toBase64()
        }

        private fun startFlowViaBrowser(codeChallenge: String, context: Context) {
            val uriBuilder = Uri.parse(flowUrl).buildUpon()
                .appendQueryParameter("ra-callback", deepLinkUrl)
                .appendQueryParameter("ra-challenge", codeChallenge)
                .appendQueryParameter("ra-initiator", "android")
            backupCustomScheme?.let {
                uriBuilder.appendQueryParameter("ra-backup-callback", it)
            }
            val uri = uriBuilder.build()

            // launch via chrome custom tabs
            launchUri(context, uri)
        }

        private fun launchUri(context: Context, uri: Uri) {
            try {
                val customTabsIntent = flowPresentation?.createCustomTabsIntent(context) ?: defaultCustomTabIntent()
                customTabsIntent.launchUrl(context, uri)
            } catch (e: Exception) {
                throw DescopeException.browserError.with(cause = e)
            }
        }

    }

}
