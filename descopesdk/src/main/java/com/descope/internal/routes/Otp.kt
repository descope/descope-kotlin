package com.descope.internal.routes

import com.descope.internal.http.DescopeClient
import com.descope.sdk.DescopeOtp
import com.descope.types.AuthenticationResponse
import com.descope.types.DeliveryMethod
import com.descope.types.SignInOptions
import com.descope.types.SignUpDetails
import com.descope.types.UpdateOptions

internal class Otp(private val client: DescopeClient) : DescopeOtp {

    override suspend fun signUp(method: DeliveryMethod, loginId: String, details: SignUpDetails?): String =
        client.otpSignUp(method, loginId, details).convert(method)

    override suspend fun signIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>?): String =
        client.otpSignIn(method, loginId, options).convert(method)

    override suspend fun signUpOrIn(method: DeliveryMethod, loginId: String, options: List<SignInOptions>?): String =
        client.otpSignUpIn(method, loginId, options).convert(method)

    override suspend fun verify(method: DeliveryMethod, loginId: String, code: String): AuthenticationResponse =
        client.otpVerify(method, loginId, code).convert()

    override suspend fun updateEmail(email: String, loginId: String, refreshJwt: String, options: UpdateOptions?): String =
        client.otpUpdateEmail(email, loginId, refreshJwt, options).convert(DeliveryMethod.Email)

    override suspend fun updatePhone(phone: String, method: DeliveryMethod, loginId: String, refreshJwt: String, options: UpdateOptions?): String =
        client.otpUpdatePhone(phone, method, loginId, refreshJwt, options).convert(method)

}
