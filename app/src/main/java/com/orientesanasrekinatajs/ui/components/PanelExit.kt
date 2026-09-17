package com.orientesanasrekinatajs.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.orientesanasrekinatajs.ui.theme.LocalAnimationsEnabled

/** Translate the intact panel away before removing its content. */
@Composable
internal fun panelExitModifier(closing: Boolean, onClosed: () -> Unit): Modifier {
    val progress = remember { Animatable(0f) }
    val animationsEnabled = LocalAnimationsEnabled.current
    val latestOnClosed by rememberUpdatedState(onClosed)
    LaunchedEffect(closing, animationsEnabled) {
        if (closing) {
            if (animationsEnabled) progress.animateTo(1f, tween(180))
            latestOnClosed()
        } else progress.snapTo(0f)
    }
    return Modifier.graphicsLayer { translationY = size.height * progress.value }
}
