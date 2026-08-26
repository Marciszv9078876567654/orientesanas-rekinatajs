package com.orientesanasrekinatajs.ui.routing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.RouteSegment
import com.orientesanasrekinatajs.domain.routing.DistanceMatrix
import com.orientesanasrekinatajs.domain.routing.RoutingAlgorithms
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class RouteMode { SHORTEST, BEST_SCORE, TARGET_SCORE }

data class RoutingUiState(
    val mode: RouteMode = RouteMode.SHORTEST,
    val isCalculating: Boolean = false,
    val route: OptimizedRoute? = null,
    val alternativeRoutes: List<OptimizedRoute> = emptyList(),
    val nextLongestRouteCount: Int = 0,
    val selectedRoutePointIds: List<String> = emptyList(),
    val budgetMeters: Float? = null,
    val targetScore: Int? = null,
    val error: String? = null,
)

/** Builds the distance matrix, selects the requested heuristic, and assembles route statistics. */
class RoutingViewModel internal constructor(
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RoutingUiState())
    val uiState: StateFlow<RoutingUiState> = _uiState.asStateFlow()
    val optimizedRoute: StateFlow<OptimizedRoute?> = uiState
        .map { state -> state.route }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var routingJob: Job? = null

    fun calculateRoute(
        detectedPoints: List<ControlPoint>,
        pixelsPerMeter: Float,
        mode: RouteMode,
        budgetMeters: Float? = null,
        targetScore: Int? = null,
    ) {
        val points = detectedPoints.map(ControlPoint::copy)
        routingJob?.cancel()
        routingJob = viewModelScope.launch {
            _uiState.value = RoutingUiState(
                mode = mode,
                isCalculating = true,
                budgetMeters = budgetMeters,
                targetScore = targetScore,
            )
            runCatching {
                withContext(workerDispatcher) {
                    calculate(points, pixelsPerMeter, mode, budgetMeters, targetScore)
                }
            }.onSuccess { result ->
                _uiState.value = RoutingUiState(
                    mode = mode,
                    route = result.primary,
                    alternativeRoutes = result.alternatives.routes,
                    nextLongestRouteCount = result.alternatives.nextLongestCount,
                    selectedRoutePointIds = result.primary.path.map(ControlPoint::id),
                    budgetMeters = budgetMeters,
                    targetScore = targetScore,
                )
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    _uiState.value = RoutingUiState(
                        mode = mode,
                        budgetMeters = budgetMeters,
                        targetScore = targetScore,
                        error = throwable.message ?: "Route calculation failed",
                    )
                }
            }
        }
    }

    fun reset() {
        routingJob?.cancel()
        _uiState.value = RoutingUiState()
    }

    fun openSavedRoute(
        route: OptimizedRoute,
        points: List<ControlPoint>,
        pixelsPerMeter: Float,
        selectedRoutePointIds: List<String>,
        mode: RouteMode,
        budgetMeters: Float?,
        targetScore: Int?,
    ) {
        routingJob?.cancel()
        _uiState.value = RoutingUiState(
            route = route,
            selectedRoutePointIds = selectedRoutePointIds,
            mode = mode,
            budgetMeters = budgetMeters,
            targetScore = targetScore,
        )
        routingJob = viewModelScope.launch {
            val alternatives = runCatching {
                withContext(workerDispatcher) {
                    val matrix = DistanceMatrix(points.distinctBy(ControlPoint::id), pixelsPerMeter)
                    // The original budget/mode is not persisted, but loaded routes can still
                    // derive useful higher- and lower-score neighbors from all saved controls.
                    buildAlternativeRoutes(route, matrix, mode)
                }
            }.getOrDefault(AlternativeRouteSet(emptyList(), 0))
            _uiState.value = RoutingUiState(
                route = route,
                alternativeRoutes = alternatives.routes,
                nextLongestRouteCount = alternatives.nextLongestCount,
                selectedRoutePointIds = selectedRoutePointIds,
                mode = mode,
                budgetMeters = budgetMeters,
                targetScore = targetScore,
            )
        }
    }

    private fun calculate(
        points: List<ControlPoint>,
        pixelsPerMeter: Float,
        mode: RouteMode,
        budgetMeters: Float?,
        targetScore: Int?,
    ): RouteCalculation {
        require(pixelsPerMeter.isFinite() && pixelsPerMeter > 0f) {
            "Map scale must be a positive number of pixels per meter"
        }
        val combinedEndpoints = points.filter { it.type == ControlPointType.START_FINISH }
        val explicitStarts = points.filter { it.type == ControlPointType.START }
        val explicitFinishes = points.filter { it.type == ControlPointType.FINISH }
        val start = uniqueEndpoint(explicitStarts.ifEmpty { combinedEndpoints }, "start")
        val finish = uniqueEndpoint(explicitFinishes.ifEmpty { combinedEndpoints }, "finish")
        val controls = points.filter { it.type == ControlPointType.CONTROL }
        val matrixPoints = (listOf(start, finish) + controls).distinctBy(ControlPoint::id)
        val matrix = DistanceMatrix(matrixPoints, pixelsPerMeter)

        val path = when (mode) {
            RouteMode.SHORTEST -> RoutingAlgorithms.shortestRoute(matrix, start, finish, controls)
            RouteMode.BEST_SCORE -> {
                val budget = requireNotNull(budgetMeters) {
                    "A distance budget is required for best-score mode"
                }
                RoutingAlgorithms.bestScoreRoute(matrix, start, finish, controls, budget)
                    .ifEmpty { throw IllegalArgumentException("The finish is outside the distance budget") }
            }
            RouteMode.TARGET_SCORE -> RoutingAlgorithms.shortestRouteForScore(
                matrix,
                start,
                finish,
                controls,
                requireNotNull(targetScore) { "A target score is required" },
            )
        }
        val primary = assembleRoute(path, matrix)
        return RouteCalculation(
            primary = primary,
            alternatives = buildAlternativeRoutes(primary, matrix, mode),
        )
    }

    /** Builds routes immediately above and below the primary route in score and distance. */
    private fun buildAlternativeRoutes(
        primary: OptimizedRoute,
        matrix: DistanceMatrix,
        mode: RouteMode,
    ): AlternativeRouteSet {
        val nextLongerRoutes = mutableListOf<OptimizedRoute>()
        if (mode != RouteMode.SHORTEST) {
            var current = primary
            for (ignored in 0 until MAX_NEXT_LONGEST_ALTERNATIVES) {
                val currentControls = current.path.filter { it.type == ControlPointType.CONTROL }
                val currentControlIds = currentControls.mapTo(mutableSetOf(), ControlPoint::id)
                val nextLonger = matrix.points
                    .filter { point ->
                        point.type == ControlPointType.CONTROL &&
                            point.points > 0 &&
                            point.id !in currentControlIds
                    }
                    .map { added ->
                        val path = RoutingAlgorithms.shortestRoute(
                            matrix,
                            current.path.first(),
                            current.path.last(),
                            currentControls + added,
                        )
                        assembleRoute(path, matrix)
                    }
                    .filter { candidate ->
                        candidate.totalScore > current.totalScore &&
                            candidate.totalDistanceMeters >
                            current.totalDistanceMeters + DISTANCE_EPSILON
                    }
                    .minWithOrNull(
                        compareBy<OptimizedRoute> { it.totalDistanceMeters }
                            .thenByDescending { it.totalScore },
                    ) ?: break
                nextLongerRoutes += nextLonger
                current = nextLonger
            }
        }

        val lowerScoreRoutes = mutableListOf<OptimizedRoute>()
        var currentPath = primary.path
        while (lowerScoreRoutes.size < MAX_LOWER_SCORE_ALTERNATIVES) {
            val controls = currentPath.filter { it.type == ControlPointType.CONTROL }
            val scoringControls = controls.filter { it.points > 0 }
            if (scoringControls.isEmpty()) break
            val candidates = scoringControls.map { omitted ->
                val remaining = controls.filterNot { it.id == omitted.id }
                val path = RoutingAlgorithms.shortestRoute(
                    matrix,
                    currentPath.first(),
                    currentPath.last(),
                    remaining,
                )
                assembleRoute(path, matrix)
            }
            val shortest = candidates.minWith(
                compareBy<OptimizedRoute> { it.totalDistanceMeters }
                    .thenByDescending { it.totalScore },
            )
            if (
                shortest.totalScore < primary.totalScore &&
                shortest.totalDistanceMeters < primary.totalDistanceMeters - DISTANCE_EPSILON
            ) {
                lowerScoreRoutes += shortest
            }
            currentPath = shortest.path
        }
        val descendingHigherScoreRoutes = nextLongerRoutes.sortedWith(
            compareByDescending<OptimizedRoute> { it.totalScore - primary.totalScore }
                .thenBy { it.totalDistanceMeters },
        )
        return AlternativeRouteSet(
            routes = descendingHigherScoreRoutes + lowerScoreRoutes,
            nextLongestCount = nextLongerRoutes.size,
        )
    }

    private fun assembleRoute(
        path: List<ControlPoint>,
        matrix: DistanceMatrix,
    ): OptimizedRoute {
        var accumulatedDistance = 0f
        var accumulatedPoints = 0
        val segments = path.zipWithNext { from, to ->
            val distance = matrix[from, to]
            accumulatedDistance += distance
            if (to.type == ControlPointType.CONTROL) accumulatedPoints += to.points
            RouteSegment(
                from = from,
                to = to,
                distanceMeters = distance,
                accumulatedDistanceMeters = accumulatedDistance,
                accumulatedPoints = accumulatedPoints,
            )
        }
        return OptimizedRoute(
            path = path,
            totalDistanceMeters = accumulatedDistance,
            totalScore = accumulatedPoints,
            segments = segments,
        )
    }

    private fun uniqueEndpoint(candidates: List<ControlPoint>, name: String): ControlPoint {
        require(candidates.size == 1) {
            "Exactly one $name point is required; found ${candidates.size}"
        }
        return candidates.single()
    }

    private data class RouteCalculation(
        val primary: OptimizedRoute,
        val alternatives: AlternativeRouteSet,
    )

    private data class AlternativeRouteSet(
        val routes: List<OptimizedRoute>,
        val nextLongestCount: Int,
    )

    private companion object {
        const val MAX_LOWER_SCORE_ALTERNATIVES = 3
        const val MAX_NEXT_LONGEST_ALTERNATIVES = 2
        const val DISTANCE_EPSILON = 0.0001f
    }
}
