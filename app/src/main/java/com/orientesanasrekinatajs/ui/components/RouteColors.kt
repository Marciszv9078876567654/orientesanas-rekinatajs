package com.orientesanasrekinatajs.ui.components

import androidx.compose.ui.graphics.Color

// Preserve existing indices so saved routes retain their colors.
private val routePalette = listOf(
    0xFF1565C0, 0xFFD81B60, 0xFF00897B, 0xFFF57C00, 0xFF7B1FA2,
    0xFF00ACC1, 0xFF558B2F, 0xFFE64A19, 0xFF5E35B1, 0xFFC0A000,
    0xFFE53935, 0xFF3949AB, 0xFF00C853, 0xFF8D6E63, 0xFFEC407A,
    0xFF546E7A,
)

fun routeColor(index: Int): Color = Color(routePalette[index.mod(routePalette.size)])
