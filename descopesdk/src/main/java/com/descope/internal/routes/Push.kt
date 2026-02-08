package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopePush

internal class Push(private val client: DescopeClient) : DescopePush {

    override suspend fun enroll(token: String, refreshJwt: String) {
        client.pushEnrollDevice(token = token, device = client.systemInfo.device ?: "Android", refreshJwt = refreshJwt)
    }

    override suspend fun finish(transactionId: String, approved: Boolean, refreshJwt: String) {
        client.pushSignInFinish(transactionId = transactionId, approved = approved, refreshJwt = refreshJwt)
    }
}
