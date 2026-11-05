# WalkIt implementation checklist

This checklist translates the supplied proposal into development tasks. None are marked complete until implementation and verification are recorded.

## Before implementation

- [ ] Obtain instructor approval and finalize the 8–12 named segments and 3–5 routes.
- [ ] Select Android SDK, minimum SDK, test devices, and build configuration.
- [ ] Create the Android Studio Kotlin project and commit its Gradle wrapper.
- [ ] Record team ownership and review process.

## Sprint 1 — foundation and accounts

- [ ] Create the nine SQLite tables: `users`, `hazard_reports`, `pathway_segments`, `walking_routes`, `route_segments`, `badges`, `user_badges`, `saved_routes`, `user_preferences`.
- [ ] Enable foreign keys, indexes, bound query parameters, migrations, and first-run reference seeding.
- [ ] Implement local registration and login with established salted password hashing, generic login errors, throttling, and in-memory sessions.
- [ ] Implement basic profiles and owner-linked report creation/listing.
- [ ] Verify persistence and separation between two sequentially signed-in accounts.

## Sprint 2 — personal records

- [ ] Complete owner-restricted report create/view/update/delete and account deletion.
- [ ] Add profile editing, My Reports, search, category/status filters, and sorting.
- [ ] Validate forms and show confirmation, success, error, and empty states.
- [ ] Enforce ownership in the data layer, including when an Activity receives a forged ID.

## Sprint 3 — routes and achievements

- [ ] Compare predefined routes by distance, distinct active hazards, or active accessibility barriers using the proposal's tie breaks.
- [ ] Add private saved routes and per-user default comparison preference.
- [ ] Derive points from distinct owner `(segment, category)` pairs; do not award extra points for edits or duplicate pairs.
- [ ] Implement the three badges and local leaderboard with shared competition ranks.
- [ ] Label zero reports as unknown conditions, never as a guarantee of safety.

## Sprint 4 — verification and release

- [ ] Implement all eight planned Activities with explicit Intents and safe handling of stale IDs.
- [ ] Check text scaling, TalkBack labels, focus order, readable status text, and 48 dp targets.
- [ ] Execute and document at least 12 test cases, three actual defects with fixes, and two evidence-backed improvements.
- [ ] Document build steps and produce an APK and presentation.

## Acceptance records

Create `docs/TEST_RESULTS.md` when testing begins. Record device/Android version, steps, expected result, actual result, pass/fail, defect link, fix, and retest. Do not invent results.
