package com.orientesanasrekinatajs.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orientesanasrekinatajs.ui.theme.LocalLongPressFeedback
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity
import java.text.DateFormat
import java.util.Date

@Composable
internal fun RecentMapsDialog(
    maps: List<ScannedMapEntity>,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onCopy: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val longPressFeedback = LocalLongPressFeedback.current
    var menuMapId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameMapId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteMapId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    val menuGap = with(LocalDensity.current) { 6.dp.roundToPx() }
    val menuPosition = remember(menuGap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val below = anchorBounds.bottom + menuGap
                val above = anchorBounds.top - menuGap - popupContentSize.height
                return IntOffset(
                    x = (if (layoutDirection == LayoutDirection.Ltr) anchorBounds.left
                        else anchorBounds.right - popupContentSize.width).coerceIn(
                        0, (windowSize.width - popupContentSize.width).coerceAtLeast(0),
                    ),
                    y = (if (below + popupContentSize.height <= windowSize.height) below else above)
                        .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(stringResource(R.string.recent_maps)) },
        text = {
            if (maps.isEmpty()) {
                Text(stringResource(R.string.no_recent_maps))
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    maps.forEach { map ->
                        key(map.id) {
                            val copyName = stringResource(R.string.route_copy_name, map.name)
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).combinedClickable(
                                        role = Role.Button,
                                        onClick = { onOpen(map.id) },
                                        onLongClick = { longPressFeedback(); menuMapId = map.id },
                                        hapticFeedbackEnabled = false,
                                    ),
                                ) {
                                    Text(
                                        "${map.name}\n${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(map.timestamp))}",
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                                    )
                                }
                                if (menuMapId == map.id) {
                                    Popup(
                                        popupPositionProvider = menuPosition,
                                        onDismissRequest = { menuMapId = null },
                                        properties = PopupProperties(focusable = true),
                                    ) {
                                        MapDropdownContent {
                                            Surface(
                                                modifier = Modifier.width(IntrinsicSize.Max),
                                                shape = MaterialTheme.shapes.extraSmall,
                                                color = MaterialTheme.colorScheme.surfaceContainer,
                                                tonalElevation = 3.dp,
                                                shadowElevation = 8.dp,
                                            ) {
                                                Column {
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.rename_route)) },
                                                        leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, null) },
                                                        onClick = {
                                                            menuMapId = null
                                                            renameText = map.name
                                                            renameMapId = map.id
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.copy_map)) },
                                                        leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                                                        onClick = {
                                                            menuMapId = null
                                                            onCopy(map.id, copyName)
                                                        },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.delete_saved_route)) },
                                                        leadingIcon = { Icon(Icons.Default.Delete, null) },
                                                        onClick = {
                                                            menuMapId = null
                                                            deleteMapId = map.id
                                                        },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } },
    )

    maps.firstOrNull { it.id == renameMapId }?.let { map ->
        AlertDialog(
            onDismissRequest = { renameMapId = null },
            title = { CenteredDialogTitle(stringResource(R.string.rename_route)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it.take(60) },
                    label = { Text(stringResource(R.string.route_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        onRename(map.id, renameText.trim())
                        renameMapId = null
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameMapId = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    maps.firstOrNull { it.id == deleteMapId }?.let { map ->
        ConfirmationDialog(
            title = stringResource(R.string.delete_saved_route_title),
            message = "${map.name}\n\n${stringResource(R.string.delete_saved_route_confirmation)}",
            onConfirm = {
                onDelete(map.id)
                deleteMapId = null
            },
            onDismiss = { deleteMapId = null },
        )
    }
}

@Composable
internal fun MessageDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } },
    )
}

@Composable
internal fun ConfirmationDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun CenteredDialogTitle(title: String) {
    Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}
