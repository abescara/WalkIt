# WalkIt production setup: Google Maps and Google sign in

This guide is for a public release of WalkIt. The current downloadable APK is a **debug build**; its SHA-1 fingerprint and `0.1.0` version are for development. Completing the Google Cloud settings below does not by itself publish a release build.

## 1. Fix the app identity and signing method

1. Decide on the permanent Android package name before release. The repository currently uses `com.walkit.prototype` in `app/build.gradle.kts`. Keep that name only if it is the one you want users to install long term. Changing it later creates a different Android app.
2. Decide how people will install WalkIt:
   - **Google Play:** Set up [Play App Signing](https://developer.android.com/studio/publish/app-signing). Create and protect an upload key. In Play Console → **Release → Setup → App integrity**, copy the **app signing certificate SHA-1**. Google Play signs the app delivered to users with this certificate; the upload certificate is different.
   - **Direct APK distribution:** Create a release keystore in Android Studio, keep it and its passwords backed up privately, and obtain its SHA-1 with `keytool -list -v -keystore <path-to-release-keystore> -alias <alias>`.
3. Increase `versionCode` and choose a public `versionName` in `app/build.gradle.kts` for the first release. Build a **signed release** APK or App Bundle. The debug APK in `artifacts/` is not the release artifact.

Use the SHA-1 of the certificate that signs the **installed app** in the next step. The debug SHA-1 from the earlier setup guide only authorizes APKs built with this developer machine's debug key. See [Android's signing guide](https://developer.android.com/studio/publish/app-signing) and [Google's certificate guide](https://developers.google.com/android/guides/client-auth).

## 2. Configure the production Google Maps key

1. In [Google Cloud Console](https://console.cloud.google.com/), select the WalkitGMapsAPI project (`walkitgmapsapi`) and verify it has billing enabled and **Maps SDK for Android** enabled.
2. In **APIs & Services → Credentials**, create a separate key named **WalkIt Production Android Maps**, or edit the existing key if you want one key for both builds.
3. Under **API restrictions**, allow only **Maps SDK for Android**.
4. Under **Application restrictions**, choose **Android apps**, add the final package name, and enter the release app signing SHA-1 from step 1. If distributing both through Play and as a separately signed direct APK, add each installed app's package and signing SHA-1 combination.
5. Save. Google says restriction changes can take a few minutes to apply. Follow [Google's API key instructions](https://developers.google.com/maps/documentation/android-sdk/get-api-key).
6. Supply this key at build time as `WALKIT_GOOGLE_MAPS_KEY` in the repository root `local.properties` or as an environment variable. The build reads either location and puts the key in the Android manifest. `local.properties` is ignored by Git; keep CI secrets and release keystores outside the repository. Never use the Supabase service role key here.

The Maps key is included in the installed Android app and can be extracted from it. The **Android app and Maps SDK restrictions** protect its use. Monitor usage and billing in Google Cloud. [Maps SDK usage and billing](https://developers.google.com/maps/documentation/android-sdk/usage-and-billing).

## 3. Configure Google sign in for public users

Google sign in uses **different credentials** from the Maps API key. WalkIt currently opens Supabase's Google OAuth page in the browser. For this flow, create a **Web application OAuth client** in Google Cloud; the Android Maps key does not sign users in.

1. Open **Google Auth Platform → Branding** in the same Google Cloud project. Enter the public app name **WalkIt**, a monitored support email, and the requested public homepage and privacy policy URLs. Use pages you control and intend to keep online. Configure **Audience** for **External** users if anyone with a Google account should sign in. In **Data Access**, request only the identity scopes WalkIt needs (`openid`, `email`, `profile`). Google may require brand or scope verification before unrestricted public use; complete any review shown in the console. See [Google's OAuth publishing guide](https://support.google.com/cloud/answer/13461325).
2. In **Google Auth Platform → Clients**, create an OAuth client of type **Web application**. Add this exact **Authorized redirect URI**:

   `https://juxnlpazlvjggxmojnoq.supabase.co/auth/v1/callback`

   This is the Google-to-Supabase callback. Do **not** put `walkit://auth/callback` in Google's redirect URI field. Follow [Supabase's Google provider guide](https://supabase.com/docs/guides/auth/social-sign-in/auth-google).
3. Copy the Web client ID and client secret directly into **Supabase → Authentication → Providers → Google**, enable the provider, and save. Keep the client secret in Supabase only; never place it in the app, Git, or a screenshot.
4. In **Supabase → Authentication → URL Configuration**, add `walkit://auth/callback` to **Redirect URLs**. Set **Site URL** to your real production HTTPS homepage or support page, not localhost. The app explicitly asks Supabase to return to `walkit://auth/callback`; this is the Supabase-to-Android return link. See [Supabase redirect URL guidance](https://supabase.com/docs/guides/auth/redirect-urls).
5. The `supabase/05_oauth_profiles.sql` migration has already been run in the current WalkIt Supabase project. It creates a profile for a first-time Google user.
6. In **Google Auth Platform → Audience**, change publishing status to **In Production** and complete any required verification. Leaving the OAuth app in **Testing** restricts who can sign in. Do not rely on a test-user list for public users.

## 4. Finish the release and check it as a real user

1. Prepare a signed release build with the production Maps key. If using Google Play, distribute the App Bundle through a Play track and install the **Play-signed** app for the final Maps check. A locally signed APK can have a different SHA-1.
2. On a phone, confirm the map loads roads and labels, current location appears after permission is granted, report photo markers open, and the Find a route tab displays its route.
3. Sign out. Use a Google account that is **not** listed as a test user and try the Google button on Sign Up. Confirm that the browser returns to WalkIt, the map opens, and the new profile appears. Sign out and use the same Google account from Log In to confirm repeat sign in.
4. If email/password signup remains available, configure reliable production email delivery and check that its confirmation link opens WalkIt. Remove any localhost URL from Supabase's Site URL and email templates.
5. Check Google Cloud billing, the Maps API key restrictions, Supabase Auth logs, and the app's release crash reports after rollout.

**Current app limitation:** WalkIt returns from OAuth through a custom `walkit://` URI. Before a wider public release, replace that with an Android verified HTTPS App Link and an auth code/PKCE flow so another app cannot claim the same custom URI. The current repository also needs a maintained release signing configuration and a production support/privacy page. Treat this guide as the account setup for production, not a claim that the entire app is launch ready.
