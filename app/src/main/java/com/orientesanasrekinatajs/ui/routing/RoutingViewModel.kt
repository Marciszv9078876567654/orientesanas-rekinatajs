package com.orientesanasrekinatajs.ui.routing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.RouteSegment
import com.orientesanasrekinatajs.domain.model.RouteMetadata
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

sealed interface RouteManagementAction {
    data class Apply(
        val orderedRoutes: List<OptimizedRoute>,
        val metadata: Map<String, RouteMetadata>,
    ) : RouteManagementAction

    data class Select(val routeId: String) : RouteManagementAction
}

data class AlternativeRouteCriteria(
    val count: Int,
    val sourceRouteId: String? = null,
    val minDistanceMeters: Float? = null,
    val maxDistanceMeters: Float? = null,
    val minScore: Int? = null,
    val maxScore: Int? = null,
    val useRelativeValues: Boolean = false,
)

data class RoutingUiState(
    val mode: RouteMode = RouteMode.SHORTEST,
    val isCalculating: Boolean = false,
    val isGeneratingAlternatives: Boolean = false,
    val route: OptimizedRoute? = null,
    val alternativeRoutes: List<OptimizedRoute> = emptyList(),
    val nextLongestRouteCount: Int = 0,
    val selectedRoutePointIds: List<String> = emptyList(),
    val selectedRouteId: String? = null,
    val budgetMeters: Float? = null,
    val targetScore: Int? = null,
    val routeMetadata: Map<String, RouteMetadata> = emptyMap(),
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
            val previous = _uiState.value
            _uiState.value = previous.copy(
                mode = mode, isCalculating = true, budgetMeters = budgetMeters,
                targetScore = targetScore, error = null,
            )
            runCatching {
                withContext(workerDispatcher) {
                    calculate(points, pixelsPerMeter, mode, budgetMeters, targetScore)
                }
            }.onSuccess { result ->
                val currentRoutes = listOfNotNull(previous.route) + previous.alternativeRoutes
                val newRoute = result.primary
                val primary = previous.route ?: newRoute
                val regularRoutes = currentRoutes.filterNot { previous.metadataFor(it).isAlternative }
                val generatedAlternatives = currentRoutes.filter { previous.metadataFor(it).isAlternative }
                val ordered = if (previous.route == null) listOf(newRoute) else {
                    regularRoutes + newRoute + generatedAlternatives.sortedByDescending(OptimizedRoute::totalScore)
                }
                val name = nextRouteName(currentRoutes.mapIndexed { index, existingRoute ->
                    previous.routeMetadata[existingRoute.id]?.name?.takeIf(String::isNotBlank)
                        ?: defaultRouteName(index)
                })
                val metadata = previous.routeMetadata + (newRoute.id to RouteMetadata(
                    name = name,
                    order = ordered.indexOf(newRoute),
                    isDisplayed = false,
                    colorIndex = nextColorIndex(previous.routeMetadata),
                ))
                publishRoutes(
                    routes = ordered,
                    primary = primary,
                    selected = newRoute,
                    metadata = metadata,
                    mode = mode,
                    budgetMeters = budgetMeters,
                    targetScore = targetScore,
                )
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    _uiState.value = previous.copy(
                        mode = mode, isCalculating = false, budgetMeters = budgetMeters,
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

    fun invalidateRoute() {
        routingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isCalculating = false,
            isGeneratingAlternatives = false,
            route = null,
            alternativeRoutes = emptyList(),
            nextLongestRouteCount = 0,
            selectedRoutePointIds = emptyList(),
            selectedRouteId = null,
            routeMetadata = emptyMap(),
            error = null,
        )
    }

    fun openSavedRoute(
        route: OptimizedRoute,
        alternativeRoutes: List<OptimizedRoute>,
        routeMetadata: Map<String, RouteMetadata>,
        points: List<ControlPoint>,
        pixelsPerMeter: Float,
        selectedRoutePointIds: List<String>,
        selectedRouteId: String? = null,
        mode: RouteMode,
        budgetMeters: Float?,
        targetScore: Int?,
    ) {
        routingJob?.cancel()
        val legacySelectedRoute = runCatching {
            val pointsById = points.associateBy(ControlPoint::id)
            val selectedPath = selectedRoutePointIds.map { id -> requireNotNull(pointsById[id]) }
            require(selectedPath.size >= 2)
            assembleRoute(
                selectedPath,
                DistanceMatrix(points.distinctBy(ControlPoint::id), pixelsPerMeter),
            )
        }.getOrDefault(route)
        val primaryRoute = if (alternativeRoutes.isEmpty()) legacySelectedRoute else route
        val legacyOrderedRoutes = alternativeRoutes
            .sortedWith(
                compareByDescending<OptimizedRoute> { it.totalScore > primaryRoute.totalScore }
                    .thenByDescending { it.totalScore }
                    .thenBy { it.totalDistanceMeters },
            )
            .let { alternatives ->
                val beforePrimary = alternatives.count { it.totalScore > primaryRoute.totalScore }
                alternatives.take(beforePrimary) + primaryRoute + alternatives.drop(beforePrimary)
            }
        val normalizedMetadata = (listOf(primaryRoute) + alternativeRoutes).associate { candidate ->
            candidate.id to (routeMetadata[candidate.id] ?: routeMetadata[candidate.path.pathKey()] ?: RouteMetadata())
        }
        val savedOrderExists = (listOf(primaryRoute) + alternativeRoutes).all { candidate ->
            normalizedMetadata[candidate.id]?.order != null
        }
        val orderedRoutes = if (savedOrderExists) {
            (listOf(primaryRoute) + alternativeRoutes).sortedWith(
                compareBy<OptimizedRoute> {
                    normalizedMetadata[it.id]?.order ?: Int.MAX_VALUE
                }.thenBy { legacyOrderedRoutes.indexOf(it) },
            )
        } else {
            legacyOrderedRoutes
        }
        val primaryRouteIndex = orderedRoutes.indexOf(primaryRoute).coerceAtLeast(0)
        val orderedAlternatives = orderedRoutes.filterNot { it === primaryRoute }
        _uiState.value = RoutingUiState(
            route = primaryRoute,
            alternativeRoutes = orderedAlternatives,
            nextLongestRouteCount = primaryRouteIndex,
            selectedRoutePointIds = selectedRoutePointIds,
            selectedRouteId = orderedRoutes.firstOrNull { it.id == selectedRouteId }?.id
                ?: orderedRoutes.firstOrNull {
                it.path.map(ControlPoint::id) == selectedRoutePointIds
            }?.id ?: primaryRoute.id,
            mode = mode,
            budgetMeters = budgetMeters,
            targetScore = targetScore,
            routeMetadata = normalizedMetadata,
        )
    }

    fun manageRoutes(action: RouteManagementAction) {
        routingJob?.cancel()
        val current = _uiState.value
        val routes = listOfNotNull(current.route) + current.alternativeRoutes
        when (action) {
            is RouteManagementAction.Apply -> {
                publishManagedRoutes(
                    action.orderedRoutes,
                    action.metadata
                        .filterKeys(action.orderedRoutes.mapTo(mutableSetOf()) { it.id }::contains)
                        .mapValues { (_, metadata) -> metadata.copy(name = metadata.name.take(60)) },
                )
            }
            is RouteManagementAction.Select -> {
                val selected = routes.firstOrNull { it.id == action.routeId }
                    ?.takeIf { current.routeMetadata[it.id]?.isHidden != true }
                    ?: return
                _uiState.value = current.copy(
                    selectedRoutePointIds = selected.path.map(ControlPoint::id),
                    selectedRouteId = selected.id,
                )
            }
        }
    }

    private fun publishManagedRoutes(
        routes: List<OptimizedRoute>,
        metadata: Map<String, RouteMetadata>,
    ) {
        val previous = _uiState.value
        val previousPrimaryId = previous.route?.id
        val primary = routes.firstOrNull { it.id == previousPrimaryId } ?: routes.firstOrNull()
        val primaryIndex = routes.indexOf(primary).coerceAtLeast(0)
        val alternatives = routes.filterNot { it === primary }
        val selectedKey = previous.selectedRoutePointIds.joinToString("|")
        val selected = routes.firstOrNull {
            it.id == previous.selectedRouteId && metadata[it.id]?.isHidden != true
        }
            ?: routes.firstOrNull { it.path.pathKey() == selectedKey } ?: primary
        val visibleSelected = selected?.takeIf { metadata[it.id]?.isHidden != true }
            ?: routes.firstOrNull { metadata[it.id]?.isHidden != true }
        _uiState.value = previous.copy(
            route = primary,
            alternativeRoutes = alternatives,
            nextLongestRouteCount = primaryIndex,
            selectedRoutePointIds = visibleSelected?.path?.map(ControlPoint::id).orEmpty(),
            selectedRouteId = visibleSelected?.id,
            routeMetadata = metadata,
        )
    }

    fun clearAlternativeRoutes() {
        routingJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isCalculating = false,
            isGeneratingAlternatives = false,
            alternativeRoutes = emptyList(),
            nextLongestRouteCount = 0,
            selectedRoutePointIds = _uiState.value.route?.path?.map(ControlPoint::id).orEmpty(),
            selectedRouteId = _uiState.value.route?.id,
            routeMetadata = _uiState.value.route?.id?.let { primaryId ->
                _uiState.value.routeMetadata.filterKeys { it == primaryId }
            }.orEmpty(),
            error = null,
        )
    }

    fun generateAlternativeRoutes(
        points: List<ControlPoint>,
        pixelsPerMeter: Float,
        criteria: AlternativeRouteCriteria,
    ) {
        val current = _uiState.value
        val routes = listOfNotNull(current.route) + current.alternativeRoutes
        val source = criteria.sourceRouteId?.let { id -> routes.firstOrNull { it.id == id } }
            ?: routes.firstOrNull { it.id == current.selectedRouteId }
            ?: routes.firstOrNull { it.path.map(ControlPoint::id) == current.selectedRoutePointIds }
            ?: current.route ?: return
        routingJob?.cancel()
        routingJob = viewModelScope.launch {
            _uiState.value = current.copy(isGeneratingAlternatives = true, error = null)
            runCatching {
                withContext(workerDispatcher) {
                    val matrix = DistanceMatrix(points.distinctBy(ControlPoint::id), pixelsPerMeter)
                    buildAlternativeRoutes(source, matrix, criteria)
                }
            }.onSuccess { alternatives ->
                val latest = _uiState.value
                val existingRoutes = listOfNotNull(latest.route) + latest.alternativeRoutes
                val existingKeys = existingRoutes.mapTo(mutableSetOf()) { it.path.pathKey() }
                val additions = alternatives.routes.filter { it.path.pathKey() !in existingKeys }
                val baseName = latest.routeMetadata[source.id]?.name?.ifBlank { null }
                    ?: defaultRouteName(existingRoutes.indexOf(source))
                var metadata = latest.routeMetadata
                additions.forEachIndexed { index, candidate ->
                    val difference = candidate.totalScore - source.totalScore
                    metadata = metadata + (candidate.id to RouteMetadata(
                        name = "$baseName ${if (difference >= 0) "+" else ""}$difference",
                        order = existingRoutes.size + index,
                        isDisplayed = false,
                        colorIndex = nextColorIndex(metadata),
                        isAlternative = true,
                        parentRouteId = source.id,
                    ))
                }
                val regularRoutes = existingRoutes.filterNot { latest.metadataFor(it).isAlternative }
                val generatedRoutes = existingRoutes.filter { latest.metadataFor(it).isAlternative } + additions
                val ordered = regularRoutes + generatedRoutes.sortedWith(
                    compareByDescending<OptimizedRoute>(OptimizedRoute::totalScore)
                        .thenBy(OptimizedRoute::totalDistanceMeters),
                )
                publishRoutes(
                    routes = ordered,
                    primary = latest.route ?: source,
                    selected = source,
                    metadata = metadata,
                    mode = latest.mode,
                    budgetMeters = latest.budgetMeters,
                    targetScore = latest.targetScore,
                    isGeneratingAlternatives = false,
                )
            }.onFailure { throwable ->
                if (throwable !is CancellationException) {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingAlternatives = false,
                        error = throwable.message ?: "Alternative route generation failed",
                    )
                }
            }
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
        return RouteCalculation(primary = primary)
    }

    /** Builds distinct shortest routes that satisfy the requested absolute or relative bounds. */
    private fun buildAlternativeRoutes(
        primary: OptimizedRoute,
        matrix: DistanceMatrix,
        criteria: AlternativeRouteCriteria,
    ): AlternativeRouteSet {
        require(criteria.count in 1..MAX_ALTERNATIVE_ROUTES) {
            "Alternative route count must be between 1 and $MAX_ALTERNATIVE_ROUTES"
        }
        val minDistance = criteria.minDistanceMeters.resolveMinimumRelativeTo(
            primary.totalDistanceMeters,
            criteria.useRelativeValues,
        )
        val maxDistance = criteria.maxDistanceMeters.resolveMaximumRelativeTo(
            primary.totalDistanceMeters,
            criteria.useRelativeValues,
        )
        val minScore = criteria.minScore.resolveMinimumRelativeTo(
            primary.totalScore,
            criteria.useRelativeValues,
        )
        val maxScore = criteria.maxScore.resolveMaximumRelativeTo(
            primary.totalScore,
            criteria.useRelativeValues,
        )
        if (criteria.useRelativeValues) {
            require(
                listOfNotNull(criteria.minDistanceMeters, criteria.maxDistanceMeters).all { it >= 0f } &&
                    listOfNotNull(criteria.minScore, criteria.maxScore).all { it >= 0 },
            ) { "Relative bounds must be non-negative" }
        }
        require(minDistance == null || maxDistance == null || minDistance <= maxDistance) {
            "Minimum distance cannot exceed maximum distance"
        }
        require(minScore == null || maxScore == null || minScore <= maxScore) {
            "Minimum score cannot exceed maximum score"
        }

        val start = primary.path.first()
        val finish = primary.path.last()
        val controls = matrix.points.filter {
            it.type == ControlPointType.CONTROL && it.points > 0
        }
        val availableScore = controls.sumOf(ControlPoint::points)
        val primaryPathKey = primary.path.pathKey()
        val candidates = (0..availableScore).asSequence()
            .map { target ->
                assembleRoute(
                    RoutingAlgorithms.shortestRouteForScore(matrix, start, finish, controls, target),
                    matrix,
                )
            }
            .filter { candidate -> candidate.path.pathKey() != primaryPathKey }
            .distinctBy { candidate -> candidate.path.pathKey() }
            .filter { candidate ->
                (minDistance == null || candidate.totalDistanceMeters + DISTANCE_EPSILON >= minDistance) &&
                    (maxDistance == null || candidate.totalDistanceMeters <= maxDistance + DISTANCE_EPSILON) &&
                    (minScore == null || candidate.totalScore >= minScore) &&
                    (maxScore == null || candidate.totalScore <= maxScore)
            }
            .sortedWith(
                compareByDescending<OptimizedRoute> { it.totalScore }
                    .thenBy { it.totalDistanceMeters },
            )
            .take(criteria.count)
            .toList()
        val higherScoreRoutes = candidates.filter { it.totalScore > primary.totalScore }
        val remainingRoutes = candidates.filter { it.totalScore <= primary.totalScore }
        return AlternativeRouteSet(
            routes = higherScoreRoutes + remainingRoutes,
            nextLongestCount = higherScoreRoutes.size,
        )
    }

    private fun List<ControlPoint>.pathKey(): String = joinToString("|") { it.id }

    private fun RoutingUiState.metadataFor(route: OptimizedRoute): RouteMetadata =
        routeMetadata[route.id] ?: routeMetadata[route.path.pathKey()] ?: RouteMetadata()

    private fun nextRouteName(existingNames: List<String>): String {
        val used = existingNames.filter(String::isNotBlank).toSet()
        if ("Route" !in used) return "Route"
        var number = 2
        while ("Route ($number)" in used) number++
        return "Route ($number)"
    }

    private fun defaultRouteName(index: Int): String = if (index <= 0) "Route" else "Route (${index + 1})"

    private fun nextColorIndex(metadata: Map<String, RouteMetadata>): Int =
        ((metadata.values.maxOfOrNull(RouteMetadata::colorIndex) ?: -1) + 1) % ROUTE_COLOR_COUNT

    private fun publishRoutes(
        routes: List<OptimizedRoute>,
        primary: OptimizedRoute,
        selected: OptimizedRoute,
        metadata: Map<String, RouteMetadata>,
        mode: RouteMode,
        budgetMeters: Float?,
        targetScore: Int?,
        isGeneratingAlternatives: Boolean = false,
    ) {
        val orderedMetadata = metadata.mapValues { (id, value) ->
            value.copy(order = routes.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: value.order)
        }
        _uiState.value = _uiState.value.copy(
            mode = mode,
            isCalculating = false,
            isGeneratingAlternatives = isGeneratingAlternatives,
            route = primary,
            alternativeRoutes = routes.filterNot { it.id == primary.id },
            nextLongestRouteCount = routes.indexOfFirst { it.id == primary.id }.coerceAtLeast(0),
            selectedRoutePointIds = selected.path.map(ControlPoint::id),
            selectedRouteId = selected.id,
            budgetMeters = budgetMeters,
            targetScore = targetScore,
            routeMetadata = orderedMetadata.filterKeys(routes.mapTo(mutableSetOf()) { it.id }::contains),
            error = null,
        )
    }

    private fun Float?.resolveMinimumRelativeTo(base: Float, relative: Boolean): Float? =
        this?.let { if (relative) base - it else it }

    private fun Float?.resolveMaximumRelativeTo(base: Float, relative: Boolean): Float? =
        this?.let { if (relative) base + it else it }

    private fun Int?.resolveMinimumRelativeTo(base: Int, relative: Boolean): Int? =
        this?.let { if (relative) base - it else it }

    private fun Int?.resolveMaximumRelativeTo(base: Int, relative: Boolean): Int? =
        this?.let { if (relative) base + it else it }

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
    )

    private data class AlternativeRouteSet(
        val routes: List<OptimizedRoute>,
        val nextLongestCount: Int,
    )

    private companion object {
        const val MAX_ALTERNATIVE_ROUTES = 20
        const val DISTANCE_EPSILON = 0.0001f
        const val ROUTE_COLOR_COUNT = 10
    }
}
