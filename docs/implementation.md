# Orienteering Route Optimizer — Implementation

This document describes the Android application as it is currently implemented.

## Platform and architecture

- Android 11+ (`minSdk 30`, `targetSdk 37`)
- Kotlin, coroutines, Flow, and lifecycle-aware ViewModels
- Jetpack Compose with Material 3
- OpenCV for map-boundary and control-symbol detection
- Google ML Kit for control-number OCR
- Room plus app-private PNG files for saved map persistence
- Preferences DataStore for theme, language, animation, unit, and default-budget settings

The UI is driven by `MapProcessingViewModel`, `RoutingViewModel`, and `SettingsViewModel`. Image processing and route calculation run away from the main thread and publish immutable UI state.

## User flow

1. The home page lets the user select a photo, invoke the phone's system camera, or reopen a timestamped entry from Recent maps.
2. OpenCV attempts to detect the four map corners. If it cannot, a dismissible explanation is shown and the user can drag four handles to select the boundary manually.
3. The image is perspective-corrected, control symbols are detected, and control numbers are read with OCR.
4. The Edit map page displays the rectified image. The user can:
   - pinch to zoom around the fingers and pan the full-width map in calibration, corner, and point-editing modes;
   - tap once for the first calibration endpoint and again for the second;
   - drag either endpoint after the line has been completed, or reset the line;
   - enter that line's real distance in metres;
   - readjust the map corners while perspective-projecting existing controls and calibration endpoints into the new rectified image;
   - verify upright, zoom-scaled reference markers and their codes directly on the calibration and point-editing maps;
   - drag, add, delete, clear with confirmation, and edit the type/code of detected course points;
   - choose shortest-route or best-score routing.
5. The UI derives pixels per metre from the calibration line and its entered distance, then sends that scale to the routing layer.
6. A successful calculation opens a separate, zoomable Route page. Its header identifies unsaved/saved state and provides contextual save, rename, delete, and Home actions; Edit is part of the map HUD. Route details label endpoint rows as S, F, or S/F and separate visit score from cumulative score.

## Saved maps

Saving writes the rectified bitmap as a PNG under the app's private files directory and stores its name, timestamp, pixels-per-metre scale, calibration line/distance, rotation, current edited points, and route visit order/statistics in Room. The database and point rows are replaced transactionally; a failed database write removes the just-created image file. Recent maps observes Room reactively and reopens a selection directly on Route without showing Save until the snapshot is modified. Saved routes can be renamed or deleted; Settings can clear all saved maps after confirmation. Room migration 1→2 preserves older map rows and supplies defaults for the new route metadata.

## Image input

Gallery selection uses `ActivityResultContracts.PickVisualMedia`. Camera capture uses `ActivityResultContracts.TakePicture`, a cache-backed `FileProvider` URI, and the device's installed camera application. The app therefore does not request camera or media-storage permission.

## Image-processing pipeline

1. Decode the selected URI into a software bitmap.
2. Detect a quadrilateral boundary with grayscale conversion, blur, edge detection, and contour analysis.
3. Perspective-warp the chosen boundary into a rectangular map.
4. Isolate magenta course markings and classify control, start, finish, and combined start/finish symbols.
5. Crop regions around ordinary controls and use ML Kit OCR to extract two- or three-digit codes.
6. Convert detected symbols into domain `ControlPoint` objects.

The source bitmap is retained after a boundary-detection failure so the manual-boundary page can recover without asking the user to select the image again.

## Scale and routing

The calibration canvas stores two image-space points. Given their pixel distance `p` and the user-entered real distance `m`, the UI derives:

```text
pixelsPerMeter = p / m
```

The distance matrix converts Euclidean image distances to metres with that value. Shortest-route mode uses nearest-neighbour construction followed by 2-opt improvement. Best-score mode uses budget-constrained greedy insertion followed by route improvement.

Routing validates that exactly one usable start and finish exist. Problems are presented in centered, dismissible dialogs without changing the underlying page layout.

## UI and accessibility details

- The app draws a Material surface behind system safe areas, so status/navigation areas match the selected theme.
- Screen top bars use consistently positioned back buttons and centered titles.
- Hardware/software back returns from Route to Edit map, then from Edit map to Home.
- Home actions have icons and share the width of the widest intrinsically sized action.
- Settings use compact horizontal choice chips for both theme and language, with dividers between option groups.
- Contextual calibration tips can be enabled or disabled with the persisted Usage tips setting.
- Compact rotate, recenter, edit, line-reset, point-clear, and point-add HUD actions sit on an opaque Material surface, while canvases are clipped below the top bar. Line and point clearing require confirmation.
- Pinch zoom and pan are available on boundary selection, corner editing, calibration, point editing, and Route; zoom stays anchored beneath the gesture centroid and supports up to 12× magnification.
- Rotation is shared by the map workflow and animates over 180 ms. Edit/Route page changes use a subtle crossfade. Labels are drawn in screen space with a thin white outline, so their text stays upright and legible while the bitmap rotates.
- New points are created at a fixed center crosshair; cancelling their Add point dialog removes the provisional point.
- A lone first calibration endpoint can be dragged before the second is placed.
- Editing a saved route exposes both top-bar Back and bottom Discard actions, which reload the stored snapshot; Route uses a Home header action and an Edit HUD action.
- The activity is portrait-locked and uses `adjustNothing` both in the manifest and at runtime so opening the keyboard does not resize or pan a zoomed map viewport.
- Status-bar color matches the elevated screen header and uses dark icons in light themes.
- English and Latvian resources are supplied, with system-locale fallback.
- Route drawing animation follows the persisted animation preference.

## Verification

The project contains JVM tests for preferences, distance matrices, routing, and OCR helpers, plus Android instrumentation tests for Room/repository persistence and migration, perspective point reprojection, image processing, ViewModels, Compose canvases, and combined start/finish route details. Standard verification is:

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```
