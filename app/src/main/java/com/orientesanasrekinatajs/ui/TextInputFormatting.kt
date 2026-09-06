package com.orientesanasrekinatajs.ui

internal fun String.localizedFloatOrNull(): Float? = replace(',', '.').toFloatOrNull()

/** Scores are derived from code / 10, including 1- and 2-point controls. */
internal fun isValidControlCode(text: String): Boolean = text.toIntOrNull() in 10..999
