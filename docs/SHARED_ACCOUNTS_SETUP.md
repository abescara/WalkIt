# Shared accounts setup

The Android app connects to the WalkIt Supabase project at `https://juxnlpazlvjggxmojnoq.supabase.co`. It uses the public project key and Supabase Auth for email/password sign in. The key is safe to include in a client app because access to tables is controlled by row policies. Never put the service role key or database password in the app.

Existing device-only accounts cannot be copied into Supabase as passwords because their password hashes use a separate scheme. Users should register again on the shared service.

1. In **Authentication → Providers → Email**, enable email and password. If email confirmation remains on, users must confirm their email before logging in.
2. In **Authentication → URL Configuration**, set the **Site URL** to `https://github.com/abescara/WalkIt`. Supabase sends the browser there after confirming an email; return to the app and sign in. The default `http://localhost:3000` causes a “localhost refused to connect” page on phones. An account may still be confirmed when that page appears, so try signing in before requesting another email.
3. Run [`supabase/schema.sql`](../supabase/schema.sql) in the SQL Editor once. This project already completed that step.
4. Run [`supabase/02_profile_trigger.sql`](../supabase/02_profile_trigger.sql) in the SQL Editor once. This project has completed that step. The trigger creates a profile when a user signs up, even when email confirmation is enabled.
5. Run [`supabase/03_forum.sql`](../supabase/03_forum.sql) in the SQL Editor to enable shared forum posts and replies. The app shows a setup message until this migration is applied.
6. Install the main branch APK on two devices, register different email addresses, confirm each email, add reports, and confirm both accounts appear on the leaderboard and in forum discussions.

The schema uses authenticated row policies. Reports are saved locally and their text, category, and location are uploaded to Supabase; failed uploads retry after the next login. The leaderboard grants 10 points for a synced report and 2 for a synced comment. Comment and photo file synchronization remain separate work, so comment points and report photos are currently limited to the original device.
