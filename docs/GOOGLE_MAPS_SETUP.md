# Google Maps and Google sign in setup

The Android app now uses Google Maps SDK for Android. A Google Maps API key is required to display the map. This key is separate from the Google OAuth credentials used for sign in.

## Google Maps

1. In Google Cloud Console, choose or create a project and attach a billing account. Enable **Maps SDK for Android**.
2. Create an API key under **APIs & Services → Credentials**. Restrict its API access to **Maps SDK for Android** and its application access to Android app `com.walkit.prototype` with the debug certificate SHA-1 below. Add the release signing certificate SHA-1 before distributing a release APK.
3. Put `WALKIT_GOOGLE_MAPS_KEY=your-key` in the repository root `local.properties`, or set the environment variable `WALKIT_GOOGLE_MAPS_KEY` before building. `local.properties` is ignored by Git. The build embeds the key in the Android manifest; the Android app restriction is therefore important.
4. Build and install the APK on a device with Google Play services. Confirm that the base map, current location, report photos, and route line render.

Debug package: `com.walkit.prototype`  
Debug SHA-1: `0C:A4:92:44:7F:0E:CF:C1:CA:35:16:4D:76:1B:77:B6:5D:AE:54:E9`

To get the release SHA-1 later, run the Gradle `:app:signingReport` task or inspect the release keystore certificate.

## Google sign in and sign up

Google is currently disabled in the WalkIt Supabase project. Both the Log In and Sign Up Google buttons use the same Supabase OAuth flow; Supabase creates a new account on first use.

1. In the same or another Google Cloud project, configure **Google Auth Platform → Branding and Audience**. If the app is in testing, add the intended test accounts.
2. Under **Clients**, create a **Web application** OAuth client. Set its authorized redirect URI to `https://juxnlpazlvjggxmojnoq.supabase.co/auth/v1/callback`.
3. In Supabase → Authentication → Providers → Google, enter that Web client ID and client secret, enable Google, and save. Do not put the client secret in the app or repository.
4. In Supabase → Authentication → URL Configuration, ensure `walkit://auth/callback` is in the Redirect URLs list.
5. The [`supabase/05_oauth_profiles.sql`](../supabase/05_oauth_profiles.sql) migration has already been run in this project; it gives a first-time Google user a profile and username.
6. Install the current APK and test Google sign in with a test account. The browser should return to WalkIt and open the map.

The Maps API key, Web OAuth client ID, and Web OAuth client secret are distinct values. The client secret belongs only in Supabase.
