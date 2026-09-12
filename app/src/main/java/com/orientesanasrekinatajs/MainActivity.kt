package com.orientesanasrekinatajs

import android.os.Bundle
import android.view.WindowManager
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
import com.orientesanasrekinatajs.ui.routing.RouteMode
import com.orientesanasrekinatajs.ui.routing.RoutingViewModel
import com.orientesanasrekinatajs.ui.savedmaps.SavedMapsViewModel
import com.orientesanasrekinatajs.ui.settings.SettingsViewModel
import com.orientesanasrekinatajs.ui.settings.setApplicationLocale
import com.orientesanasrekinatajs.ui.theme.OrienteeringAppTheme
import com.orientesanasrekinatajs.ui.transfer.MapTransferViewModel

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
    private val mapTransferViewModel: MapTransferViewModel by viewModels {
        MapTransferViewModel.factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        setContent {
            val userPreferences by settingsViewModel.userPreferences.collectAsStateWithLifecycle()
            val processingState by mapProcessingViewModel.uiState.collectAsStateWithLifecycle()
            val routingState by routingViewModel.uiState.collectAsStateWithLifecycle()
            val savedMapsState by savedMapsViewModel.uiState.collectAsStateWithLifecycle()
            val mapTransferState by mapTransferViewModel.uiState.collectAsStateWithLifecycle()

            LaunchedEffect(userPreferences.language) {
                setApplicationLocale(userPreferences.language)
            }

            OrienteeringAppTheme(userPreferences = userPreferences) {
                OrienteeringApp(
                    processingState = processingState,
                    routingState = routingState,
                    userPreferences = userPreferences,
                    savedMapsState = savedMapsState,
                    mapTransferState = mapTransferState,
                    onImageSelected = { uri ->
                        routingViewModel.reset()
                        mapProcessingViewModel.processImage(uri)
                    },
                    onApplyBoundary = { boundary ->
                        routingViewModel.reset()
                        mapProcessingViewModel.applyBoundary(boundary)
                    },
                    onApplyEditedBoundary = { boundary, lineStart, lineEnd, lineDistance ->
                        mapProcessingViewModel.applyEditedBoundary(
                            boundary, lineStart, lineEnd, lineDistance,
                        )
                    },
                    onUpdateControlPoint = { point ->
                        mapProcessingViewModel.updateControlPoint(point)
                    },
                    onAddControlPoint = { point ->
                        mapProcessingViewModel.addControlPoint(point)
                    },
                    onRemoveControlPoint = { id ->
                        mapProcessingViewModel.removeControlPoint(id)
                    },
                    onClearControlPoints = {
                        mapProcessingViewModel.clearControlPoints()
                    },
                    onStartColorCalibration = mapProcessingViewModel::startColorCalibration,
                    onApplyColorCalibrationSample = mapProcessingViewModel::applyColorCalibrationSample,
                    onMoveColorCalibrationReference = mapProcessingViewModel::moveColorCalibrationReference,
                    onClearColorCalibrationReferences = mapProcessingViewModel::clearColorCalibrationReferences,
                    onConfirmColorCalibration = mapProcessingViewModel::confirmColorCalibration,
                    onCancelColorCalibration = mapProcessingViewModel::cancelColorCalibration,
                    onDismissProcessingError = mapProcessingViewModel::dismissError,
                    onDismissReviewSummary = mapProcessingViewModel::dismissReviewSummary,
                    onRestoreMapState = mapProcessingViewModel::restoreEditState,
                    onInvalidateRoute = routingViewModel::invalidateRoute,
                    onCalculateRoute = { pixelsPerMeter, mode, budgetMeters, targetScore ->
                        routingViewModel.calculateRoute(
                            detectedPoints = processingState.controlPoints,
                            pixelsPerMeter = pixelsPerMeter,
                            mode = mode,
                            budgetMeters = budgetMeters,
                            targetScore = targetScore,
                        )
                    },
                    onGenerateAlternativeRoutes = { points, pixelsPerMeter, criteria ->
                        routingViewModel.generateAlternativeRoutes(points, pixelsPerMeter, criteria)
                    },
                    onManageRoutes = routingViewModel::manageRoutes,
                    onManageRouteRestrictions = routingViewModel::manageRouteRestrictions,
                    onExportMap = mapTransferViewModel::export,
                    onExportPdf = mapTransferViewModel::exportPdf,
                    onImportMap = { uri ->
                        mapTransferViewModel.import(uri) { imported ->
                            routingViewModel.reset()
                            mapProcessingViewModel.openImportedMap(imported)
                            routingViewModel.openSavedRestrictions(imported.routeRestrictions)
                            imported.route?.let { importedRoute ->
                                routingViewModel.openSavedRoute(
                                    route = importedRoute,
                                    alternativeRoutes = imported.alternativeRoutes,
                                    routeMetadata = imported.routeMetadata,
                                    points = imported.points,
                                    pixelsPerMeter = imported.pixelsPerMeter,
                                    selectedRoutePointIds = imported.selectedRoutePointIds,
                                    selectedRouteId = imported.selectedRouteId,
                                    mode = runCatching { RouteMode.valueOf(imported.routeMode) }
                                        .getOrDefault(RouteMode.SHORTEST),
                                    budgetMeters = imported.routeBudgetMeters,
                                    targetScore = imported.routeTargetScore,
                                    routeRestrictions = imported.routeRestrictions,
                                )
                            }
                        }
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
                            routingViewModel.openSavedRestrictions(saved.routeRestrictions)
                            saved.route?.let { savedRoute ->
                                routingViewModel.openSavedRoute(
                                    route = savedRoute,
                                    alternativeRoutes = saved.alternativeRoutes,
                                    routeMetadata = saved.routeMetadata,
                                    points = saved.points,
                                    pixelsPerMeter = saved.pixelsPerMeter,
                                    selectedRoutePointIds = saved.selectedRoutePointIds,
                                    selectedRouteId = saved.selectedRouteId,
                                    mode = runCatching { RouteMode.valueOf(saved.routeMode) }
                                        .getOrDefault(RouteMode.SHORTEST),
                                    budgetMeters = saved.routeBudgetMeters,
                                    targetScore = saved.routeTargetScore,
                                    routeRestrictions = saved.routeRestrictions,
                                )
                            }
                        }
                    },
                    onRenameSavedMap = { id, name, onRenamed ->
                        savedMapsViewModel.rename(id, name) { renamed ->
                            if (mapProcessingViewModel.uiState.value.savedMapId == id) {
                                mapProcessingViewModel.renameSavedMap(renamed.name)
                            }
                            onRenamed(renamed)
                        }
                    },
                    onDeleteSavedMap = savedMapsViewModel::delete,
                    onCopySavedMap = savedMapsViewModel::copy,
                    onClearAllSavedMaps = savedMapsViewModel::clearAll,
                    onDismissSavedMapsEvent = savedMapsViewModel::clearEvent,
                    onDismissMapTransferEvent = mapTransferViewModel::clearEvent,
                    onReset = {
                        routingViewModel.reset()
                        mapProcessingViewModel.reset()
                    },
                    onUpdateTheme = settingsViewModel::updateTheme,
                    onUpdateLanguage = settingsViewModel::updateLanguage,
                    onUpdateAnimations = settingsViewModel::updateAnimations,
                    onUpdateMapRotationGestures = settingsViewModel::updateMapRotationGestures,
                    onUpdateVibrationFeedback = settingsViewModel::updateVibrationFeedback,
                    onUpdateUsageTips = settingsViewModel::updateUsageTips,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
