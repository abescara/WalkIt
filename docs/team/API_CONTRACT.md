# WalkIt API contract draft

This document is a discussion contract, not a live API. No backend endpoint is currently configured.

## Design goals

- Keep route, report, and photo metadata separate from presentation code.
- Use authenticated identity for write ownership; ignore client-supplied owner IDs.
- Return stable IDs and ISO 8601 UTC timestamps.
- Accept image uploads separately from report JSON and return a media ID or protected URL.
- Support pagination for reports and forums before data grows.

## Candidate endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/v1/reports?bbox=...&status=active` | Read reports in the visible map area |
| POST | `/v1/media` | Upload a photo and return a media reference |
| POST | `/v1/reports` | Create a report from description, time, coordinates, category, and media references |
| GET | `/v1/reports/{id}` | Read report details |
| PATCH | `/v1/reports/{id}` | Update an owned report or its status |
| DELETE | `/v1/reports/{id}` | Delete an owned report |

## Candidate report payload

```json
{
  "description": "Uneven sidewalk near the crossing",
  "observedAt": "2026-11-06T02:00:00Z",
  "location": { "latitude": 14.5995, "longitude": 120.9842 },
  "category": "ACCESSIBILITY_BARRIER",
  "mediaIds": ["media-id"]
}
```

The team should choose authentication, media limits, retention, moderation, and server hosting before implementing these endpoints. Do not place credentials in the Android app or repository.
