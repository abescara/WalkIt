# Shared accounts setup

The Android app connects to the WalkIt Supabase project at `https://juxnlpazlvjggxmojnoq.supabase.co`. It uses the public project key and Supabase Auth for email/password sign in. The key is safe to include in a client app because access to tables is controlled by row policies. Never put the service role key or database password in the app.

Existing device-only accounts cannot be copied into Supabase as passwords because their password hashes use a separate scheme. Users should register again on the shared service.

1. In **Authentication → Providers → Email**, enable email and password. If email confirmation remains on, users must confirm their email before logging in.
2. Run [`supabase/schema.sql`](../supabase/schema.sql) in the SQL Editor once. This project already completed that step.
3. Run [`supabase/02_profile_trigger.sql`](../supabase/02_profile_trigger.sql) in the SQL Editor once. The trigger creates a profile when a user signs up, even when email confirmation is enabled.
4. Install the feature branch APK on two devices, register different email addresses, add reports, and confirm both accounts appear on the leaderboard.

The schema uses authenticated row policies. Reports are saved locally and their text, category, and location are uploaded to Supabase; failed uploads retry after the next login. The leaderboard grants 10 points for a synced report and 2 for a synced comment. Comment and photo file synchronization remain separate work, so comment points and report photos are currently limited to the original device.
