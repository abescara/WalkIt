# MVP draft scope

## Implemented

- Buildable Kotlin Android app with an offline SQLite database and nine initial tables.
- Local account creation and sign in. Passwords use per-account random salts and PBKDF2 hashes. Session is held only in memory.
- Create and list shared hazard reports tied to sample pathway segments. The signed-in owner may mark their own reports resolved or delete them after confirmation. Ownership is checked in SQL updates and deletes.
- View two predefined sample routes with distance, active report counts, and active accessibility barrier counts. The screen warns that zero recorded hazards does not prove safety.
- Database work runs on a background executor.

## Planned, not implemented

- Report editing, search, filters, sorting, profiles, account deletion, login throttling, badge logic, leaderboard, saved routes, and preferences.
- Eight separate Activities and Intent navigation. This initial draft uses one Activity with simple screens.
- Full route preference sorting, production reference data, field observation date entry, and migrations.
- Accessibility review, device tests, the proposal's 12 test cases, defect log, and usability improvements.

## Build verification

`./gradlew :app:assembleDebug` completed successfully on 6 November 2026 with Android Gradle Plugin 9.4.0, Gradle 9.6.0, JDK 17, and SDK 36. This verifies compilation and packaging, not behavior on a device.
