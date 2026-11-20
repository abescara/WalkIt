# Google sign in for a directly installed WalkIt APK

**No Google Play Store upload is required.** Google sign in works in an APK installed directly on a phone. You can first make sign in work while the Google OAuth app is in **Testing**. Changing it to **In Production** is a separate step for a polished public OAuth app; it does not publish WalkIt to the Play Store.

WalkIt already has a Google button and an Android return link. The current blocker is that **Google is disabled in the WalkIt Supabase project** because no Google OAuth client ID and secret have been saved there. The Supabase migration that creates a profile for first-time Google users (`supabase/05_oauth_profiles.sql`) has already run.

## Set up the Google login credentials

1. In [Google Cloud Console](https://console.cloud.google.com/), select the existing **WalkitGMapsAPI** project. The Maps API key you created is for drawing the map; it is **not** the Google login credential.
2. Open **Google Auth Platform → Branding** (or **APIs & Services → OAuth consent screen** if that is what your console shows). Set the app name to **WalkIt**, choose a support email, and fill in the minimum required contact information. In **Audience**, choose **External**. Leave the publishing status as **Testing** for now. Google says apps requesting only basic identity scopes (`openid`, `email`, `profile`) can be accessed without a test-user allowlist even in Testing, though users may see a testing warning. [Google's OAuth app-state guide](https://developers.google.com/identity/protocols/oauth2/production-readiness/overview).
3. Open **Clients → Create client → Web application**. Name it **WalkIt Supabase Login**. Under **Authorized redirect URIs**, add exactly:

   `https://juxnlpazlvjggxmojnoq.supabase.co/auth/v1/callback`

   This URL sends Google back to Supabase. Do **not** put `walkit://auth/callback` in Google's redirect URI field. The current app uses Supabase's browser OAuth flow, so the Web client is the credential needed for this flow. [Supabase Google setup](https://supabase.com/docs/guides/auth/social-sign-in/auth-google).
4. Google will show a **Client ID** and **Client secret**. In [Supabase's Google provider settings](https://supabase.com/dashboard/project/juxnlpazlvjggxmojnoq/auth/providers?provider=Google), paste both into their matching fields, turn on **Enable Sign in with Google**, and click **Save**. Keep the client secret in Supabase; do not put it in the APK, GitHub, or a screenshot.
5. In [Supabase URL Configuration](https://supabase.com/dashboard/project/juxnlpazlvjggxmojnoq/auth/url-configuration), make sure **Redirect URLs** includes exactly `walkit://auth/callback`. This second link sends Supabase back to the installed WalkIt app. The **Site URL** should not be localhost. [Supabase mobile redirect guidance](https://supabase.com/docs/guides/auth/redirect-urls).

## Check it on your phone

1. Install the current [WalkIt APK](../artifacts/WalkIt-debug.apk) directly. You do not need to upload it anywhere.
2. On the Sign Up screen, tap **Google** and choose a Google account. The browser should return to WalkIt and open the map. A first-time Google account should get a profile automatically.
3. Sign out, tap **Google** on Log In, and confirm the same account returns to its existing profile.
4. A testing warning can appear while the OAuth app remains in Testing. If access is denied, check the requested scopes and Audience settings. If the browser does not return to WalkIt, recheck both redirect URLs in steps 3 and 5.

## Later, if you want a fully published Google OAuth app

Google requires a public homepage and privacy policy for production OAuth branding. The homepage must describe WalkIt and link to the privacy policy; Google may require you to verify ownership of the domain and complete brand verification. These pages are **not prerequisites for trying the current basic Google sign-in flow**. When you have them, add their URLs under **Branding** and change **Audience** to **In Production**. This is unrelated to publishing an APK on Google Play. [Google's production OAuth policy](https://developers.google.com/identity/protocols/oauth2/production-readiness/policy-compliance).

## About the Android Maps key and future APKs

The Maps SDK key is separate from the Google login client. The current APK was signed with the developer machine's **debug certificate**, and its Maps key allows `com.walkit.prototype` with that certificate's SHA-1. If you later create a separately signed APK for direct distribution, add that APK's **release signing SHA-1** and package name to the Maps key's Android app restrictions. You can obtain the SHA-1 from its release keystore using `keytool -list -v -keystore <path-to-release-keystore> -alias <alias>`. No Play Store is needed. [Google Maps key restrictions](https://developers.google.com/maps/documentation/android-sdk/get-api-key).

The current Google button uses a browser and `walkit://auth/callback`, not Google's native account chooser. It can be used in a directly installed APK. For a larger public rollout, the app should move to a verified HTTPS Android App Link and an auth code/PKCE flow; those are app changes, separate from the Google Cloud steps above.
