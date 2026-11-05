# WalkIt

WalkIt is a Kotlin Android prototype for recording pedestrian hazards and comparing predefined walking routes in a selected campus or neighborhood. Reports and accounts are local; viewing the illustrative base map requires an internet connection.

## Status

**Buildable MVP draft.** The prototype opens directly without a login screen and uses a local guest profile. It implements hazard creation/listing/resolution/deletion, a comparison of two sample routes, and an interactive street map with illustrative route lines and hazard pins. The supplied proposal says the full scope and demonstration area still require instructor review. The remaining features are tracked in the implementation plan.

## Planned prototype

- Kotlin Android app in Android Studio
- On-device SQLite database through `SQLiteOpenHelper`
- Guest-mode hazard reports with local persistence and owner-restricted changes
- Predefined pathway segments and routes, route comparison, saved routes, and preferences
- Derived badges, points, and a local leaderboard
- Local account and report storage on one installation; accounts and reports do not sync between devices
- Interactive MapLibre street map with road labels, OpenStreetMap data attribution, and demo-only sample geometry

See [the project proposal](docs/WalkIt_Project_Proposal.pdf) for detailed requirements and [the implementation checklist](docs/IMPLEMENTATION_PLAN.md) for a development sequence.

## Getting started

1. Open this repository in Android Studio with JDK 17 and Android SDK 36 installed.
2. Build and run the `app` configuration on an Android 8.0 (API 26) or newer device or emulator. Or run `./gradlew assembleDebug` (`.\gradlew.bat assembleDebug` on Windows).
3. Open Hazard reports and add a sample report. Visit Compare routes or Open map to see it reflected in route counts. Reports are saved locally under a guest profile.

The draft uses four clearly fictional pathway names, two sample routes, and illustrative map geometry in central Manila. Replace them only after the team agrees on the demonstration area. Street map tiles require internet access; hazard reports remain on-device. See [prototype scope](docs/PROTOTYPE.md) for implemented behavior and known gaps.

## Safety and data limitations

The prototype's reports are local observations, not verified safety or accessibility guidance. A route with no active reports may still have unknown conditions. Use synthetic accounts and demonstration data. Do not publish real user data or credentials.

## Team

Sam Denielle Arias; Helijah Sephreen Coo; Angel John Escara; Daniel Kenneth Euginio; John Carlo Salvador.

## License

No license has been selected. Contact the project team before reusing the material.
