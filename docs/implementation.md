# Complete Implementation Guide: Orienteering Route Optimizer

This document defines the architecture, data structures, Computer Vision pipelines, routing algorithms, and UI configurations required to build the application natively on Android.

## 1. Tech Stack & Architecture Overview
*   **Platform:** Android 11+ (API Level 30+). Target API 35+.
*   **Language:** Kotlin (with Coroutines & Flow for concurrency).
*   **UI Framework:** Jetpack Compose with **Material Design 3**.
*   **Architecture:** Clean Architecture + MVVM.
*   **Image Input:** CameraX (for in-app scanning) & `PickVisualMedia` (Android Photo Picker).
*   **Computer Vision:** OpenCV for Android (Java/JNI Wrapper).
*   **Machine Learning (OCR):** Google ML Kit Vision.
*   **Database:** Room (using **KSP** for annotation processing).
*   **Preferences:** Jetpack Preferences DataStore.

---

## 2. Core Domain Models & Data Structures

```kotlin
// --- Geometry & Primitives ---
data class Point2D(val x: Float, val y: Float) {
    fun distanceTo(other: Point2D): Float = kotlin.math.hypot(this.x - other.x, this.y - other.y)
}

data class MapBoundary(
    val topLeft: Point2D, val topRight: Point2D,
    val bottomRight: Point2D, val bottomLeft: Point2D
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y, topRight.x, topRight.y,
        bottomRight.x, bottomRight.y, bottomLeft.x, bottomLeft.y
    )
}

// --- Control Points & Legend ---
enum class ControlPointType { START, FINISH, START_FINISH, CONTROL }

data class ControlPoint(
    val id: String = java.util.UUID.randomUUID().toString(),
    var code: Int,                    // e.g., 65, 101
    var points: Int = code / 10,      // Integer division
    var center: Point2D,              // Coordinates in the normalized map space
    var type: ControlPointType = ControlPointType.CONTROL
)

// --- Routing & Distances ---
class DistanceMatrix(val size: Int) {
    private val matrix = Array(size) { FloatArray(size) }
    fun set(i: Int, j: Int, dist: Float) { matrix[i][j] = dist; matrix[j][i] = dist }
    fun get(i: Int, j: Int): Float = matrix[i][j]
}

data class RouteSegment(
    val from: ControlPoint,
    val to: ControlPoint,
    val distanceMeters: Float,
    val accumulatedDistanceMeters: Float,
    val accumulatedPoints: Int
)

data class OptimizedRoute(
    val path: List<ControlPoint>,
    val totalDistanceMeters: Float,
    val totalScore: Int,
    val segments: List<RouteSegment>
)

// --- Settings & User Preferences ---
enum class ThemeConfig { SYSTEM, LIGHT, DARK }
enum class LanguageConfig(val tag: String) { SYSTEM(""), ENGLISH("en"), LATVIAN("lv") }
enum class DistanceUnit { METRIC, IMPERIAL }

data class UserPreferences(
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val language: LanguageConfig = LanguageConfig.SYSTEM,
    val useAnimations: Boolean = true,
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val defaultDistanceBudgetKm: Float = 15.0f
)
```

---

## 3. Data Layer: Room DB & DataStore

### 3.1 Room Entities
```kotlin
@Entity(tableName = "scanned_maps")
data class ScannedMapEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val imageFilePath: String, // Stored in Context.filesDir
    val pixelsPerMeter: Float
)

@Entity(
    tableName = "control_points",
    foreignKeys = [ForeignKey(
        entity = ScannedMapEntity::class,
        parentColumns = ["id"],
        childColumns = ["mapId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["mapId"])]
)
data class ControlPointEntity(
    @PrimaryKey val id: String,
    val mapId: String,
    val code: Int,
    val points: Int,
    val x: Float, val y: Float,
    val type: String
)
```

### 3.2 DataStore Preferences Implementation
```kotlin
class PreferencesRepository(private val dataStore: DataStore<Preferences>) {
    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_config")
        val LANG_KEY = stringPreferencesKey("language_config")
        val ANIM_KEY = booleanPreferencesKey("use_animations")
        val UNIT_KEY = stringPreferencesKey("distance_unit")
        val BUDGET_KEY = floatPreferencesKey("default_budget_km")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            UserPreferences(
                themeConfig = ThemeConfig.valueOf(prefs[THEME_KEY] ?: ThemeConfig.SYSTEM.name),
                language = LanguageConfig.valueOf(prefs[LANG_KEY] ?: LanguageConfig.SYSTEM.name),
                useAnimations = prefs[ANIM_KEY] ?: true,
                distanceUnit = DistanceUnit.valueOf(prefs[UNIT_KEY] ?: DistanceUnit.METRIC.name),
                defaultDistanceBudgetKm = prefs[BUDGET_KEY] ?: 15.0f
            )
        }

    suspend fun updateTheme(theme: ThemeConfig) = dataStore.edit { it[THEME_KEY] = theme.name }
    // ... other update functions ...
}
```

---

## 4. Computer Vision Pipeline (OpenCV)

### 4.1 Boundary Detection & Perspective Warp
1.  **Downscale** original bitmap to max 3000px dimension to save memory.
2.  Convert to Grayscale -> Apply Gaussian Blur (5x5).
3.  Apply Canny Edge Detection (`threshold1=50`, `threshold2=150`).
4.  Find Contours (`RETR_EXTERNAL`, `CHAIN_APPROX_SIMPLE`).
5.  Filter for the largest quadrilaterals using `approxPolyDP`.
6.  **Sort Points** (TopLeft, TopRight, BottomRight, BottomLeft).
7.  Calculate matrix using `getPerspectiveTransform()` and flatten using `warpPerspective()`.

### 4.2 Control Point & Legend Detection
Standard IOF maps use **Magenta/Purple (PMS 4005)**.
1.  Convert Rectified Image to **HSV** color space.
2.  Create mask: `Hue [135, 165]`, `Saturation [70, 255]`, `Value [70, 255]`.
3.  Morphological close (`MORPH_CLOSE`) to heal contour lines passing through circles.
4.  **Circles:** `HoughCircles` or contour circularity checks to identify standard controls.
5.  **Triangles:** Contour approximation (3 vertices) to find the Start point.
6.  **Double Circles:** Hierarchy analysis (`RETR_TREE`) to find nested circles (Finish).

---

## 5. ML Kit OCR & Score Assignment

1.  **Crop ROI:** For each detected circle center `(cx, cy)` with radius `R`, crop a bitmap `[cx - 2.5R, cy - 2.5R]` to `[cx + 2.5R, cy + 2.5R]`.
2.  **Process:** Pass to `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`.
3.  **Regex Matching:** Extract tokens matching `\b\d{2,3}\b`.
4.  **Scoring Logic:**
    *   If token found (e.g., "65"): `points = 65 / 10 = 6`.
    *   If multiple tokens found: Select the text block physically closest to the circle's center coordinates.
    *   If failed/ambiguous: Flag for user manual correction in UI.

---

## 6. Routing Algorithms

### 6.1 Mode A: Shortest Route (Fixed Start/Finish TSP)
*   **Goal:** Visit *all* points with minimum distance.
*   **Algorithm:** 
    1.  **Nearest Neighbor:** Start at `START`. Continually jump to the closest unvisited node until all are visited, then jump to `FINISH`.
    2.  **2-Opt Local Search:** Iteratively untangle crossing paths by reversing segments of the route. If reversing a segment `[i, j]` results in a shorter total path, keep it. Repeat until no improvements can be found.

### 6.2 Mode B: Best Score Route (Orienteering Problem)
*   **Goal:** Maximize score given a maximum distance budget $B$.
*   **Algorithm:**
    1.  Initialize path $P = [\text{START}, \text{FINISH}]$. Cost = $D(\text{START}, \text{FINISH})$.
    2.  **Greedy Insertion:** Evaluate all unvisited points $u$. For every edge $(P_i, P_{i+1})$, calculate the cost of insertion: $\Delta D = D(P_i, u) + D(u, P_{i+1}) - D(P_i, P_{i+1})$.
    3.  If $(\text{Cost} + \Delta D) \le B$, calculate the heuristic ratio: `Score(u) / ΔD`.
    4.  Insert the node $u$ with the highest ratio into the path.
    5.  Periodically apply **2-Opt** on the current sequence to optimize distance, potentially freeing up budget $B$ for more insertions.
    6.  Repeat until no more nodes can be added without exceeding $B$.

---

## 7. UI & OS Integration (Jetpack Compose)

### 7.1 Material 3 Theming & Animations
To respect user settings seamlessly, build a reactive Compose Theme that disables animations and toggles dark mode dynamically.

```kotlin
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

@Composable
fun OrienteeringAppTheme(
    userPreferences: UserPreferences,
    content: @Composable () -> Unit
) {
    val darkTheme = when (userPreferences.themeConfig) {
        ThemeConfig.LIGHT -> false
        ThemeConfig.DARK -> true
        ThemeConfig.SYSTEM -> isSystemInDarkTheme()
    }
    
    val colorScheme = if (darkTheme) darkColorScheme(...) else lightColorScheme(...)

    CompositionLocalProvider(
        LocalAnimationsEnabled provides userPreferences.useAnimations
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
```

### 7.2 OS Image Picker & Permissions
To retrieve maps from the gallery, use the modern Photo Picker which does not require raw storage permissions:

```kotlin
// Inside a Composable
val pickMedia = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
) { uri -> 
    if (uri != null) { viewModel.processMapImage(uri) }
}

// Trigger it:
Button(onClick = { 
    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) 
}) { Text("Select from Gallery") }
```

### 7.3 Dynamic Language Switching
Since Android 13, per-app language preferences are native. Using AndroidX `LocaleManagerCompat`, we handle backwards compatibility for API 30 automatically.

```kotlin
fun setAppLanguage(context: Context, languageConfig: LanguageConfig) {
    val localeList = if (languageConfig == LanguageConfig.SYSTEM) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(languageConfig.tag)
    }
    
    // Updates UI natively, persists state automatically
    AppCompatDelegate.setApplicationLocales(localeList)
}
```

### 7.4 Map Render Canvas
The interactive map screen uses a Compose `Canvas` layered structure:
1.  **Background Bitmap:** The rectified image drawn with `drawImage`.
2.  **Zoomable State:** Wrapped in a `pointerInput(Unit) { detectTransformGestures { ... } }` modifier to handle pan and scale.
3.  **Vector Overlays:** `drawCircle` and `drawLine` functions to paint the calculated route, respecting `LocalAnimationsEnabled` to either pop instantly or animate the path drawing.