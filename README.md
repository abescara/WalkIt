# WalkIt

WalkIt is an Android app for sharing pedestrian hazard reports in the Philippines and planning walking routes around them. It combines a Kotlin Android shell, a MapLibre street map, an HTML interface, and Supabase for accounts and shared data.

## What works

- Email accounts, profiles, shared reports with photos, report discussions, community posts, friends, and a leaderboard.
- In-app camera and gallery selection, followed by a map pin for choosing a report's location.
- Place search and walking routes with nearby reports shown along the route.
- Google sign in through Supabase's browser flow. The Google provider is enabled in the existing WalkIt Supabase project; a full sign in on a device still needs to be checked.

See [CURRENT_FEATURES.txt](CURRENT_FEATURES.txt) for the fuller feature list and [the proposal](docs/WalkIt_Project_Proposal.pdf) for the original project brief.

## Build

1. Open the repository in Android Studio with JDK 17 and Android SDK 36, or use `./gradlew :app:assembleDebug` (`.\gradlew.bat :app:assembleDebug` on Windows).
2. Build the app. Street map tiles and route planning require an internet connection; the map does not need a Google Maps API key or Google Cloud billing.
3. Install the generated APK from `app/build/outputs/apk/debug/`. A prebuilt development APK is also in [artifacts/WalkIt-debug.apk](artifacts/WalkIt-debug.apk).

Google sign in is separate from the map. Supabase owns the OAuth Web client secret; it must never be added to this repository. The app returns from the browser through `walkit://auth/callback`. The existing Supabase project has Google enabled, and its OAuth request redirects to Google; device sign in remains the final check.

## Repository layout

| Path | Purpose |
| --- | --- |
| `app/src/main/java/` | Android screens, camera, map, local storage, and Supabase integration |
| `app/src/main/assets/walkit/` | Packaged HTML interface and assets |
| `supabase/` | Database schema and numbered SQL changes |
| `artifacts/` | Development APK |
| `.github/workflows/` | Android build check for pushes and pull requests |

For a new Supabase project, run `supabase/schema.sql` and then the numbered SQL files in order. Existing projects should apply only changes they have not already run. Keep database passwords and service role keys outside the app and Git.

## Current limits

Reports are user observations and may be incomplete or inaccurate. Route data does not verify sidewalk accessibility, surface quality, or lighting. The APK is a debug build. Map tiles come from an online third-party style and may be unavailable if that service is down.

## License

No license has been selected. Contact the project team before reusing the material.
