# Descope SDK for Android

The Descope SDK for for Android provides convenient access
to the Descope user management and authentication APIs for applications
written for Android. You can read more on the [Descope Website](https://descope.com).

## Setup

Add the following to your `build.gradle` dependencies:

```groovy
implementation 'com.descope:descope-kotlin:0.12.1'
```

## Quickstart

A Descope `Project ID` is required to initialize the SDK. Find it
on the [project page](https://app.descope.com/settings/project) in
the Descope Console.

```kotlin
import com.descope.Descope

// Application on create
override fun onCreate() {
    Descope.setup(this, projectId = "<Your-Project-Id>")

    // Optionally, you can configure your SDK to your needs
    Descope.setup(this, projectId = "<Your-Project-Id>") {
        // set a custom base URL (needs to be set up in the Descope console)
        baseUrl = "https://my.app.com"
        // enable the logger
        if (BuildConfig.DEBUG) {
            logger = DescopeLogger()
        }
    }
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

Make sure you initialize Descope with your application context so that the
session manager implementation can access Android storage and load any
existing session and user data:

```kotlin
Descope.setup(applicationContext, projectId = "<PROJECT-ID>")
```

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
    Descope.setup(this, projectId = "<Your-Project-Id>")
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

It is possible to log out of older sessions or all sessions by providing
the optional `LogoutType` parameter.

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

### (OPTIONAL) Setup #2: Enable App Links for Magic Link and OAuth (social)

Some authentication methods rely on leaving the application's context to authenticate the
user, such as navigating to an identity provider's website to perform OAuth (social) authentication,
or receiving a Magic Link via email or text message. If you do not intend to use these authentication
methods, you can skip this step. Otherwise, in order for the user to get back
to your application, setting up [App Links](https://developer.android.com/training/app-links#android-app-links) is required.
Once you have a domain set up and [verified](https://developer.android.com/training/app-links/verify-android-applinks) for sending App Links,
you'll need to handle the incoming deep links in your app, and resume the flow:

#### Define an Activity to handle the App Link and resume a flow

Any activity can handle an incoming App Link, however in order to resume the flow, the `DescopeFlowView`
used to run the flow must be called with the `resumeFromDeepLink()` function.

_this code example demonstrates how app links can be handled - you're app architecture might differ'_
```kotlin
class FlowRedirectActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // assuming descopeFlowView is a reference to your instance of DescopeFlowView
        intent?.data?.run {
            descopeFlowView.resumeFromDeepLink(this)
        }
    }

    // alternatively you might receive the URI from another activity
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.getStringExtra(descopeFlowUri)?.run {
            // assuming descopeFlowView is a reference to your instance of DescopeFlowView
            descopeFlowView.resumeFromDeepLink(Uri.parse(this))
        }
    }
}
```

#### Add a matching Manifest declaration
```xml
<activity
    android:name=".FlowRedirectActivity"
    android:exported="true">  <!-- exported required for app links -->
    <intent-filter android:autoVerify="true"> <!-- autoVerify required for app links -->
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <!-- replace with your host, the path can change must must be reflected when running the flow -->
        <data android:scheme="https" android:host="<YOUR_HOST_HERE>" android:path="/done" />
    </intent-filter>

    <!-- Optional: App Links are blocked by default on Opera and some other browsers. Add a custom scheme for that use case specifically -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <!-- replace with something unique. this will only be used as a backup for Opera users. -->
        <data android:scheme="myapp" android:host="auth"  />
    </intent-filter>
</activity>
```

### Run a Flow

After completing the prerequisite steps, it is now possible to run a flow.
The flow will run in a dedicated `DescopeFlowView` which receives a `DescopeFlow`
object. The `DescopeFlow` objects defines all of the options available when running a flow.
Read the class documentation for a detailed explanation. The flow needs to reside in your UI in
some form, and to start it, call the `run()` function

```kotlin
descopeFlowView.listener = object : DescopeFlowView.Listener {
    override fun onReady() {
        // present the flow view via animation, or however you see fit
    }

    override fun onSuccess(response: AuthenticationResponse) {
        // optionally hide the flow UI

        // manage the incoming session
        Descope.sessionManager.manageSession(DescopeSession(response))

        // launch the "logged in" UI of your app
    }

    override fun onError(exception: DescopeException) {
        // handle any errors here
    }

    override fun onNavigation(uri: Uri): DescopeFlowView.NavigationStrategy {
        // manage navigation event by deciding whether to open the URI
        // in a custom tab (default behavior), inline, or do nothing.
    }
}

val descopeFlow = DescopeFlow(Uri.parse("<URL_FOR_FLOW_IN_SETUP_#1>"))
// set the OAuth provider ID that is configured to "sign in with Google"
descopeFlow.oauthProvider = OAuthProvider.Google
// set the oauth redirect URI to use your app's deep link 
descopeFlow.oauthRedirect = "<URL_FOR_APP_LINK_IN_SETUP_#2>"
// customize the flow presentation further
descopeFlow.presentation = flowPresentation
// run the flow
descopeFlowView.run(descopeFlow)
```

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
val authURL = Descope.oauth.signUpOrIn(OAuthProvider.Github, redirectUrl = "exampleauthschema://my-app.com/handle-oauth")
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

### Passkeys

Users can authenticate by creating or using a [passkey](https://fidoalliance.org/passkeys/).
Configure your Passkey/WebAuthn settings on the [Descope console](https://app.descope.com/settings/authentication/webauthn).
Make sure it is enabled and that the top level domain is configured correctly.
After that, go through the [Add support for Digital Asset Links](https://developer.android.com/training/sign-in/passkeys#add-support-dal)
setup, as described in the official Google docs, and complete the asset links and manifest preparations.

**Note:** The passkey operations are all suspending functions that perform
network requests before and after displaying the modal authentication view.
It is thus recommended to switch the user interface to a loading state before
calling them, otherwise the user might accidentally interact with the app when
the authentication view is not being displayed.

```kotlin
// Enter loading state...

val authResponse = Descope.passkey.signUpOrIn(this@MyActivity, loginId)
val session = DescopeSession(authResponse)
Descope.sessionManager.manageSession(session)

// Exit loading state...
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

The Descope SDK for Android is licensed for use under the terms and conditions
of the [MIT license Agreement](https://github.com/descope/descope-android/blob/main/LICENSE).
