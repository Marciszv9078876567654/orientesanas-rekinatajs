package com.orientesanasrekinatajs.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.orientesanasrekinatajs.data.preferences.PreferencesRepository
import com.orientesanasrekinatajs.domain.model.DistanceUnit
import com.orientesanasrekinatajs.domain.model.LanguageConfig
import com.orientesanasrekinatajs.domain.model.ThemeConfig
import com.orientesanasrekinatajs.domain.model.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bridges persisted settings to lifecycle-aware UI state. */
class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val userPreferences: StateFlow<UserPreferences> =
        preferencesRepository.userPreferencesFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = UserPreferences(),
        )

    fun updateTheme(theme: ThemeConfig) {
        viewModelScope.launch { preferencesRepository.updateTheme(theme) }
    }

    fun updateLanguage(language: LanguageConfig) {
        viewModelScope.launch { preferencesRepository.updateLanguage(language) }
    }

    fun updateAnimations(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateAnimations(enabled) }
    }

    fun updateUsageTips(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateUsageTips(enabled) }
    }

    fun updateDistanceUnit(unit: DistanceUnit) {
        viewModelScope.launch { preferencesRepository.updateDistanceUnit(unit) }
    }

    fun updateDefaultDistanceBudgetKm(budgetKm: Float) {
        viewModelScope.launch {
            preferencesRepository.updateDefaultDistanceBudgetKm(budgetKm)
        }
    }

    companion object {
        fun factory(repository: PreferencesRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { SettingsViewModel(repository) }
            }
    }
}
