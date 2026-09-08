package com.orientesanasrekinatajs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.orientesanasrekinatajs.R
import com.orientesanasrekinatajs.domain.model.LanguageConfig
import com.orientesanasrekinatajs.domain.model.ThemeConfig
import com.orientesanasrekinatajs.domain.model.UserPreferences

@Composable
internal fun SettingsDialog(
    preferences: UserPreferences,
    onUpdateTheme: (ThemeConfig) -> Unit,
    onUpdateLanguage: (LanguageConfig) -> Unit,
    onUpdateAnimations: (Boolean) -> Unit,
    onUpdateMapRotationGestures: (Boolean) -> Unit,
    onUpdateUsageTips: (Boolean) -> Unit,
    onClearAllSavedMaps: () -> Unit,
    hasSavedMaps: Boolean,
    onDismiss: () -> Unit,
) {
    var confirmClearSavedMaps by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsChipGroup(
                    label = stringResource(R.string.theme),
                    options = ThemeConfig.entries,
                    selected = preferences.themeConfig,
                    optionLabel = { themeLabel(it) },
                    onSelect = onUpdateTheme,
                )
                HorizontalDivider()
                SettingsChipGroup(
                    label = stringResource(R.string.language),
                    options = LanguageConfig.entries,
                    selected = preferences.language,
                    optionLabel = { languageLabel(it) },
                    onSelect = onUpdateLanguage,
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.animations), style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = preferences.useAnimations,
                        onCheckedChange = onUpdateAnimations,
                        modifier = Modifier.graphicsLayer(scaleX = 0.85f, scaleY = 0.85f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        stringResource(R.string.map_rotation),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Switch(
                        checked = preferences.disableMapRotationGestures,
                        onCheckedChange = onUpdateMapRotationGestures,
                        modifier = Modifier.graphicsLayer(scaleX = 0.85f, scaleY = 0.85f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.usage_tips), style = MaterialTheme.typography.labelLarge)
                    Switch(
                        checked = preferences.showUsageTips,
                        onCheckedChange = onUpdateUsageTips,
                        modifier = Modifier.graphicsLayer(scaleX = 0.85f, scaleY = 0.85f),
                    )
                }
                HorizontalDivider()
                TextButton(
                    onClick = { confirmClearSavedMaps = true },
                    enabled = hasSavedMaps,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Text(stringResource(R.string.clear_saved_maps))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
    if (confirmClearSavedMaps) {
        ConfirmationDialog(
            title = stringResource(R.string.clear_saved_maps_title),
            message = stringResource(R.string.clear_saved_maps_confirmation),
            onConfirm = { onClearAllSavedMaps(); confirmClearSavedMaps = false },
            onDismiss = { confirmClearSavedMaps = false },
        )
    }
}

@Composable
private fun <T> SettingsChipGroup(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

@Composable
private fun themeLabel(theme: ThemeConfig): String = stringResource(
    when (theme) {
        ThemeConfig.SYSTEM -> R.string.theme_system
        ThemeConfig.LIGHT -> R.string.theme_light
        ThemeConfig.DARK -> R.string.theme_dark
    },
)

@Composable
private fun languageLabel(language: LanguageConfig): String = stringResource(
    when (language) {
        LanguageConfig.SYSTEM -> R.string.language_system
        LanguageConfig.ENGLISH -> R.string.language_english
        LanguageConfig.LATVIAN -> R.string.language_latvian
    },
)
