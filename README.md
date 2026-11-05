# WalkIt

WalkIt is a proposed offline Android app for recording pedestrian hazards and comparing predefined walking routes in a selected campus or neighborhood.

## Status

**Proposal / project setup.** The Android application has not been implemented. The supplied proposal says the scope and demonstration area still require instructor review. This repository provides the source proposal and an actionable build plan so the team can begin development without misrepresenting planned features as finished.

## Planned prototype

- Kotlin Android app in Android Studio
- On-device SQLite database through `SQLiteOpenHelper`
- Local registration, login, profiles, and owner-restricted hazard reports
- Predefined pathway segments and routes, route comparison, saved routes, and preferences
- Derived badges, points, and a local leaderboard
- Offline use on one installation; accounts and reports do not sync between devices

See [the project proposal](docs/WalkIt_Project_Proposal.pdf) for detailed requirements and [the implementation checklist](docs/IMPLEMENTATION_PLAN.md) for a development sequence.

## Getting started

1. Review the proposal with the instructor and confirm the demonstration area, sample segments, and routes.
2. Create an Android Studio project using Kotlin, then commit the generated Gradle wrapper and app module. Do not commit machine-specific files or secrets.
3. Implement the first sprint in the checklist and document test results as features are completed.

## Safety and data limitations

The prototype's reports are local observations, not verified safety or accessibility guidance. A route with no active reports may still have unknown conditions. Use synthetic accounts and demonstration data. Do not publish real user data or credentials.

## Team

Sam Denielle Arias; Helijah Sephreen Coo; Angel John Escara; Daniel Kenneth Euginio; John Carlo Salvador.

## License

No license has been selected. Contact the project team before reusing the material.
