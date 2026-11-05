# MVP draft scope

## Implemented

- Buildable Kotlin Android app with an offline SQLite database, including local report discussions.
- Login and registration screens removed for this MVP. On first launch, a local guest profile is created for report ownership; no password is requested.
- Launch directly into a full-screen interactive map with Map, Forums, and Profile bottom tabs. Forums and Profile are intentionally empty placeholders.
- Camera button opens an in-app CameraX preview with a Single/Batch switch above the shutter. Single capture advances directly to the report sheet; Batch capture keeps the camera open until Done is tapped. The sheet can also add photos from the gallery.
- After camera capture or gallery selection, a map picker opens with a fixed pin at the center. Panning and zooming the map selects the report coordinates; the selected point is constrained to the Philippines. The report sheet collects a description and time. Photos and metadata are saved locally; no cloud upload happens.
- Each report appears as one circular thumbnail marker at its selected coordinate. Tapping it opens the photos and a discussion panel. Comments support anonymous posting and an optional photo attachment and persist in the local database.
- Create and list shared hazard reports tied to sample pathway segments. The signed-in owner may mark their own reports resolved or delete them after confirmation. Ownership is checked in SQL updates and deletes.
- View two predefined sample routes with distance, active report counts, and active accessibility barrier counts. The screen warns that zero recorded hazards does not prove safety.
- Open an interactive MapLibre street map constrained to the Philippines, with visible roads and labels. The map has a current-location control and no hard-coded route overlays. The compass and prototype map label are hidden.
- Open the expandable point-to-point route planner from a top-right control. It accepts coordinates for Points A and B, can use the current location for A or the map center for either point, and requests a route from BRouter using its hiking profile. An internet connection is required; route output depends on the public service and available OpenStreetMap coverage.
- Show OpenStreetMap contributor attribution. The map style and tiles load from the internet; accounts and reports stay in local SQLite.
- Database work runs on a background executor.

## Planned, not implemented

- Report editing, search, filters, sorting, profiles, account deletion, login throttling, badge logic, leaderboard, saved routes, and preferences.
- Eight separate Activities and Intent navigation. This initial draft uses one Activity with simple screens.
- Full route preference sorting, production reference data, field observation date entry, and migrations.
- Replacing illustrative map coordinates with instructor-approved segment coordinates and connecting stored reports to map pins.
- Adding a backend/cloud storage flow if cross-device photo and discussion sharing is required; current reports, photos, and comments are local to the installation.
- Account registration, login, and profile management if they are restored for a later release.
- Accessibility review, device tests, the proposal's 12 test cases, defect log, and usability improvements.

## Build verification

`./gradlew :app:assembleDebug` completed successfully on 6 November 2026 with Android Gradle Plugin 9.4.0, Gradle 9.6.0, JDK 17, and SDK 36. This verifies compilation and packaging, not behavior on a device.
