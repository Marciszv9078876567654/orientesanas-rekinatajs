package com.orientesanasrekinatajs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity
import java.text.DateFormat
import java.util.Date

@Composable
internal fun RecentMapsDialog(
    maps: List<ScannedMapEntity>,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(stringResource(R.string.recent_maps)) },
        text = {
            if (maps.isEmpty()) {
                Text(stringResource(R.string.no_recent_maps))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    maps.take(8).forEach { map ->
                        OutlinedButton(
                            onClick = { onOpen(map.id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "${map.name}\n${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(map.timestamp))}",
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } },
    )
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
