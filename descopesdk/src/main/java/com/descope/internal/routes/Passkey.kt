package com.descope.internal.routes

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.descope.internal.http.DescopeClient
import com.descope.internal.others.activityHelper
import com.descope.internal.others.decodeBase64
import com.descope.internal.others.stringOrEmptyAsNull
import com.descope.internal.others.toBase64
import com.descope.internal.others.with
import com.descope.sdk.DescopePasskey
import com.descope.types.AuthenticationResponse
import com.descope.types.DescopeException
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.google.android.gms.fido.Fido
import com.google.android.gms.fido.fido2.api.common.Attachment
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAssertionResponse
import com.google.android.gms.fido.fido2.api.common.AuthenticatorAttestationResponse
import com.google.android.gms.fido.fido2.api.common.AuthenticatorErrorResponse
import com.google.android.gms.fido.fido2.api.common.AuthenticatorSelectionCriteria
import com.google.android.gms.fido.fido2.api.common.ErrorCode.ABORT_ERR
import com.google.android.gms.fido.fido2.api.common.ErrorCode.TIMEOUT_ERR
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredential
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialCreationOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialDescriptor
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialParameters
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRequestOptions
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialRpEntity
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialType
import com.google.android.gms.fido.fido2.api.common.PublicKeyCredentialUserEntity
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class Passkey(override val client: DescopeClient) : Route, DescopePasskey {

    override suspend fun signUp(context: Context, loginId: String, details: SignUpDetails?): AuthenticationResponse {
        val startResponse = client.passkeySignUpStart(loginId, details, getPackageOrigin(context))
        val pendingIntent = performRegister(context, startResponse.options)
        val result = suspendCoroutine { continuation ->
            activityHelper.startHelperActivity(context, pendingIntent) { code, intent ->
                continuation.resume(Pair(code, intent))
            }
        }
        return finishSignUp(startResponse.transactionId, result.first, result.second)
    }

    override suspend fun signIn(context: Context, loginId: String, options: List<SignInOptions>?): AuthenticationResponse {
        val startResponse = client.passkeySignInStart(loginId, getPackageOrigin(context), options)
        val pendingIntent = performAssertion(context, startResponse.options)
        val result = suspendCoroutine { continuation ->
            activityHelper.startHelperActivity(context, pendingIntent) { code, intent ->
                continuation.resume(Pair(code, intent))
            }
        }
        return finishSignIn(startResponse.transactionId, result.first, result.second)
    }

    override suspend fun signUpOrIn(context: Context, loginId: String, options: List<SignInOptions>?): AuthenticationResponse {
        val startResponse = client.passkeySignUpInStart(loginId, getPackageOrigin(context), options)
        val pendingIntent = if (startResponse.create) {
            performRegister(context, startResponse.options)
        } else {
            performAssertion(context, startResponse.options)
        }
        val result = suspendCoroutine { continuation ->
            activityHelper.startHelperActivity(context, pendingIntent) { code, intent ->
                continuation.resume(Pair(code, intent))
            }
        }
        return if (startResponse.create) finishSignUp(startResponse.transactionId, result.first, result.second)
        else finishSignIn(startResponse.transactionId, result.first, result.second)
    }

    override suspend fun add(context: Context, loginId: String, refreshJwt: String) {
        val startResponse = client.passkeyAddStart(loginId, getPackageOrigin(context), refreshJwt)
        val pendingIntent = performRegister(context, startResponse.options)
        val result = suspendCoroutine { cont ->
            activityHelper.startHelperActivity(context, pendingIntent) { code, intent ->
                cont.resume(Pair(code, intent))
            }
        }
        val registerResponse = prepareRegisterResponse(result.first, result.second)
        client.passkeyAddFinish(startResponse.transactionId, registerResponse)
    }

    private suspend fun finishSignUp(transactionId: String, resultCode: Int, intent: Intent?): AuthenticationResponse {
        val jsonResponse = prepareRegisterResponse(resultCode, intent)
        val jwtResponse = client.passkeySignUpFinish(transactionId, jsonResponse)
        return jwtResponse.convert()
    }

    private suspend fun finishSignIn(transactionId: String, resultCode: Int, intent: Intent?): AuthenticationResponse {
        val jsonResponse = prepareAssertionResponse(resultCode, intent)
        val jwtResponse = client.passkeySignInFinish(transactionId, jsonResponse)
        return jwtResponse.convert()
    }
}

// Fido2 Helpers

const val RESULT_CANCELED = 0

private suspend fun performRegister(context: Context, options: String): PendingIntent {
    val client = Fido.getFido2ApiClient(context)
    val opts = parsePublicKeyCredentialCreationOptions(convertOptions(options))
    val task = client.getRegisterPendingIntent(opts)
    return task.await()
}

private suspend fun performAssertion(context: Context, options: String): PendingIntent {
    val client = Fido.getFido2ApiClient(context)
    val opts = parsePublicKeyCredentialRequestOptions(convertOptions(options))
    val task = client.getSignPendingIntent(opts)
    return task.await()
}

private fun prepareRegisterResponse(resultCode: Int, intent: Intent?): String {
    val credential = extractCredential(resultCode, intent)
    val rawId = credential.rawId.toBase64()
    val response = credential.response as AuthenticatorAttestationResponse
    return JSONObject().apply {
        put("id", rawId)
        put("type", PublicKeyCredentialType.PUBLIC_KEY.toString())
        put("rawId", rawId)
        put("response", JSONObject().apply {
            put("clientDataJson", response.clientDataJSON.toBase64())
            put("attestationObject", response.attestationObject.toBase64())
        })
    }.toString()
}

private fun prepareAssertionResponse(resultCode: Int, intent: Intent?): String {
    val credential = extractCredential(resultCode, intent)
    val rawId = credential.rawId.toBase64()
    val response = credential.response as AuthenticatorAssertionResponse
    return JSONObject().apply {
        put("id", rawId)
        put("type", PublicKeyCredentialType.PUBLIC_KEY.toString())
        put("rawId", rawId)
        put("response", JSONObject().apply {
            put("clientDataJson", response.clientDataJSON.toBase64())
            put("authenticatorData", response.authenticatorData.toBase64())
            put("signature", response.signature.toBase64())
            response.userHandle?.let { put("userHandle", it.toBase64()) }
        })
    }.toString()
}

private fun extractCredential(resultCode: Int, intent: Intent?): PublicKeyCredential {
    // check general response
    if (resultCode == RESULT_CANCELED) throw DescopeException.passkeyCancelled
    if (intent == null) throw DescopeException.passkeyFailed.with(message = "Null intent received from ")

    // get the credential from the intent extra
    val credential = try {
        val byteArray = intent.getByteArrayExtra("FIDO2_CREDENTIAL_EXTRA")!!
        PublicKeyCredential.deserializeFromBytes(byteArray)
    } catch (e: Exception) {
        throw DescopeException.passkeyFailed.with(message = "Failed to extract credential from intent", cause = e)
    }

    // check for any logical failures
    (credential.response as? AuthenticatorErrorResponse)?.run {
        when (errorCode) {
            ABORT_ERR -> throw DescopeException.passkeyCancelled
            TIMEOUT_ERR -> throw DescopeException.passkeyCancelled.with(message = "The operation timed out")
            else -> throw DescopeException.passkeyFailed.with("Passkey authentication failed (${errorCode.name}: $errorMessage)")
        }
    }

    return credential
}

// JSON Parsing

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

private fun parsePublicKeyCredentialCreationOptions(options: String): PublicKeyCredentialCreationOptions {
    val json = JSONObject(options)
    return PublicKeyCredentialCreationOptions.Builder()
        .setUser(parseUser(json.getJSONObject("user")))
        .setChallenge(json.getString("challenge").decodeBase64())
        .setParameters(parseParameters(json.getJSONArray("pubKeyCredParams")))
        .setTimeoutSeconds(json.getDouble("timeout"))
        .setExcludeList(parseCredentialDescriptors(json.getJSONArray("excludeCredentials")))
        .setAuthenticatorSelection(parseSelection(json.getJSONObject("authenticatorSelection")))
        .setRp(parseRp(json.getJSONObject("rp")))
        .build()
}

private fun parsePublicKeyCredentialRequestOptions(options: String): PublicKeyCredentialRequestOptions {
    val json = JSONObject(options)
    return PublicKeyCredentialRequestOptions.Builder()
        .setChallenge(json.getString("challenge").decodeBase64())
        .setAllowList(parseCredentialDescriptors(json.getJSONArray("allowCredentials")))
        .setRpId(json.getString("rpId"))
        .setTimeoutSeconds(json.getDouble("timeout"))
        .build()
}

private fun parseUser(jsonObject: JSONObject) = PublicKeyCredentialUserEntity(
    jsonObject.getString("id").decodeBase64(),
    jsonObject.getString("name"),
    "", // icon
    jsonObject.stringOrEmptyAsNull("displayName") ?: ""
)

private fun parseParameters(jsonArray: JSONArray) = mutableListOf<PublicKeyCredentialParameters>().apply {
    for (i in 0 until jsonArray.length()) {
        val jsonObject = jsonArray.getJSONObject(i)
        add(PublicKeyCredentialParameters(jsonObject.getString("type"), jsonObject.getInt("alg")))
    }
}

private fun parseCredentialDescriptors(jsonArray: JSONArray) = mutableListOf<PublicKeyCredentialDescriptor>().apply {
    for (i in 0 until jsonArray.length()) {
        val jsonObject = jsonArray.getJSONObject(i)
        add(
            PublicKeyCredentialDescriptor(
                PublicKeyCredentialType.PUBLIC_KEY.toString(),
                jsonObject.getString("id").decodeBase64(),
                null
            )
        )
    }
}

private fun parseSelection(jsonObject: JSONObject) = AuthenticatorSelectionCriteria.Builder().run {
    jsonObject.stringOrEmptyAsNull("authenticatorAttachment")?.let {
        setAttachment(Attachment.fromString(it))
    }
    build()
}

private fun parseRp(jsonObject: JSONObject) = PublicKeyCredentialRpEntity(
    jsonObject.getString("id"),
    jsonObject.getString("name"),
    null
)

// Android Helpers 

private fun getPackageOrigin(context: Context): String {
    @Suppress("DEPRECATION")
    val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        packageInfo.signingInfo.apkContentsSigners // nullable according to source code
    } else {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        packageInfo.signatures
    }

    if (signers.isNullOrEmpty()) {
        throw DescopeException.passkeyFailed.with(message = "Failed to find signing certificates")
    }

    val cert = signers[0].toByteArray()
    try {
        val md = MessageDigest.getInstance("SHA-256")
        val certHash = md.digest(cert)
        val encoded = certHash.toBase64()
        return "android:apk-key-hash:$encoded"
    } catch (e: Exception) {
        throw DescopeException.passkeyFailed.with(message = "Failed to encode origin")
    }
}

// Task Extensions

private suspend fun <T> Task<T>.await(): T {
    // fast path
    if (isComplete) {
        val e = exception
        return if (e == null) {
            if (isCanceled) {
                throw CancellationException("Task $this was cancelled normally.")
            } else {
                result as T
            }
        } else {
            throw e
        }
    }

    return suspendCancellableCoroutine { cont ->
        // Run the callback directly to avoid unnecessarily scheduling on the main thread.
        addOnCompleteListener(DirectExecutor) {
            val e = it.exception
            if (e == null) {
                if (it.isCanceled) cont.cancel() else cont.resume(it.result as T)
            } else {
                cont.resumeWithException(e)
            }
        }
    }
}

private object DirectExecutor : Executor {
    override fun execute(r: Runnable) {
        r.run()
    }
}
