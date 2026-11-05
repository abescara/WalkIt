# Architecture and team boundaries

## Current prototype

The Android app is a single offline prototype. SQLite stores guest profile, reports, routes, and local photo submissions. Map tiles load over the internet; photos and report metadata stay in app storage. The map and route geometry are demonstration data.

## Suggested code boundaries

```text
app/src/main/java/com/walkit/prototype/
  ui/          Activities, map screen, camera screen, report sheet, navigation
  data/        SQLite helper, repositories, entities, migrations
  network/     API interface and DTOs, only when backend work is approved
```

Move existing classes into these packages gradually with package and import updates. Avoid a broad one-shot rewrite while parallel work is active.

## Backend contract

Backend work should begin with `docs/team/API_CONTRACT.md`. The first draft contract is intentionally storage-provider neutral. Backend implementation is optional until the team decides whether accounts, photo uploads, and shared reports are within the approved scope.

## Ownership checks

The current guest profile is local to one installation. A future service must authenticate a user independently of IDs supplied by the client, validate uploaded media, and define deletion and moderation behavior before public sharing.
