package com.orientesanasrekinatajs.domain.model

/**
 * The complete set of user-configurable preferences, persisted via Jetpack DataStore.
 *
 * All properties have sensible defaults so the app works out-of-the-box before the user
 * has opened the Settings screen.
 *
 * @property themeConfig              Preferred app theme (system / light / dark).
 * @property language                 Preferred display language.
 * @property useAnimations            Whether UI transitions and route-drawing animations
 *                                    are enabled. Disable for accessibility or low-end
 *                                    device performance.
 * @property disableMapRotationGestures Whether two-finger twisting rotates map views.
 * @property showUsageTips            Whether contextual usage tips open automatically.
 * @property distanceUnit             Metric or imperial units for distance display.
 * @property defaultDistanceBudgetKm  The default distance budget (in kilometers) used by
 *                                    the Best Score routing algorithm.
 */
data class UserPreferences(
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val language: LanguageConfig = LanguageConfig.SYSTEM,
    val useAnimations: Boolean = true,
    val vibrationFeedback: Boolean = true,
    val disableMapRotationGestures: Boolean = false,
    val showUsageTips: Boolean = true,
    val distanceUnit: DistanceUnit = DistanceUnit.METRIC,
    val defaultDistanceBudgetKm: Float = 15.0f,
)
