# Figma handoff

## Link the design

Create the WalkIt file in the team Figma workspace, set access for the team, then add the file URL here:

**Figma file:** Add the team file link here.

Keep Figma as the visual source of truth. Use page names such as `Mobile MVP`, `Components`, and `Prototype flows`. Include mobile frames for Map, Camera Single, Camera Batch, Photo Report sheet, Forums empty state, and Profile empty state.

## Handoff to Android

1. Use a consistent mobile frame size and define text styles, colors, spacing, corner radii, and button states as Figma variables or styles.
2. Name layers and components by purpose (`PhotoReportSheet`, `CameraModeSwitch`) instead of their visual appearance.
3. Add annotations for behaviors that a static frame cannot show: camera permission, capture mode, batch completion, submit errors, loading, and map markers.
4. In Dev Mode, inspect dimensions and export only assets used in the app. Keep icons as SVG/vector where practical; use PNG for raster images.
5. Translate design tokens into Android `colors.xml`, `dimens.xml`, and `strings.xml`; implement native layouts and behaviors in the app. Do not paste web CSS into the Android app.
6. Add a Figma frame link and screenshot to UI pull requests so reviewers can compare implementation with the approved design.

## Suggested review states

- Map: default Philippines view, zoomed map, report photo markers.
- Camera: Single selected, Batch selected, captured-photo count, permission denied.
- Photo report sheet: one image, multiple images, empty description, valid coordinates, submitting.
- Forums and Profile: intentional empty states.
