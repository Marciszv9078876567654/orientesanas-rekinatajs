package com.orientesanasrekinatajs.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Compact, precise single-position moves, separate from the drag handle. */
@Composable
internal fun MoveOrderArrows(
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    upDescription: String,
    downDescription: String,
    buttonSize: Dp = 28.dp,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Column(verticalArrangement = Arrangement.Center) {
            IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(buttonSize)) {
                Icon(Icons.Default.ArrowUpward, contentDescription = upDescription)
            }
            IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(buttonSize)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = downDescription)
            }
        }
    }
}
