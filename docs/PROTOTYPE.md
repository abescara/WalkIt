# MVP draft scope

## Implemented

- Buildable Kotlin Android app with an offline SQLite database and nine initial tables.
- Login and registration screens removed for this MVP. On first launch, a local guest profile is created for report ownership; no password is requested.
- Launch directly into a full-screen interactive map with Map, Forums, and Profile bottom tabs. Forums and Profile are intentionally empty placeholders.
- Camera button opens an in-app CameraX preview with a Single/Batch switch above the shutter. Single capture advances directly to the report sheet; Batch capture keeps the camera open until Done is tapped. The sheet can also add photos from the gallery.
- The report sheet collects a description, time, and latitude/longitude. Coordinates are constrained to the Philippines. Photos and metadata are saved to local app storage; no cloud upload happens.
- Submitted photos appear on the map as circular thumbnail markers. Large source photos are downsampled for map display, and same-report markers receive small offsets to remain tappable.
- Create and list shared hazard reports tied to sample pathway segments. The signed-in owner may mark their own reports resolved or delete them after confirmation. Ownership is checked in SQL updates and deletes.
- View two predefined sample routes with distance, active report counts, and active accessibility barrier counts. The screen warns that zero recorded hazards does not prove safety.
- Open an interactive MapLibre street map constrained to the Philippines, with visible roads and labels and two illustrative route lines around Manila. It is not real WalkIt pathway data and does not use device location.
- Show OpenStreetMap contributor attribution. The map style and tiles load from the internet; accounts and reports stay in local SQLite.
- Database work runs on a background executor.

## Planned, not implemented

- Report editing, search, filters, sorting, profiles, account deletion, login throttling, badge logic, leaderboard, saved routes, and preferences.
- Eight separate Activities and Intent navigation. This initial draft uses one Activity with simple screens.
- Full route preference sorting, production reference data, field observation date entry, and migrations.
- Replacing illustrative map coordinates with instructor-approved segment coordinates and connecting stored reports to map pins.
- Adding a backend/cloud storage flow if cross-device photo sharing is required; current photo submissions are local to the installation.
- Account registration, login, and profile management if they are restored for a later release.
- Accessibility review, device tests, the proposal's 12 test cases, defect log, and usability improvements.

## Build verification

`./gradlew :app:assembleDebug` completed successfully on 6 November 2026 with Android Gradle Plugin 9.4.0, Gradle 9.6.0, JDK 17, and SDK 36. This verifies compilation and packaging, not behavior on a device.
