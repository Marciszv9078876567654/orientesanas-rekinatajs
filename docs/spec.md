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
- Save the rectified map image, calibrated scale, and edited points, then reopen it from Recent maps.

## Main screens

### Home

Contains photo selection, system-camera capture, settings, and Recent maps actions. Recent maps lists saved entries by timestamp and reopens a selected map. Primary action buttons use consistent intrinsic widths. The explanatory subtitle is centered and no wider than the title.

### Settings

Presented as a dialog with grouped, horizontally arranged theme and language choices and an animation switch. Dividers separate each group.

### Select map boundary

Shown when automatic boundary detection fails. A dismissible dialog explains the failure. Four draggable handles allow the map boundary to be supplied manually.

### Edit map

Contains the zoomable distance-calibration map, distance entry, route-mode controls, corner editing, and full point editing. Calibration starts empty: the first tap creates one endpoint, the second completes the line, and completed endpoints can then be dragged. Detected controls appear as zoom-scaled reference markers with upright adjacent labels in calibration and point editing. Compact overlay actions provide rotation, recentering, line reset, and point creation as appropriate. New points are placed at the center crosshair, start with an empty control-code field, and remain provisional until saved. A failed calculation appears in a dismissible dialog and does not reflow the screen.

### Route

Contains only route-result actions. The map supports focal-point pinch zoom and pan and has overlaid rotation/recenter controls, plus the route summary, step details, and Save map action. The details table separates each visit's score from its cumulative score and safely supports one combined start/finish point appearing at both ends. Back returns to Edit map without discarding the selected image, calibration line, distance, or rotation.

## Routing rules

- Shortest route requires one start and one finish and visits every ordinary control.
- Best-score route maximizes collected score without exceeding the entered/default distance budget.
- A combined start/finish symbol may satisfy both endpoint requirements.
- Invalid or ambiguous endpoints produce a user-visible error rather than a crash.

## Supported platform

- Android 11 and newer
- Portrait-first responsive Compose UI
- Light, dark, and system themes
- English, Latvian, and system languages
