# Orienteering Route Optimizer — Product Specification

## Purpose

The app digitizes a photographed orienteering map, identifies its course markings, and calculates either a short all-controls route or a high-scoring route within a distance budget.

## Implemented capabilities

- Select an image with Android Photo Picker or capture one with the system camera.
- Automatically detect and perspective-correct the map boundary.
- Recover from failed boundary detection with manual four-corner selection.
- Detect start, finish, combined start/finish, and ordinary control symbols.
- Read control codes with OCR and derive their score values.
- Calibrate distance by placing a two-point line on a zoomable map and entering its real length.
- Zoom while selecting or correcting map corners.
- Add, move, edit, and delete detected start, finish, and control points.
- Calculate shortest and budget-constrained best-score routes.
- Show results on a dedicated zoomable route page with rotation, route summary, and step details.
- Persist theme, language, animation, distance-unit, and default-budget preferences.
- Switch between system, English, and Latvian language settings.
- Save, rename, reopen, update, and delete a route snapshot containing the rectified image, route order, scale, calibration line, rotation, and edited points.
- Clear all saved maps from Settings after confirmation.

## Main screens

### Home

Contains photo selection, system-camera capture, settings, and Recent maps actions. Recent maps lists named saved entries with timestamps and reopens a selection directly on Route. Primary action buttons use consistent intrinsic widths. The explanatory subtitle is centered and no wider than the title.

### Settings

Presented as a dialog with grouped, horizontally arranged theme and language choices and an animation switch. Dividers separate each group.

### Select map boundary

Shown when automatic boundary detection fails. A dismissible dialog explains the failure. Four draggable handles allow the map boundary to be supplied manually.

### Edit map

Contains the zoomable distance-calibration map, distance entry, route-mode controls, corner editing, and full point editing. Calibration starts empty: the first tap creates one draggable endpoint and the second completes the line. Detected controls appear as zoom-scaled reference markers with upright, white-outlined labels. Compact overlay actions provide rotation, recentering, confirmed line reset, confirmed clear-all-points, and point creation. Corner edits preserve controls and calibration by perspective-projecting coordinates into the new crop; Back leaves the corner/point sub-editor without applying it. New points are placed at the enlarged center crosshair, start with an empty control-code field, and remain provisional until saved. For saved routes, Back and a bottom Discard action restore the saved snapshot.

### Route

Contains only route-result actions. The map supports focal-point pinch zoom and pan and has overlaid rotation, recenter, and Edit controls. The header has Home plus state-dependent Save or Delete; saved routes also have a rename icon next to the title. A clean loaded route is not offered Save again until changed. The summary and details action are vertically aligned. The details table labels S/F endpoints, separates each visit's score from cumulative score, and safely supports one combined start/finish point appearing at both ends. Page changes and quarter-turn rotation use subtle animations when animations are enabled.

## Routing rules

- Shortest route requires one start and one finish and visits every ordinary control.
- Best-score route maximizes collected score without exceeding the entered/default distance budget.
- A combined start/finish symbol may satisfy both endpoint requirements.
- Invalid or ambiguous endpoints produce a user-visible error rather than a crash.

## Supported platform

- Android 11 and newer
- Portrait-first responsive Compose UI
- Portrait orientation is locked; the keyboard does not resize/pan the map window.
- Light, dark, and system themes
- English, Latvian, and system languages
