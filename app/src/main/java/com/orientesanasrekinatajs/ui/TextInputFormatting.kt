package com.orientesanasrekinatajs.ui

internal fun String.localizedFloatOrNull(): Float? = replace(',', '.').toFloatOrNull()
