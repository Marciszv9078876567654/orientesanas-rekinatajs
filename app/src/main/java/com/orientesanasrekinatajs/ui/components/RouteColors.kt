package com.orientesanasrekinatajs.ui.components

import androidx.compose.ui.graphics.Color

fun routeColor(index: Int): Color = listOf(
    0xFF1565C0, 0xFFD81B60, 0xFF00897B, 0xFFF57C00, 0xFF7B1FA2,
    0xFF00ACC1, 0xFF558B2F, 0xFFE64A19, 0xFF5E35B1, 0xFFC0A000,
).let { Color(it[index.mod(it.size)]) }
