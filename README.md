# WalkIt

WalkIt is a Kotlin Android prototype for recording pedestrian hazards and comparing predefined walking routes in the Philippines. Accounts and report rankings use Supabase; viewing the street map also requires an internet connection.

## Status

**Buildable MVP draft.** The prototype has Supabase account registration and email login, then opens the live Philippines map with a packaged HTML frontend. Users can capture a photo, choose its report location with a movable map-center pin, and save a description and time. Signed-in users can view shared report markers, photos, and discussions, and post comments with optional photos. The route planner searches places, accepts map pins, warns about shared reports near a route, and can request a detour through BRouter. Google and Apple sign-in require [provider setup](docs/SHARED_ACCOUNTS_SETUP.md); Google Maps also requires [an Android Maps API key](docs/GOOGLE_MAPS_SETUP.md). Apply [the shared map migration](supabase/04_shared_map_reports.sql) before using photo and comment sharing.

## Planned prototype

- Kotlin Android app in Android Studio
- On-device SQLite database through `SQLiteOpenHelper`
- Account-owned hazard reports with local persistence and owner-restricted changes
- Predefined pathway segments and routes, route comparison, saved routes, and preferences
- A shared leaderboard and report badges based on reports uploaded to Supabase; route, detour, and forum badges are shown as upcoming
- Supabase email/password accounts with local report storage and shared report metadata
- Interactive Google Maps street map with road labels, automatic location centering, photo-location picker, and tappable report markers
- Expandable point-to-point route planner; route calculation needs an internet connection and uses the public BRouter service
- Report discussion UI with comments, optional anonymous posting, and optional local photo attachments
- Shared forum posts and replies after the forum migration is applied; no sample forum comments
- Packaged mobile screen designs for community, routes, rewards, notifications, profile, and report flows; some content remains a visual prototype

See [the project proposal](docs/WalkIt_Project_Proposal.pdf) for detailed requirements and [the implementation checklist](docs/IMPLEMENTATION_PLAN.md) for a development sequence.

## Team development

Read [the team workflow](docs/team/TEAM_WORKFLOW.md), [the code boundaries](docs/team/ARCHITECTURE.md), and the [Figma handoff guide](docs/design/FIGMA_HANDOFF.md) before splitting work. Pull requests run an Android debug build in GitHub Actions. Keep the Figma source of truth in the team Figma workspace and link it from the handoff guide.

## Getting started

1. Open this repository in Android Studio with JDK 17 and Android SDK 36 installed.
2. Build and run the `app` configuration on an Android 8.0 (API 26) or newer device or emulator. Or run `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows).
3. Create an account or log in, then from the Map screen tap the camera button, choose Single or Batch, capture photos, then tap Done in Batch mode. Pan the map beneath the center pin and choose the photo location, then add a category, description, and time. Tap a report marker to view its photos and add to its discussion. Open Routes, enter addresses or place names, or use the map pin buttons to choose start and destination.

The draft uses four clearly fictional pathway names and two sample routes. Replace them after the team agrees on the demonstration area. Street map tiles and route calculation require internet access. Report photos and report-marker discussions remain on-device; forum posts and replies are shared after the forum migration. See [shared accounts setup](docs/SHARED_ACCOUNTS_SETUP.md) for the hosted database and [prototype scope](docs/PROTOTYPE.md) for implemented behavior and known gaps.

The HTML screen package and Android/native integration are described in [frontend integration](docs/FRONTEND_INTEGRATION.md).

## Safety and data limitations

The prototype's reports are local observations, not verified safety or accessibility guidance. A route with no active reports may still have unknown conditions. Use synthetic accounts and demonstration data. Do not publish real user data or credentials.

## Team

Sam Denielle Arias; Helijah Sephreen Coo; Angel John Escara; Daniel Kenneth Euginio; John Carlo Salvador.

## License

No license has been selected. Contact the project team before reusing the material.
