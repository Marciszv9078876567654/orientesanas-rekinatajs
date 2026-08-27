package com.orientesanasrekinatajs.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.orientesanasrekinatajs.domain.model.DistanceUnit
import com.orientesanasrekinatajs.domain.model.LanguageConfig
import com.orientesanasrekinatajs.domain.model.ThemeConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PreferencesRepositoryTest {

    @Test
    fun usageTipsCanBeDisabled() = runBlocking {
        val repository = PreferencesRepository(FakeDataStore(mutablePreferencesOf()))

        repository.updateUsageTips(false)

        assertFalse(repository.userPreferencesFlow.first().showUsageTips)
    }

    @Test
    fun pointsPerKilometerIsOffByDefaultAndCanBeEnabled() = runBlocking {
        val repository = PreferencesRepository(FakeDataStore(mutablePreferencesOf()))

        assertFalse(repository.userPreferencesFlow.first().showPointsPerKilometer)

        repository.updatePointsPerKilometer(true)

        assertEquals(true, repository.userPreferencesFlow.first().showPointsPerKilometer)
    }

    @Test
    fun mapRotationGesturesAreOnByDefaultAndCanBeDisabled() = runBlocking {
        val repository = PreferencesRepository(FakeDataStore(mutablePreferencesOf()))

        assertEquals(false, repository.userPreferencesFlow.first().disableMapRotationGestures)

        repository.updateMapRotationGestures(true)

        assertFalse(repository.userPreferencesFlow.first().disableMapRotationGestures)
    }

    @Test
    fun unknownEnumValuesFallBackToDefaults() = runBlocking {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("theme_config") to "REMOVED_THEME",
            stringPreferencesKey("language_config") to "REMOVED_LANGUAGE",
            stringPreferencesKey("distance_unit") to "REMOVED_UNIT",
        )

        val result = PreferencesRepository(FakeDataStore(preferences))
            .userPreferencesFlow
            .first()

        assertEquals(ThemeConfig.SYSTEM, result.themeConfig)
        assertEquals(LanguageConfig.SYSTEM, result.language)
        assertEquals(DistanceUnit.METRIC, result.distanceUnit)
    }

    private class FakeDataStore(initial: Preferences) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }
}
