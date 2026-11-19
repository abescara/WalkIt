# Shared accounts setup

The current APK supports multiple accounts on one device. To share accounts, reports, and rankings between phones, create a Supabase project and connect the Android client to it. Local SQLite accounts cannot be copied into Supabase as passwords because their password hashes use a separate scheme; users should register again on the shared service.

1. Create a project in the [Supabase dashboard](https://supabase.com/dashboard).
2. In **Authentication → Providers → Email**, enable email and password. Decide whether to require email confirmation during team testing.
3. Run [`supabase/schema.sql`](../supabase/schema.sql) in the project's SQL editor.
4. In **Project Settings → API**, copy the Project URL and publishable key. Provide these to the Android developer. Never put the service role key or database password in the app.
5. After the Android client is connected, create two accounts on separate devices, add reports, and confirm the leaderboard shows both users.

The schema uses authenticated row policies. The leaderboard grants 10 points for a report and 2 for a comment. The team can revise this scoring rule before launch. Photo storage and synchronization remain separate work.
