package com.orientesanasrekinatajs

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orientesanasrekinatajs.data.preferences.PreferencesRepository
import com.orientesanasrekinatajs.ui.OrienteeringApp
import com.orientesanasrekinatajs.ui.processing.MapProcessingViewModel
import com.orientesanasrekinatajs.ui.routing.RoutingViewModel
import com.orientesanasrekinatajs.ui.savedmaps.SavedMapsViewModel
import com.orientesanasrekinatajs.ui.settings.SettingsViewModel
import com.orientesanasrekinatajs.ui.settings.setApplicationLocale
import com.orientesanasrekinatajs.ui.theme.OrienteeringAppTheme

class MainActivity : AppCompatActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.factory(PreferencesRepository.create(applicationContext))
    }
    private val mapProcessingViewModel: MapProcessingViewModel by viewModels {
        MapProcessingViewModel.factory(applicationContext)
    }
    private val routingViewModel: RoutingViewModel by viewModels()
    private val savedMapsViewModel: SavedMapsViewModel by viewModels {
        SavedMapsViewModel.factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val userPreferences by settingsViewModel.userPreferences.collectAsStateWithLifecycle()
            val processingState by mapProcessingViewModel.uiState.collectAsStateWithLifecycle()
            val routingState by routingViewModel.uiState.collectAsStateWithLifecycle()
            val savedMapsState by savedMapsViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(userPreferences.language) {
                setApplicationLocale(userPreferences.language)
            }

            OrienteeringAppTheme(userPreferences = userPreferences) {
                OrienteeringApp(
                    processingState = processingState,
                    routingState = routingState,
                    userPreferences = userPreferences,
                    savedMapsState = savedMapsState,
                    onImageSelected = { uri ->
                        routingViewModel.reset()
                        mapProcessingViewModel.processImage(uri)
                    },
                    onApplyBoundary = { boundary ->
                        routingViewModel.reset()
                        mapProcessingViewModel.applyBoundary(boundary)
                    },
                    onApplyEditedBoundary = { boundary ->
                        routingViewModel.reset()
                        mapProcessingViewModel.applyEditedBoundary(boundary)
                    },
                    onUpdateControlPoint = { point ->
                        routingViewModel.reset()
                        mapProcessingViewModel.updateControlPoint(point)
                    },
                    onAddControlPoint = { point ->
                        routingViewModel.reset()
                        mapProcessingViewModel.addControlPoint(point)
                    },
                    onRemoveControlPoint = { id ->
                        routingViewModel.reset()
                        mapProcessingViewModel.removeControlPoint(id)
                    },
                    onClearControlPoints = {
                        routingViewModel.reset()
                        mapProcessingViewModel.clearControlPoints()
                    },
                    onCalculateRoute = { pixelsPerMeter, mode, budgetMeters ->
                        routingViewModel.calculateRoute(
                            detectedPoints = processingState.controlPoints,
                            pixelsPerMeter = pixelsPerMeter,
                            mode = mode,
                            budgetMeters = budgetMeters,
                        )
                    },
                    onSaveMap = { draft, onSaved ->
                        savedMapsViewModel.save(draft) { saved ->
                            mapProcessingViewModel.markSaved(saved, draft)
                            onSaved(saved)
                        }
                    },
                    onLoadSavedMap = { id ->
                        savedMapsViewModel.load(id) { saved ->
                            routingViewModel.reset()
                            mapProcessingViewModel.openSavedMap(saved)
                            routingViewModel.openSavedRoute(saved.route)
                        }
                    },
                    onRenameSavedMap = { id, name, onRenamed ->
                        savedMapsViewModel.rename(id, name) { renamed ->
                            mapProcessingViewModel.renameSavedMap(renamed.name)
                            onRenamed(renamed)
                        }
                    },
                    onDeleteSavedMap = savedMapsViewModel::delete,
                    onClearAllSavedMaps = savedMapsViewModel::clearAll,
                    onDismissSavedMapsEvent = savedMapsViewModel::clearEvent,
                    onReset = {
                        routingViewModel.reset()
                        mapProcessingViewModel.reset()
                    },
                    onUpdateTheme = settingsViewModel::updateTheme,
                    onUpdateLanguage = settingsViewModel::updateLanguage,
                    onUpdateAnimations = settingsViewModel::updateAnimations,
                    onUpdateUsageTips = settingsViewModel::updateUsageTips,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
