# WalkIt

WalkIt is a Kotlin Android prototype for recording pedestrian hazards and comparing predefined walking routes in the Philippines. Reports and accounts are local; viewing the street map requires an internet connection.

## Status

**Buildable MVP draft.** The prototype opens directly to a Philippines-focused full-screen map with Map, Forums, and Profile tabs. Forums and Profile are empty placeholders. The camera opens inside the app with Single and Batch capture modes. After capture, a photo sheet collects a description, time, and coordinates, then shows circular photo markers on the map. Photos and reports are stored on the device; there is no cloud upload service yet.

## Planned prototype

- Kotlin Android app in Android Studio
- On-device SQLite database through `SQLiteOpenHelper`
- Guest-mode hazard reports with local persistence and owner-restricted changes
- Predefined pathway segments and routes, route comparison, saved routes, and preferences
- Derived badges, points, and a local leaderboard
- Local account and report storage on one installation; accounts and reports do not sync between devices
- Interactive MapLibre street map with road labels, OpenStreetMap data attribution, and demo-only sample geometry

See [the project proposal](docs/WalkIt_Project_Proposal.pdf) for detailed requirements and [the implementation checklist](docs/IMPLEMENTATION_PLAN.md) for a development sequence.

## Team development

Read [the team workflow](docs/team/TEAM_WORKFLOW.md), [the code boundaries](docs/team/ARCHITECTURE.md), and the [Figma handoff guide](docs/design/FIGMA_HANDOFF.md) before splitting work. Pull requests run an Android debug build in GitHub Actions. Keep the Figma source of truth in the team Figma workspace and link it from the handoff guide.

## Getting started

1. Open this repository in Android Studio with JDK 17 and Android SDK 36 installed.
2. Build and run the `app` configuration on an Android 8.0 (API 26) or newer device or emulator. Or run `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows).
3. Tap the camera button, choose Single or Batch, capture photos, then tap Done in Batch mode. Add description, time, and coordinates in the report sheet to place photo markers on the map. The map is constrained to the Philippines and opens over illustrative sample data.

The draft uses four clearly fictional pathway names, two sample routes, and illustrative map geometry in central Manila. Replace them after the team agrees on the demonstration area. Street map tiles require internet access; hazard reports and photos remain on-device. See [prototype scope](docs/PROTOTYPE.md) for implemented behavior and known gaps.

## Safety and data limitations

The prototype's reports are local observations, not verified safety or accessibility guidance. A route with no active reports may still have unknown conditions. Use synthetic accounts and demonstration data. Do not publish real user data or credentials.

## Team

Sam Denielle Arias; Helijah Sephreen Coo; Angel John Escara; Daniel Kenneth Euginio; John Carlo Salvador.

## License

No license has been selected. Contact the project team before reusing the material.
