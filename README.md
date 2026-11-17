# WalkIt

WalkIt is a Kotlin Android prototype for recording pedestrian hazards and comparing predefined walking routes in the Philippines. Reports and accounts are local; viewing the street map requires an internet connection.

## Status

**Buildable MVP draft.** The prototype opens directly to a live Philippines map with a packaged HTML frontend for the map shell and secondary screens. There is no login gate. Users can capture a photo, choose its report location with a movable map-center pin, and save a description and time. Report markers open their photos and a local discussion. A route planner connects two points through BRouter. Reports, photos, and comments remain on the device; cross-device sync is not configured.

## Planned prototype

- Kotlin Android app in Android Studio
- On-device SQLite database through `SQLiteOpenHelper`
- Guest-mode hazard reports with local persistence and owner-restricted changes
- Predefined pathway segments and routes, route comparison, saved routes, and preferences
- Derived badges, points, and a local leaderboard
- Local account and report storage on one installation; accounts and reports do not sync between devices
- Interactive MapLibre street map with road labels, OpenStreetMap data attribution, current-location control, photo-location picker, and tappable report markers
- Expandable point-to-point route planner; route calculation needs an internet connection and uses the public BRouter service
- Report discussion UI with comments, optional anonymous posting, and optional local photo attachments
- Packaged mobile screen designs for community, routes, rewards, notifications, profile, and report flows; some content remains a visual prototype

See [the project proposal](docs/WalkIt_Project_Proposal.pdf) for detailed requirements and [the implementation checklist](docs/IMPLEMENTATION_PLAN.md) for a development sequence.

## Team development

Read [the team workflow](docs/team/TEAM_WORKFLOW.md), [the code boundaries](docs/team/ARCHITECTURE.md), and the [Figma handoff guide](docs/design/FIGMA_HANDOFF.md) before splitting work. Pull requests run an Android debug build in GitHub Actions. Keep the Figma source of truth in the team Figma workspace and link it from the handoff guide.

## Getting started

1. Open this repository in Android Studio with JDK 17 and Android SDK 36 installed.
2. Build and run the `app` configuration on an Android 8.0 (API 26) or newer device or emulator. Or run `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows).
3. From the Map screen, tap the camera button, choose Single or Batch, capture photos, then tap Done in Batch mode. Pan the map beneath the center pin and choose the photo location, then add a category, description, and time. Tap a report marker to view its photos and add to its discussion. Open Routes or use the map route control to set Point A and Point B coordinates and request a route.

The draft uses four clearly fictional pathway names and two sample routes. Replace them after the team agrees on the demonstration area. Street map tiles and route calculation require internet access; reports, photos, and discussions remain on-device. See [prototype scope](docs/PROTOTYPE.md) for implemented behavior and known gaps.

The HTML screen package and Android/native integration are described in [frontend integration](docs/FRONTEND_INTEGRATION.md).

## Safety and data limitations

The prototype's reports are local observations, not verified safety or accessibility guidance. A route with no active reports may still have unknown conditions. Use synthetic accounts and demonstration data. Do not publish real user data or credentials.

## Team

Sam Denielle Arias; Helijah Sephreen Coo; Angel John Escara; Daniel Kenneth Euginio; John Carlo Salvador.

## License

No license has been selected. Contact the project team before reusing the material.
