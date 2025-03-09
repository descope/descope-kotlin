package com.descope.internal.routes

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import androidx.annotation.RequiresApi
import androidx.credentials.CreateCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialNoCreateOptionException
import androidx.credentials.exceptions.CreateCredentialProviderConfigurationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import androidx.credentials.exceptions.publickeycredential.GetPublicKeyCredentialDomException
import com.descope.internal.http.DescopeClient
import com.descope.internal.others.with
import com.descope.sdk.DescopePasskey
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import org.json.JSONObject
import java.security.MessageDigest

internal class Passkey(override val client: DescopeClient) : Route, DescopePasskey {
    @RequiresApi(Build.VERSION_CODES.P)
    override suspend fun signUp(context: Context, loginId: String, details: SignUpDetails?): AuthenticationResponse {
        val startResponse = client.passkeySignUpStart(loginId, details, getPackageOrigin(context))
        val registerResponse = performRegister(context, startResponse.options)
        val jwtResponse = client.passkeySignUpFinish(startResponse.transactionId, registerResponse)
        return jwtResponse.convert()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override suspend fun signIn(context: Context, loginId: String, options: List<SignInOptions>?): AuthenticationResponse {
        val startResponse = client.passkeySignInStart(loginId, getPackageOrigin(context), options)
        val assertionResponse = performAssertion(context, startResponse.options)
        val jwtResponse = client.passkeySignInFinish(startResponse.transactionId, assertionResponse)
        return jwtResponse.convert()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override suspend fun signUpOrIn(context: Context, loginId: String, options: List<SignInOptions>?): AuthenticationResponse {
        val startResponse = client.passkeySignUpInStart(loginId, getPackageOrigin(context), options)
        val jwtResponse = if (startResponse.create) {
            val registerResponse = performRegister(context, startResponse.options)
            client.passkeySignUpFinish(startResponse.transactionId, registerResponse)
        } else {
            val assertionResponse = performAssertion(context, startResponse.options)
            client.passkeySignInFinish(startResponse.transactionId, assertionResponse)
        }
        return jwtResponse.convert()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override suspend fun add(context: Context, loginId: String, refreshJwt: String) {
        val startResponse = client.passkeyAddStart(loginId, getPackageOrigin(context), refreshJwt)
        val registerResponse = performRegister(context, startResponse.options)
        client.passkeyAddFinish(startResponse.transactionId, registerResponse)
    }

}

private fun convertOptions(options: String): String {
    val root = try {
        JSONObject(options)
    } catch (e: Exception) {
        throw DescopeException.decodeError.with(message = "Invalid passkey options")
    }
    val publicKey = try {
        root.getString("publicKey")
    } catch (e: Exception) {
        throw DescopeException.decodeError.with(message = "Malformed passkey options")
    }
    return publicKey
}

@RequiresApi(Build.VERSION_CODES.P)
internal fun getPackageOrigin(context: Context): String {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    val signers = packageInfo.signingInfo?.apkContentsSigners // nullable according to source code

    if (signers.isNullOrEmpty()) {
        throw DescopeException.passkeyFailed.with(message = "Failed to find signing certificates")
    }

    val cert = signers[0].toByteArray()
    try {
        val md = MessageDigest.getInstance("SHA-256")
        val certHash = md.digest(cert)
        val encoded = Base64.encodeToString(certHash, Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE)
        return "android:apk-key-hash:$encoded"
    } catch (e: Exception) {
        throw DescopeException.passkeyFailed.with(message = "Failed to encode origin")
    }
}

@SuppressLint("PublicKeyCredential")
internal suspend fun performRegister(context: Context, options: String): String {
    val publicKey = convertOptions(options)
    val request = CreatePublicKeyCredentialRequest(publicKey)

    try {
        val credentialManager = CredentialManager.create(context)
        val result = credentialManager.createCredential(context, request as CreateCredentialRequest) as CreatePublicKeyCredentialResponse
        return result.registrationResponseJson
    } catch (e: CreateCredentialCancellationException) {
        throw DescopeException.passkeyCancelled
    } catch (e: CreatePublicKeyCredentialDomException) {
        throw DescopeException.passkeyFailed.with(message = "Error signing registration", cause = e)
    } catch (e: CreateCredentialInterruptedException) {
        throw DescopeException.passkeyFailed.with(message = "Please try again", cause = e)
    } catch (e: CreateCredentialProviderConfigurationException) {
        throw DescopeException.passkeyFailed.with(message = "Application might be improperly configured", cause = e)
    } catch (e: CreateCredentialNoCreateOptionException) {
        throw DescopeException.passkeyFailed.with(message = "No option to create credentials", cause = e)
    } catch (e: CreateCredentialUnknownException) {
        throw DescopeException.passkeyFailed.with(message = "Unknown failure", cause = e)
    } catch (e: Exception) {
        throw DescopeException.passkeyFailed.with(message = "Unexpected failure", cause = e)
    }
}

internal suspend fun performAssertion(context: Context, options: String): String {
    val publicKey = convertOptions(options)
    val option = GetPublicKeyCredentialOption(publicKey)
    val request = GetCredentialRequest(listOf(option))

    try {
        val credentialManager = CredentialManager.create(context)
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential as PublicKeyCredential
        return credential.authenticationResponseJson
    } catch (e: NoCredentialException) {
        throw DescopeException.passkeyNoPasskeys.with(cause = e)
    } catch (e: GetCredentialCancellationException) {
        throw DescopeException.passkeyCancelled
    } catch (e: GetPublicKeyCredentialDomException) {
        throw DescopeException.passkeyFailed.with(message = "Error signing assertion", cause = e)
    } catch (e: GetCredentialInterruptedException) {
        throw DescopeException.passkeyFailed.with(message = "Please try again", cause = e)
    } catch (e: GetCredentialProviderConfigurationException) {
        throw DescopeException.passkeyFailed.with(message = "Application might be improperly configured", cause = e)
    } catch (e: GetCredentialUnknownException) {
        throw DescopeException.passkeyFailed.with(message = "Unknown failure", cause = e)
    } catch (e: Exception) {
        throw DescopeException.passkeyFailed.with(message = "Unexpected failure", cause = e)
    }
}
