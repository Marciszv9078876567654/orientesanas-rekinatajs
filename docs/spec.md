# Orienteering Route Optimizer - App Specification

## 1. Overview
The **Orienteering Route Optimizer** is an Android application designed for rogaining and score orienteering enthusiasts. It allows users to digitize physical orienteering maps via camera or gallery, automatically extracts map details, calculates optimal routes based on point values and distance constraints, and provides a highly customizable user experience through a dedicated Settings module.

## 2. Tech Stack
*   **Platform:** Android 11+ (API Level 30+)
*   **Language:** Kotlin
*   **UI Framework:** Jetpack Compose
*   **Local Storage (Relational):** Room Database (SQLite) + internal storage for images
*   **Local Storage (Key-Value):** Jetpack Preferences DataStore (for user settings)
*   **Image Processing:** OpenCV for Android
*   **Text Recognition (OCR):** Google ML Kit (Vision) API
*   **Architecture:** MVVM (Model-View-ViewModel) with Clean Architecture principles
*   **Concurrency:** Kotlin Coroutines & Flow

## 3. Core Features
1.  **Image Input:** Capture a map via the device camera or pick an image from the gallery.
2.  **Boundary Detection:** Auto-detect map edges (perspective crop) with a manual fallback.
3.  **Scale Calibration:** Detect map scale via OCR or allow manual grid-line pixel-to-meter calibration.
4.  **Control Point Detection:** Identify Start/Finish points and standard control points.
5.  **Score Parsing:** Use OCR to read control point numbers and calculate scores automatically (Integer division by 10). Allow manual corrections.
6.  **Route Calculation:**
    *   *Shortest Route (TSP):* Visits all points.
    *   *Best Score Route (OP):* Maximizes score within a distance budget.
7.  **Route Visualization:** Renders the calculated route over the map and provides a detailed step-by-step table.
8.  **History:** Saves scanned maps and generated routes for future viewing.
9.  **Settings & Preferences:** Customization options for the app's behavior and appearance.
    *   *Theme:* System Default, Light Mode, Dark Mode.
    *   *Language:* System Default, English, Latvian (and support for adding others).
    *   *Animations:* Toggle to enable/disable UI transitions and calculation animations (for accessibility and low-end device performance).
    *   *Units:* Metric (Kilometers/Meters) vs. Imperial (Miles/Feet).
    *   *Default Budget:* Set a preferred default distance for the Best Score routing algorithm.

---

## 4. Step-by-Step Development Plan

### Step 1: Project Setup & Base Architecture
*   **Tasks:** Initialize Android Studio project. Set up Compose Navigation (Screens: Home -> History -> Image Source -> Crop -> Parsing -> Routing -> Result -> Settings). Configure Hilt/Koin for dependency injection.

### Step 2: Settings & User Preferences (DataStore)
*   **Goal:** Establish the app's configuration layer early so the rest of the UI can react to it.
*   **Tasks:**
    *   Implement Jetpack Preferences DataStore.
    *   Create a `SettingsViewModel` exposing a `StateFlow<UserPreferences>`.
    *   Build a Compose Settings Screen with dropdowns for Language/Theme and switches for Animations.
    *   Implement in-app locale switching using `AppCompatDelegate.setApplicationLocales` (supported via AndroidX for API 30+).
    *   Wrap the root Compose `AppTheme` so it observes the theme state (Light/Dark) and animation scales dynamically.

### Step 3: Perspective Cropping & Boundary Detection
*   **Tasks:** Integrate OpenCV. Implement Canny edge detection and contour finding. Create an interactive 4-point corner drag UI in Compose for manual adjustments. Apply perspective warp.

### Step 4: Scale Calibration
*   **Tasks:** Use ML Kit to look for scale text (e.g., "1:16 000"). Build a UI for drawing a line between two map grid lines to calculate the exact `pixels_per_meter` ratio.

### Step 5: Legend & Control Point Detection
*   **Tasks:** Filter HSV colors for magenta map markings. Use Hough Circle/Contour analysis for circles (controls) and triangles (start). Extract ROI and feed to ML Kit for digit recognition. Provide a fallback editing UI.

### Step 6: Routing Algorithms
*   **Tasks:** Build the Distance Matrix. Implement Nearest-Neighbor + 2-Opt for Shortest Path. Implement Greedy Insertion + 2-Opt for Best Score Path (using the default distance budget from DataStore if provided).

### Step 7: Route Visualization
*   **Tasks:** Draw the cropped map, route lines, and overlays using Compose `Canvas`. Build a bottom sheet with a `LazyColumn` for the step-by-step point table. Format distances based on the user's unit preference (Metric/Imperial).

### Step 8: History & Local Storage
*   **Tasks:** Set up Room DB for `ScannedMap`, `ControlPoint`, and `CalculatedRoute`. Save images to `Context.filesDir`. Build the History list screen.

---

## 5. Implementation Additions for Settings

### 5.1 Domain Models (Settings)

```kotlin
// Data class to represent the current state of settings
data class UserPreferences(
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val language: LanguageConfig = LanguageConfig.SYSTEM,
    val useAnimations: Boolean = true,
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val defaultDistanceBudgetKm: Float = 15.0f
)

enum class ThemeConfig { SYSTEM, LIGHT, DARK }
enum class LanguageConfig(val tag: String) { 
    SYSTEM(""), 
    ENGLISH("en"), 
    LATVIAN("lv") 
}
enum class DistanceUnit { METRIC, IMPERIAL }
```

### 5.2 DataStore Repository implementation

```kotlin
class PreferencesRepository(private val dataStore: DataStore<Preferences>) {
    
    private companion object {
        val THEME_KEY = stringPreferencesKey("theme_config")
        val LANG_KEY = stringPreferencesKey("language_config")
        val ANIMATIONS_KEY = booleanPreferencesKey("use_animations")
        val UNIT_KEY = stringPreferencesKey("distance_unit")
        val BUDGET_KEY = floatPreferencesKey("default_budget")
    }

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            UserPreferences(
                themeConfig = ThemeConfig.valueOf(preferences[THEME_KEY] ?: ThemeConfig.SYSTEM.name),
                language = LanguageConfig.valueOf(preferences[LANG_KEY] ?: LanguageConfig.SYSTEM.name),
                useAnimations = preferences[ANIMATIONS_KEY] ?: true,
                distanceUnit = DistanceUnit.valueOf(preferences[UNIT_KEY] ?: DistanceUnit.METRIC.name),
                defaultDistanceBudgetKm = preferences[BUDGET_KEY] ?: 15.0f
            )
        }

    suspend fun updateTheme(theme: ThemeConfig) {
        dataStore.edit { it[THEME_KEY] = theme.name }
    }
    
    suspend fun updateAnimations(enabled: Boolean) {
        dataStore.edit { it[ANIMATIONS_KEY] = enabled }
    }
    // ... other update methods ...
}
```

### 5.3 Modifying the Compose Theme for Animations
To respect the "Animations" toggle, you can provide a custom `LocalAnimationEnabled` composition local, or directly disable `AnimatedVisibility` and `animate*AsState` durations.

```kotlin
// In your AppTheme.kt
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
    
    val colors = if (darkTheme) DarkColorPalette else LightColorPalette

    CompositionLocalProvider(
        LocalAnimationsEnabled provides userPreferences.useAnimations
    ) {
        MaterialTheme(
            colors = colors,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
```

### 5.4 Language Switching on Android 13+ (Backwards Compatible)
To change the language without requiring the user to go to the system settings:

```kotlin
fun changeApplicationLocale(languageConfig: LanguageConfig) {
    val localeList = if (languageConfig == LanguageConfig.SYSTEM) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(languageConfig.tag)
    }
    
    // This updates the locale dynamically and persists it across restarts
    AppCompatDelegate.setApplicationLocales(localeList)
}
```
*Note: Make sure `android:localeConfig="@xml/locales_config"` is added to `AndroidManifest.xml` to support Android 13+ per-app language preferences properly.*
