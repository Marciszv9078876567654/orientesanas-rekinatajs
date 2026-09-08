package com.orientesanasrekinatajs.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.orientesanasrekinatajs.domain.model.DistanceUnit
import com.orientesanasrekinatajs.domain.model.LanguageConfig
import com.orientesanasrekinatajs.domain.model.ThemeConfig
import com.orientesanasrekinatajs.domain.model.UserPreferences
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * File name of the DataStore preferences backing [PreferencesRepository].
 */
private const val DATA_STORE_FILE_NAME = "user_preferences"

/**
 * App-wide singleton [DataStore] instance, lazily created from the [Context] and backed by
 * the `user_preferences` file in the app's data directory.
 *
 * Every [PreferencesRepository] created via [PreferencesRepository.create] shares this
 * single store, so preference updates are visible to all of them.
 */
private val Context.dataStore by preferencesDataStore(name = DATA_STORE_FILE_NAME)

private inline fun <reified T : Enum<T>> enumPreference(value: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

/**
 * Single source of truth for [UserPreferences], persisted with Jetpack Preferences DataStore.
 *
 * [userPreferencesFlow] emits the current preferences and re-emits whenever any of them
 * changes. On first launch (no stored values) the emitted values are the defaults defined
 * in [UserPreferences]; if the DataStore file is unreadable ([IOException], e.g. a
 * [androidx.datastore.core.CorruptionException] which subclasses it) the flow falls back to
 * an empty [Preferences] emission, which [userPreferencesFlow] maps to all-default
 * [UserPreferences], keeping the app usable.
 *
 * All write methods are `suspend` and must be invoked from a coroutine.
 *
 * Instances should be created via [create] (e.g. from an `Application` class or a DI
 * module) rather than by constructing the class directly, so the singleton DataStore
 * above is used.
 */
class PreferencesRepository(private val dataStore: DataStore<Preferences>) {

    /**
     * Emits the current [UserPreferences] and re-emits whenever any preference changes.
     */
    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            UserPreferences(
                themeConfig = enumPreference(preferences[THEME_KEY], ThemeConfig.SYSTEM),
                language = enumPreference(preferences[LANG_KEY], LanguageConfig.SYSTEM),
                useAnimations = preferences[ANIM_KEY] ?: true,
                disableMapRotationGestures = preferences[MAP_ROTATION_GESTURES_KEY] ?: false,
                showUsageTips = preferences[USAGE_TIPS_KEY] ?: true,
                distanceUnit = enumPreference(preferences[UNIT_KEY], DistanceUnit.METRIC),
                defaultDistanceBudgetKm = preferences[BUDGET_KEY] ?: 15.0f,
            )
        }

    /**
     * Updates the user's theme preference ([ThemeConfig]).
     */
    suspend fun updateTheme(theme: ThemeConfig) {
        dataStore.edit { it[THEME_KEY] = theme.name }
    }

    /**
     * Updates the user's language preference ([LanguageConfig]).
     */
    suspend fun updateLanguage(language: LanguageConfig) {
        dataStore.edit { it[LANG_KEY] = language.name }
    }

    /**
     * Enables or disables UI transitions and calculation animations.
     */
    suspend fun updateAnimations(enabled: Boolean) {
        dataStore.edit { it[ANIM_KEY] = enabled }
    }

    suspend fun updateMapRotationGestures(enabled: Boolean) {
        dataStore.edit { it[MAP_ROTATION_GESTURES_KEY] = enabled }
    }

    suspend fun updateUsageTips(enabled: Boolean) {
        dataStore.edit { it[USAGE_TIPS_KEY] = enabled }
    }

    /**
     * Updates the distance display unit ([DistanceUnit]).
     */
    suspend fun updateDistanceUnit(unit: DistanceUnit) {
        dataStore.edit { it[UNIT_KEY] = unit.name }
    }

    /**
     * Updates the default distance budget (in kilometers) used by the Best Score
     * routing algorithm.
     */
    suspend fun updateDefaultDistanceBudgetKm(budgetKm: Float) {
        dataStore.edit { it[BUDGET_KEY] = budgetKm }
    }

    companion object {
        // DataStore preference keys.
        private val THEME_KEY = stringPreferencesKey("theme_config")
        private val LANG_KEY = stringPreferencesKey("language_config")
        private val ANIM_KEY = booleanPreferencesKey("use_animations")
        private val MAP_ROTATION_GESTURES_KEY = booleanPreferencesKey("map_rotation_gestures")
        private val USAGE_TIPS_KEY = booleanPreferencesKey("show_usage_tips")
        private val UNIT_KEY = stringPreferencesKey("distance_unit")
        private val BUDGET_KEY = floatPreferencesKey("default_budget_km")

        /**
         * Creates a [PreferencesRepository] backed by the app's singleton DataStore.
         *
         * Safe to call multiple times: [Context.dataStore] is lazy, so all repositories
         * created from any [Context] share the same underlying store.
         */
        fun create(context: Context): PreferencesRepository =
            PreferencesRepository(context.dataStore)
    }
}
