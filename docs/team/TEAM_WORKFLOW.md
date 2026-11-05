# Team workflow

## Suggested ownership

- **Android UI:** map-first layout, camera screen, photo detail sheet, navigation, and accessibility.
- **Database:** SQLite schema, migrations, repositories, local photo metadata, and persistence behavior.
- **Backend:** API contract and service implementation when cross-device sharing is approved. Keep server work behind an interface so local mode remains usable.

Each person owns a feature area, but any teammate can review. Agree on the specific owners in the team chat; no names or accounts are assigned here.

## GitHub workflow

1. Start each task from the latest `main` branch.
2. Use short branches such as `feature/map-camera`, `feature/photo-repository`, or `feature/api-contract`.
3. Keep commits focused and use descriptive messages.
4. Open a pull request to `main`, explain the change, and include a screenshot for UI changes.
5. Have one teammate review the pull request and confirm the Android build check passes before merging.
6. Avoid concurrent edits to `MainActivity.kt`. Put new UI screens in `ui/`, persistence in `data/`, and service contracts in `network/` as those packages are introduced.

## Coordination rules

- Discuss shared models and method signatures before changing them.
- Database changes include a versioned migration and a short schema note.
- Backend changes update the API contract before implementation; never commit secrets or real user reports.
- UI work uses the Figma handoff and resource-based colors, spacing, and text.
- Merge or rebase frequently so long-running branches do not drift.

## Pull request checklist

- [ ] The change matches its issue or agreed task.
- [ ] Android debug build succeeds.
- [ ] UI changes include before/after screenshots or a short screen recording.
- [ ] Data/schema changes include migration notes.
- [ ] No API keys, passwords, or real personal data are included.
