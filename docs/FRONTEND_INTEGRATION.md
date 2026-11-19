# Frontend integration

## Package layout

The supplied mobile screens and image assets are packaged in `app/src/main/assets/walkit/`:

- `screens/` contains the light and dark HTML screen variants.
- `images/` contains the logo and UI images used by those screens.
- `runtime.js` makes the screen canvas fit common phone sizes and connects screen actions to Android.

The original `index.html` was a desktop gallery, so it is not loaded inside the app. The app opens the map after restoring a session; users without a session see the supplied login and signup layouts with working email/password fields.

## Native and HTML responsibilities

- The main HTML map shell is drawn above the native MapLibre map. Map gestures in the open map area are forwarded to MapLibre; location and route planning remain native.
- Camera capture, photo-location picking, GPS, report persistence, and the point-to-point route request remain native Android features.
- Community, Routes, Rewards, Notifications, Profile, report, and report-preview screens load from local HTML assets in a WebView.
- `FrontendBridge` connects supported screen actions to Android navigation, camera/gallery selection, local report saving, coordinate search, theme switching, and route planning.

Keep the bridge limited to packaged local pages. Do not enable external page navigation or load user-provided HTML into this WebView. The prototype uses local file access to show private app photo files in report previews.

## Current prototype limits

Friends and notifications still contain sample content from the designs. The community forum loads shared posts and replies after its database migration; its sample cards are removed. Account details, leaderboard entries, report counts, points, and report badges use shared account data. Profile name and bio edits persist through Supabase. Selected profile photos, report photos, and report-marker discussions are stored on the current device and do not yet appear on another device. Route preferences are still visual controls.

When updating a screen, edit the packaged asset and keep its image paths relative to `screens/`. The WebView loads the individual screen files, not the desktop gallery.
