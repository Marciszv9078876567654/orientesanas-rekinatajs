package com.orientesanasrekinatajs.ui.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orientesanasrekinatajs.data.preferences.PreferencesRepository
import com.orientesanasrekinatajs.domain.model.LanguageConfig
import com.orientesanasrekinatajs.domain.model.ThemeConfig
import com.orientesanasrekinatajs.domain.model.UserPreferences
import com.orientesanasrekinatajs.ui.theme.LocalAnimationsEnabled
import com.orientesanasrekinatajs.ui.theme.OrienteeringAppTheme
import com.orientesanasrekinatajs.ui.theme.Purple40
import com.orientesanasrekinatajs.ui.theme.Purple80
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase3Test {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeProvidesAnimationPreference() {
        var animationsEnabled = true

        composeRule.setContent {
            OrienteeringAppTheme(
                userPreferences = UserPreferences(useAnimations = false),
                dynamicColor = false,
            ) {
                animationsEnabled = LocalAnimationsEnabled.current
            }
        }

        composeRule.runOnIdle { assertFalse(animationsEnabled) }
    }

    @Test
    fun themeHonorsExplicitLightAndDarkPreferences() {
        var lightPrimary = Color.Unspecified
        var darkPrimary = Color.Unspecified

        composeRule.setContent {
            OrienteeringAppTheme(
                userPreferences = UserPreferences(themeConfig = ThemeConfig.LIGHT),
                dynamicColor = false,
            ) {
                lightPrimary = MaterialTheme.colorScheme.primary
            }
            OrienteeringAppTheme(
                userPreferences = UserPreferences(themeConfig = ThemeConfig.DARK),
                dynamicColor = false,
            ) {
                darkPrimary = MaterialTheme.colorScheme.primary
            }
        }

        composeRule.runOnIdle {
            assertEquals(Purple40, lightPrimary)
            assertEquals(Purple80, darkPrimary)
        }
    }

    @Test
    fun languageConfigMapsToApplicationLocales() {
        assertEquals("", localeListFor(LanguageConfig.SYSTEM).toLanguageTags())
        assertEquals("en", localeListFor(LanguageConfig.ENGLISH).toLanguageTags())
        assertEquals("lv", localeListFor(LanguageConfig.LATVIAN).toLanguageTags())
    }

    @Test
    fun viewModelReflectsRepositoryUpdates() = runBlocking {
        val repository = PreferencesRepository(FakeDataStore(mutablePreferencesOf()))
        val store = ViewModelStore()

        try {
            val viewModel = ViewModelProvider(
                store,
                SettingsViewModel.factory(repository),
            )[SettingsViewModel::class.java]
            val updatedPreferences = async {
                viewModel.userPreferences.first { it.themeConfig == ThemeConfig.DARK }
            }

            repository.updateTheme(ThemeConfig.DARK)

            assertEquals(ThemeConfig.DARK, updatedPreferences.await().themeConfig)
        } finally {
            store.clear()
        }
    }

    private class FakeDataStore(initial: Preferences) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = state

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = transform(state.value).also { state.value = it }
    }
}
