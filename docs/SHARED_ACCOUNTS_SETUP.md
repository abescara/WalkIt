# Shared accounts setup

The Android app connects to the WalkIt Supabase project at `https://juxnlpazlvjggxmojnoq.supabase.co`. It uses the public project key and Supabase Auth for email/password sign in. The key is safe to include in a client app because access to tables is controlled by row policies. Never put the service role key or database password in the app.

Existing device-only accounts cannot be copied into Supabase as passwords because their password hashes use a separate scheme. Users should register again on the shared service.

1. In **Authentication → Providers → Email**, enable email and password. If email confirmation remains on, users must confirm their email before logging in.
2. In **Authentication → URL Configuration**, set the **Site URL** to a non-localhost URL such as `https://github.com/abescara/WalkIt`. Add **`walkit://auth/callback`** to **Redirect URLs**. New WalkIt signup emails and social sign-ins request that app link. Old emails retain their original destination; send a fresh confirmation email to test. A localhost destination in a customized email template must also be removed.
3. Run [`supabase/schema.sql`](../supabase/schema.sql) in the SQL Editor once. This project already completed that step.
4. Run [`supabase/02_profile_trigger.sql`](../supabase/02_profile_trigger.sql) in the SQL Editor once. This project has completed that step. The trigger creates a profile when a user signs up, even when email confirmation is enabled.
5. Run [`supabase/03_forum.sql`](../supabase/03_forum.sql) in the SQL Editor to enable shared forum posts and replies. The app shows a setup message until this migration is applied.
6. Run [`supabase/04_shared_map_reports.sql`](../supabase/04_shared_map_reports.sql) in the SQL Editor to enable shared report photos and map discussions. The app key cannot create the tables or Storage bucket.
7. Install the main branch APK on two devices, register different email addresses, confirm each email, then post a report with a photo on one device. Confirm its marker, photos, and discussion appear on the other device.
8. Run [`supabase/05_oauth_profiles.sql`](../supabase/05_oauth_profiles.sql) in the SQL Editor before enabling Google or Apple. It gives social users a valid profile and username at signup.
9. Run [`supabase/06_friends.sql`](../supabase/06_friends.sql) in the SQL Editor to enable friend requests and accepted friendships. Until this is applied, the Friends tab shows a setup message.

## Google and Apple sign-in

The app opens the provider's sign-in page in the phone browser and returns to WalkIt through `walkit://auth/callback`. Both providers require their own credentials in **Authentication → Providers**. The public app key cannot create those credentials.

- **Google:** In Google Cloud Console, create an OAuth client for a **Web application**. Add `https://juxnlpazlvjggxmojnoq.supabase.co/auth/v1/callback` as an authorized redirect URI. Paste its client ID and secret into Supabase → Authentication → Providers → Google, then enable and save the provider. Configure the Google consent screen for your test users if the app is still in testing.
- **Apple:** In Apple Developer, create a Services ID for Sign in with Apple and an authentication key. Set Apple's return URL to `https://juxnlpazlvjggxmojnoq.supabase.co/auth/v1/callback`. Add the Services ID and key details in Supabase → Authentication → Providers → Apple, then enable and save. Apple Developer membership is required to configure this provider.

Test each provider on an installed APK after enabling it. If a provider is disabled or credentials are incomplete, Supabase will show an error instead of signing in.

## Confirmation email

In Supabase → Authentication → Email Templates → **Confirm signup**, set a subject such as **Confirm your WalkIt email**, then paste [`supabase/confirm_signup_email.html`](../supabase/confirm_signup_email.html) as the HTML body. It uses `{{ .ConfirmationURL }}`, so Supabase keeps the actual confirmation token and the app redirect requested at signup. Save the template and register a new test account to inspect the email and return link. Do not replace `{{ .ConfirmationURL }}` with a fixed localhost URL.

The schema uses authenticated row policies. Reports and their photos are saved locally first, then uploaded to Supabase; failed uploads retry after the next login. Signed-in users can view report markers, photos, and comments from other users, and add comments with optional photos. The leaderboard grants 10 points for a synced report and 2 for a synced comment. Sharing requires a network connection and migration 04.
