# Frontend integration

## Package layout

The supplied mobile screens and image assets are packaged in `app/src/main/assets/walkit/`:

- `screens/` contains the light and dark HTML screen variants.
- `images/` contains the logo and UI images used by those screens.
- `runtime.js` makes the screen canvas fit common phone sizes and connects screen actions to Android.

The original `index.html` was a desktop gallery, so it is not loaded inside the app. The app starts on the map and keeps login and signup out of the launch flow.

## Native and HTML responsibilities

- The main HTML map shell is drawn above the native MapLibre map. Map gestures in the open map area are forwarded to MapLibre; its current-location and route controls remain native.
- Camera capture, photo-location picking, GPS, report persistence, and the point-to-point route request remain native Android features.
- Community, Routes, Rewards, Notifications, Profile, report, and report-preview screens load from local HTML assets in a WebView.
- `FrontendBridge` connects supported screen actions to Android navigation, camera/gallery selection, local report saving, coordinate search, theme switching, and route planning.

Keep the bridge limited to packaged local pages. Do not enable external page navigation or load user-provided HTML into this WebView. The prototype uses local file access to show private app photo files in report previews.

## Current prototype limits

Several pages contain sample text, counts, and posts from the design. Community posting, friends, profile edits, badges, and notifications do not yet synchronize or persist as backend data. Reports, photos, and discussions are stored on the current device only. Place-name geocoding and fully accessible route preferences are not implemented.

When updating a screen, edit the packaged asset and keep its image paths relative to `screens/`. The WebView loads the individual screen files, not the desktop gallery.
