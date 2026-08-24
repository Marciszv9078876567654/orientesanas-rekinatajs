## Rules for the Agent
1. **Rule of One:** Execute exactly ONE task at a time. Do not write code for subsequent tasks unless explicitly instructed.
2. **Strict Scope:** Only generate code for the requested layer (e.g., if asked for a DAO, do not write the ViewModel).
3. **Assume Modern Android:** Use Kotlin Coroutines, Flow, Jetpack Compose (M3), Room (KSP), and Hilt/Koin.
4. **No Placeholder Logic for Math:** When implementing algorithms or OpenCV logic, provide the full mathematical implementation as described in the specs, not `// TODO`.
5. **Mark as Done:** When a task is complete, ask the user to mark it `[x]` in this document.

---

## Phase 1: Core Domain & Architecture
- [x] **TASK-1.1: Core Domain Models**
    - **Objective:** Create the foundational data classes.
    - **Inputs:** None.
    - **Outputs:** `Point2D`, `MapBoundary`, `ControlPointType` (Enum), `ControlPoint`, `RouteSegment`, `OptimizedRoute`.
    - **Constraints:** Pure Kotlin. No Android dependencies. Include distance formulas in `Point2D`.

- [x] **TASK-1.2: Settings Domain**
    - **Objective:** Create the data structures for user preferences.
    - **Outputs:** `ThemeConfig` (Enum), `LanguageConfig` (Enum), `DistanceUnit` (Enum), `UserPreferences` (Data class).

---

## Phase 2: Local Storage (Data Layer)
- [ ] **TASK-2.1: Room DB Entities**
    - **Objective:** Define the SQLite tables.
    - **Inputs:** `TASK-1.1` Domain Models.
    - **Outputs:** `ScannedMapEntity`, `ControlPointEntity`.
    - **Constraints:** Use `@Entity`, `@PrimaryKey`, and `@ForeignKey` for relationships.

- [ ] **TASK-2.2: Room DAOs**
    - **Objective:** Define data access methods.
    - **Outputs:** `MapDao` with basic CRUD operations (insert map, insert points, get map with points using Flow).

- [ ] **TASK-2.3: Preferences DataStore**
    - **Objective:** Setup Jetpack DataStore for Settings.
    - **Inputs:** `TASK-1.2` Settings Domain.
    - **Outputs:** `PreferencesRepository` exposing a `Flow<UserPreferences>` and suspend update functions.

---

## Phase 3: Settings & Theming UI
- [ ] **TASK-3.1: Settings ViewModel**
    - **Objective:** Bridge DataStore to the UI.
    - **Inputs:** `PreferencesRepository`.
    - **Outputs:** `SettingsViewModel` exposing `StateFlow<UserPreferences>`.

- [ ] **TASK-3.2: Base Compose Theme**
    - **Objective:** Create the dynamic Material 3 theme.
    - **Inputs:** `UserPreferences` state.
    - **Outputs:** `OrienteeringAppTheme` that reacts to Dark/Light mode and sets `LocalAnimationsEnabled`.

- [ ] **TASK-3.3: Language Manager Utility**
    - **Objective:** Handle on-the-fly language switching.
    - **Outputs:** Utility function using `AppCompatDelegate.setApplicationLocales` (AndroidX).

---

## Phase 4: Image Processing (OpenCV)
*Note: Do not write any ViewModels for these tasks. Pure utility classes only.*

- [ ] **TASK-4.1: Boundary Detection Utility**
    - **Objective:** Find map corners in a photo.
    - **Inputs:** Raw `Bitmap`.
    - **Outputs:** `OpenCVUtils.detectBoundaries(bitmap): MapBoundary?`.
    - **Constraints:** Use Grayscale, Gaussian Blur, Canny, and `approxPolyDP`.

- [ ] **TASK-4.2: Perspective Warp Utility**
    - **Objective:** Flatten the cropped map.
    - **Inputs:** Raw `Bitmap`, `MapBoundary`.
    - **Outputs:** `OpenCVUtils.warpPerspective(bitmap, boundary): Bitmap`.

- [ ] **TASK-4.3: Legend Color Segmentation**
    - **Objective:** Find magenta control circles.
    - **Inputs:** Rectified `Bitmap`.
    - **Outputs:** `List<Point2D>` (Centers of detected circles).
    - **Constraints:** Convert to HSV. Use `HoughCircles` or contour circularity.

---

## Phase 5: ML Kit & Parsing
- [ ] **TASK-5.1: ROI Cropping Utility**
    - **Objective:** Cut out small squares around detected circles for the OCR.
    - **Inputs:** Rectified `Bitmap`, `Point2D` (center), Radius.
    - **Outputs:** Small cropped `Bitmap`.

- [ ] **TASK-5.2: OCR Text Recognition**
    - **Objective:** Read the number from the cropped ROI.
    - **Inputs:** Cropped `Bitmap`.
    - **Outputs:** Suspend function `extractControlNumber(bitmap): Int?`.
    - **Constraints:** Use Google ML Kit Text Recognition. Implement regex filtering for `\b\d{2,3}\b`.

---

## Phase 6: Routing Algorithms (Math Layer)
- [ ] **TASK-6.1: Distance Matrix**
    - **Objective:** Fast lookup for Euclidean distances between all points.
    - **Inputs:** `List<ControlPoint>`, `pixelsPerMeter` ratio.
    - **Outputs:** `DistanceMatrix` class returning physical distance in meters.

- [ ] **TASK-6.2: Nearest Neighbor Algorithm (Mode A)**
    - **Objective:** Base heuristic for shortest path.
    - **Inputs:** `DistanceMatrix`, Start, Finish, Control Points.
    - **Outputs:** `List<ControlPoint>` ordered by visit sequence.

- [ ] **TASK-6.3: 2-Opt Optimization (Mode A)**
    - **Objective:** Untangle the Nearest Neighbor path.
    - **Inputs:** `List<ControlPoint>` (from 6.2), `DistanceMatrix`.
    - **Outputs:** Optimized `List<ControlPoint>`.

- [ ] **TASK-6.4: Orienteering Problem Algorithm (Mode B)**
    - **Objective:** Maximize points within a budget.
    - **Inputs:** `DistanceMatrix`, Start, Finish, Control Points, `Budget` (Float).
    - **Outputs:** `List<ControlPoint>` optimizing Score/Cost ratio without exceeding Budget.

---

## Phase 7: UI Component Layer (Compose)
- [ ] **TASK-7.1: Image Picker & Camera Source**
    - **Objective:** Let user pick/take a photo.
    - **Outputs:** Compose screen with `PickVisualMedia` and CameraX invocation.

- [ ] **TASK-7.2: Interactive Corner Canvas**
    - **Objective:** Let user manually drag 4 corners to fix boundary detection.
    - **Inputs:** Raw Bitmap, Initial `MapBoundary`.
    - **Outputs:** Compose `Canvas` with touch listeners that update a `MapBoundary` state.

- [ ] **TASK-7.3: Route Rendering Canvas**
    - **Objective:** Draw the route over the map.
    - **Inputs:** Rectified Bitmap, `List<ControlPoint>`.
    - **Outputs:** Compose `Canvas` drawing `drawImage`, `drawCircle` (for points), and `drawLine` (for route). Respect `LocalAnimationsEnabled` for path drawing.

- [ ] **TASK-7.4: Route Step-by-Step Table**
    - **Objective:** Bottom sheet showing the text breakdown of the route.
    - **Inputs:** `OptimizedRoute`.
    - **Outputs:** `LazyColumn` showing Sequence ID, Point Code, Distance, and Accumulated Score.

---

## Phase 8: ViewModels & Orchestration
- [ ] **TASK-8.1: MapProcessingViewModel**
    - **Objective:** Tie OpenCV and ML Kit together.
    - **Outputs:** ViewModel that takes an Image URI, runs Tasks 4.1->4.3 off the main thread, crops via 5.1, parses via 5.2, and exposes a `StateFlow<List<ControlPoint>>`.

- [ ] **TASK-8.2: RoutingViewModel**
    - **Objective:** Tie Algorithms to the UI.
    - **Outputs:** ViewModel that takes the detected points, builds `DistanceMatrix` (6.1), runs algorithms (6.2 - 6.4) based on user mode selection, and outputs the final `OptimizedRoute` StateFlow.
