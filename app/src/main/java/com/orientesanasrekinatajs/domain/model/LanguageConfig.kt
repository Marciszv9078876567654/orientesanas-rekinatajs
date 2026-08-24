package com.orientesanasrekinatajs.domain.model

/**
 * The user's language preference.
 *
 * [SYSTEM] follows the device setting. The [tag] is the BCP-47 language code used by
 * `AppCompatDelegate.setApplicationLocales`; an empty string means "system default".
 *
 * @property tag BCP-47 language tag (e.g. "en", "lv"). Empty for [SYSTEM].
 */
enum class LanguageConfig(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    LATVIAN("lv"),
}
