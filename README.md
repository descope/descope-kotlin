# Descope SDK for Android

The Descope SDK for for Android provides convenient access
to the Descope user management and authentication APIs for applications
written for Android. You can read more on the [Descope Website](https://descope.com).

## Setup

Add the following to your `build.gradle` dependencies:

```groovy
implementation 'com.descope:descope-kotlin:0.9.5'
```

## Quickstart

A Descope `Project ID` is required to initialize the SDK. Find it
on the [project page](https://app.descope.com/settings/project) in
the Descope Console.

```kotlin
import com.descope.Descope
import com.descope.sdk.DescopeConfig

// Application on create
override fun onCreate() {
    Descope.projectId = "<Your-Project-Id>"
}
```

Authenticate the user in your application by starting one of the
authentication methods. For example, let's use OTP via email:

```kotlin
// sends an OTP code to the given email address
Descope.otp.signUp(method = DeliveryMethod.Email, loginId = "andy@example.com")
```

Finish the authentication by verifying the OTP code the user entered:

```kotlin
// if the user entered the right code the authentication is successful
val authResponse = Descope.otp.verify(
    method = DeliveryMethod.Email,
    loginId = "andy@example.com",
    code = code
)

// we create a DescopeSession object that represents an authenticated user session
val session = DescopeSession(authResponse)

// the session manager automatically takes care of persisting the session
// and refreshing it as needed
Descope.sessionManager.manageSession(session)
```

On the next application launch check if there's a logged in user to
decide which screen to show:

```kotlin
// check if we have a valid session from a previous launch and that it hasn't expired yet
if (Descope.sessionManager.session?.refreshToken?.isExpired == true) {
    // Show main UI
} else {
    // Show login UI
}
```

Use the active session to authenticate outgoing API requests to the
application's backend:

```kotlin
val connection = url.openConnection() as HttpsURLConnection
connection.setAuthorization(Descope.sessionManager)
```

## Session Management

The `DescopeSessionManager` class is used to manage an authenticated
user session for an application.

The session manager takes care of loading and saving the session as well
as ensuring that it's refreshed when needed. For the default instances of
the `DescopeSessionManager` class this means using the `EncryptedSharedPreferences`
for secure storage of the session and refreshing it a short while before it expires.

Once the user completes a sign in flow successfully you should set the
`DescopeSession` object as the active session of the session manager.

```kotlin
val authResponse = Descope.otp.verify(DeliverMethod.Email, "andy@example.com", "123456")
val session = DescopeSession(authResponse)
Descope.sessionManager.manageSession(session)
```

The session manager can then be used at any time to ensure the session
is valid and to authenticate outgoing requests to your backend with a
bearer token authorization header.

```kotlin
val connection = url.openConnection() as HttpsURLConnection
connection.setAuthorization(Descope.sessionManager)
```

If your backend uses a different authorization mechanism you can of course
use the session JWT directly instead of the extension function. You can either
add another extension function on `URLRequest` such as the one above, or you
can do the following.

```kotlin
Descope.sessionManager.refreshSessionIfNeeded()
Descope.sessionManager.session?.sessionJwt?.apply {
    connection.setRequestProperty("X-Auth-Token", this)
} ?: throw ServerError.unauthorized
```

When the application is relaunched the `DescopeSessionManager` loads any
existing session automatically, so you can check straight away if there's
an authenticated user.

```kotlin
// Application class onCreate
override fun onCreate() {
    super.onCreate()
    Descope.projectId = "..."
    Descope.sessionManager.session?.run {
        print("User is logged in: $this")
    }
}
```

When the user wants to sign out of the application we revoke the
active session and clear it from the session manager:

```kotlin
 Descope.sessionManager.session?.refreshJwt?.run {
    Descope.auth.logout(this)
    Descope.sessionManager.clearSession()
}
```

You can customize how the `DescopeSessionManager` behaves by using
your own `storage` and `lifecycle` objects. See the documentation
for more details.

## Running Flows

We can authenticate users by building and running Flows. Flows are built in the Descope 
[flow editor](https://app.descope.com/flows). The editor allows you to easily
define both the behavior and the UI that take the user through their
authentication journey. Read more about it in the  Descope
[getting started](https://docs.descope.com/build/guides/gettingstarted/) guide.

### Setup #1: Define and host your flow

Before we can run a flow, it must first be defined and hosted. Every project
comes with predefined flows out of the box. You can customize your flows to suit your needs
and host it. Follow
the [getting started](https://docs.descope.com/build/guides/gettingstarted/) guide for more details.

### Setup #2: Enable App Links

Running a flow via the Kotlin SDK requires setting up [App Links](https://developer.android.com/training/app-links#android-app-links).
This is essential for the SDK to be notified when the user has successfully
authenticated using a flow. Once you have a domain set up and 
[verified](https://developer.android.com/training/app-links/verify-android-applinks)
for sending App Links, you'll need to handle the incoming deep links in your app:

#### Define an Activity to handle the App Link sent at the end of a flow
_this code example demonstrates how app links should be handled - you can customize it to fit your app_
```kotlin
class FlowDoneActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incomingUri: Uri = intent?.data ?: return // The incoming App link
    
        // `exchange` is a suspended function. 
        // Use whichever scope makes sense in your app or keep the global scope
        GlobalScope.launch(Dispatchers.Main) {
            try {
                // exchange the incoming URI for a session
                val authResponse = Descope.flow.currentRunner?.exchange(incomingUri) ?: throw Exception("Flow is not running")
                val session = DescopeSession(authResponse)
                Descope.sessionManager.manageSession(session)
    
                // Show the post-authentication screen, for example
                startActivity(Intent(this@FlowDoneActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                })
            } catch (e: Exception) {
                // Handle errors here
            }
            finish() // There's no UI for this Activity, it just handles the logic
        }
    }
}
```

#### Add a matching Manifest declaration
```xml
<activity
    android:name=".FlowDoneActivity"
    android:exported="true">  <!-- exported required for app links -->
    <intent-filter android:autoVerify="true"> <!-- autoVerify required for app links -->
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <!-- replace with your host, the path can change must must be reflected when running the flow -->
        <data android:scheme="https" android:host="<YOUR_HOST_HERE>" android:path="/done" />
    </intent-filter>
</activity>
```

### (OPTIONAL) Setup #3: Support Magic Link Redirects 

Supporting Magic Link authentication in flows requires adding another path entry to the [App Links](https://developer.android.com/training/app-links#android-app-links).
This is essentially the same as the app link from the [previous setup step](#setup-2-enable-app-links),
with different handling logic:

#### Define an Activity to handle the App Link sent at the end of a flow
_this code example demonstrates how app links should be handled - you can customize it to fit your app_
```kotlin
class MagicLinkRedirectActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incomingUri: Uri = intent?.data ?: return // The incoming App link

        // We need to relaunch the Activity that started the flow where the `resume` method needs to be called.
        // It should be a single top activity so that the user won't 
        // experience any weird behavior / duplicate chrome tabs
        startActivity(Intent(this@MagicLinkRedirectActivity, AuthActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or  Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("descopeFlowUri", incomingUri.toString()) // Pass the URI to the flow
        })
    }
}

// This line should be called from a new instance of the single-top
// activity that started the flow (in our case `AuthActivity`).
Descope.flow.currentRunner?.resume(this@AuthActivity, incomingUri)
```

#### Add a matching Manifest declaration
```xml
<activity
    android:name=".MagicLinkRedirectActivity"
    android:exported="true">  <!-- exported required for app links -->
    <intent-filter android:autoVerify="true"> <!-- autoVerify required for app links -->
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <!-- this is exactly the same setup we performed in setup #2, with a different path to differentiate between them -->
        <data android:scheme="https" android:host="<YOUR_HOST_HERE>" android:path="/magiclink" />
    </intent-filter>
</activity>
```

### Run a Flow

After completing the prerequisite steps, it is now possible to run a flow.
The flow will run in a Chrome [Custom Tab](https://developer.chrome.com/docs/android/custom-tabs/).
Make sure the Activity running the flow is a `SINGLE_TOP` activity, to avoid any
unexpected UX. Run the flow by creating a `DescopeFlow.Runner`:

```kotlin
Descope.flow.create(
    flowUrl = "<URL_FOR_FLOW_IN_SETUP_#1>",
    deepLinkUrl = "<URL_FOR_APP_LINK_IN_SETUP_#2>",
).start(this@MainActivity)
```

When supporting Magic Links the `resume` function must be called. In your authentication Activity
inside the `onCreate` method:

```kotlin
intent?.getStringExtra("descopeFlowUri")?.run {
    Descope.flow.currentRunner?.resume(this@AuthActivity, this)
}
```

The flow will finish by redirecting to the App Link provided to the `deepLinkUrl` parameter.
When receiving the App Link pass the URI to the `exchange` method:

```kotlin
val authResponse = Descope.flow.currentRunner?.exchange(incomingUri) ?: throw Exception("Flow is not running")
val session = DescopeSession(authResponse)
Descope.sessionManager.manageSession(session)
```

See the [app link setup](#setup-2-enable-app-links) for more details.

## Authentication Methods

We can authenticate users by using any combination of the authentication methods
supported by this SDK.
Here are some examples for how to authenticate users:

### OTP Authentication

Send a user a one-time password (OTP) using your preferred delivery
method (_email / SMS_). An email address or phone number must be
provided accordingly.

The user can either `sign up`, `sign in` or `sign up or in`

```kotlin
// Every user must have a loginId. All other user details are optional:
Descope.otp.signUp(
    DeliveryMethod.Email, "andy@example.com", SignUpDetails(
        name = "Andy Rhoads"
    )
)
```

The user will receive a code using the selected delivery method. Verify
that code using:

```kotlin
val authResponse = Descope.otp.verify(DeliveryMethod.Email, "andy@example.com", "123456")
```

### Magic Link

Send a user a Magic Link using your preferred delivery method (_email / SMS_).
The Magic Link will redirect the user to page where the its token needs
to be verified. This redirection can be configured in code, or globally
in the [Descope Console](https://app.descope.com/settings/authentication/magiclink)

The user can either `sign up`, `sign in` or `sign up or in`

```kotlin
// If configured globally, the redirect URI is optional. If provided however, it will be used
// instead of any global configuration
Descope.magiclink.signUp(DeliveryMethod.Email, "andy@example.com")
```

To verify a magic link, your redirect page must call the validation function
on the token (`t`) parameter (`https://your-redirect-address.com/verify?t=<token>`):

```kotlin
val authResponse = Descope.magiclink.verify("<token>")
```

### OAuth

Users can authenticate using their social logins, using the OAuth protocol.
Configure your OAuth settings on the [Descope console](https://app.descope.com/settings/authentication/social).
To start a flow call:

```kotlin
// Choose an oauth provider out of the supported providers
// If configured globally, the redirect URL is optional. If provided however, it will be used
// instead of any global configuration.
// Redirect the user to the returned URL to start the OAuth redirect chain
val authURL = Descope.oauth.start(OAuthProvider.Github, redirectUrl = "exampleauthschema://my-app.com/handle-oauth")
```

Take the generated URL and authenticate the user using `Chrome Custom Tabs`
The user will authenticate with the authentication provider, and will be
redirected back to the redirect URL, with an appended `code` HTTP URL parameter.
Exchange it to validate the user:

```kotlin
// Catch the redirect using a dedicated deep link Activity
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val incomingUri: Uri = intent?.data ?: return
    val code = incomingUri.getQueryParameter("code")

    GlobalScope.launch {
        // Exchange code for session
        val authResponse = Descope.oauth.exchange(code)
        val session = DescopeSession(authResponse)
        Descope.sessionManager.manageSession(session)
    }
}
```

### SSO/SAML

Users can authenticate to a specific tenant using SAML or Single Sign On.
Configure your SSO/SAML settings on the [Descope console](https://app.descope.com/settings/authentication/sso).
To start a flow call:

```kotlin
// Choose which tenant to log into
// If configured globally, the return URL is optional. If provided however, it will be used
// instead of any global configuration.
// Redirect the user to the returned URL to start the SSO/SAML redirect chain
val authURL = Descope.sso.start(emailOrTenantId = "my-tenant-ID", redirectUrl = "exampleauthschema://my-app.com/handle-saml")
```

Take the generated URL and authenticate the user using `Chrome Custom Tabs`
The user will authenticate with the authentication provider, and will be
redirected back to the redirect URL, with an appended `code` HTTP URL parameter.
Exchange it to validate the user:

```kotlin
// Catch the redirect using a dedicated deep link Activity
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val incomingUri: Uri = intent?.data ?: return
    val code = incomingUri.getQueryParameter("code")

    GlobalScope.launch {
        // Exchange code for session
        val authResponse = Descope.sso.exchange(code)
        val session = DescopeSession(authResponse)
        Descope.sessionManager.manageSession(session)
    }
}
```

### TOTP Authentication

The user can authenticate using an authenticator app, such as Google Authenticator.
Sign up like you would using any other authentication method. The sign up response
will then contain a QR code `image` that can be displayed to the user to scan using
their mobile device camera app, or the user can enter the `key` manually or click
on the link provided by the `provisioningURL`.

Existing users can add TOTP using the `update` function.

```kotlin
// Every user must have a loginId. All other user information is optional
val totpResponse = Descope.totp.signUp(loginId = "andy@example.com")

// Use one of the provided options to have the user add their credentials to the authenticator
// totpResponse.provisioningURL
// totpResponse.image
// totpResponse.key
```

There are 3 different ways to allow the user to save their credentials in their
authenticator app - either by clicking the provisioning URL, scanning the QR
image or inserting the key manually. After that, signing in is done using the
code the app produces.

```kotlin
val authResponse = Descope.totp.verify(loginId = "andy@example.com", code = "987654")
```

### Password Authentication

Authenticate users using a password.

#### Sign Up with Password

To create a new user that can later sign in with a password:

```kotlin
val authResponse = Descope.password.signUp(
    "andy@example.com",
    "securePassword123!",
    SignUpDetails(
        name = "Andy Rhoads"
    )
)
```

#### Sign In with Password

Authenticate an existing user using a password:

```kotlin
val authResponse = Descope.password.signIn(
    "andy@example.com",
    "securePassword123!"
)
```

#### Update Password

If you need to update a user's password:

```kotlin
Descope.password.update(
    "andy@example.com",
    "newSecurePassword456!",
    "user-refresh-jwt"
)
```

#### Replace Password

To replace a user's password by providing their current password:

```kotlin
val authResponse = Descope.password.replace(
    "andy@example.com",
    "securePassword123!",
    "newSecurePassword456!"
)
```

#### Send Password Reset Email

Initiate a password reset by sending an email:

```kotlin
Descope.password.sendReset(
    "andy@example.com",
    "exampleauthschema://my-app.com/handle-reset"
)
```

## Additional Information

To learn more please see the [Descope Documentation and API reference page](https://docs.descope.com/).

## Contact Us

If you need help you can email [Descope Support](mailto:support@descope.com)

## License

The Descope SDK for Flutter is licensed for use under the terms and conditions
of the [MIT license Agreement](https://github.com/descope/descope-android/blob/main/LICENSE).
