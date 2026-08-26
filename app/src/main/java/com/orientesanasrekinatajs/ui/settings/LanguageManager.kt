package com.orientesanasrekinatajs.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.orientesanasrekinatajs.domain.model.LanguageConfig

/** Applies an app language immediately and lets AppCompat recreate the UI when required. */
fun setApplicationLocale(language: LanguageConfig) {
    val locales = localeListFor(language)
    if (AppCompatDelegate.getApplicationLocales() != locales) {
        AppCompatDelegate.setApplicationLocales(locales)
    }
}

internal fun localeListFor(language: LanguageConfig): LocaleListCompat =
    if (language == LanguageConfig.SYSTEM) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(language.tag)
    }
