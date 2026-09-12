package com.orientesanasrekinatajs.ui.routing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.RouteSegment
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.domain.model.RouteRestriction
import com.orientesanasrekinatajs.domain.model.RouteRestrictionType
import com.orientesanasrekinatajs.domain.model.mandatoryConnectionLimit
import com.orientesanasrekinatajs.domain.model.mandatoryConnectionCounts
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

    data class UpdatePath(
        val routeId: String,
        val path: List<ControlPoint>,
        val pixelsPerMeter: Float,
    ) : RouteManagementAction

    data class CreateEmpty(
        val points: List<ControlPoint>,
        val pixelsPerMeter: Float,
    ) : RouteManagementAction
}

sealed interface RouteRestrictionAction {
    data class Add(val restriction: RouteRestriction) : RouteRestrictionAction
    data class ToggleStar(val restrictionId: String) : RouteRestrictionAction
    data class Delete(val restrictionId: String) : RouteRestrictionAction
    data object DeleteAllUnstarred : RouteRestrictionAction
}

data class AlternativeRouteCriteria(
    val count: Int,
    val sourceRouteId: String? = null,
    val minDistanceMeters: Float? = null,
    val maxDistanceMeters: Float? = null,
    val minScore: Int? = null,
    val maxScore: Int? = null,
    val useRelativeValues: Boolean = false,
    val useRelativeDistanceValues: Boolean = false,
    val useRelativeScoreValues: Boolean = false,
    val fixedPrefixPointCount: Int = 1,
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
    val routeRestrictions: List<RouteRestriction> = emptyList(),
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
                    calculate(
                        points,
                        pixelsPerMeter,
                        mode,
                        budgetMeters,
                        targetScore,
                        previous.routeRestrictions,
                    )
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
        routeRestrictions: List<RouteRestriction> = emptyList(),
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
            routeRestrictions = routeRestrictions,
        )
    }

    fun openSavedRestrictions(restrictions: List<RouteRestriction>) {
        _uiState.value = _uiState.value.copy(routeRestrictions = restrictions)
    }

    fun manageRouteRestrictions(action: RouteRestrictionAction) {
        routingJob?.cancel()
        val current = _uiState.value
        val updated = when (action) {
            is RouteRestrictionAction.Add -> {
                if (current.routeRestrictions.any { it.sameRuleAs(action.restriction) }) {
                    current.routeRestrictions
                } else current.routeRestrictions + action.restriction
            }
            is RouteRestrictionAction.ToggleStar -> current.routeRestrictions.map { restriction ->
                if (restriction.id == action.restrictionId) {
                    restriction.copy(isStarred = !restriction.isStarred)
                } else restriction
            }
            is RouteRestrictionAction.Delete -> current.routeRestrictions.filterNot { restriction ->
                restriction.id == action.restrictionId && !restriction.isStarred
            }
            RouteRestrictionAction.DeleteAllUnstarred ->
                current.routeRestrictions.filter(RouteRestriction::isStarred)
        }
        _uiState.value = current.copy(
            routeRestrictions = updated,
            isCalculating = false,
            isGeneratingAlternatives = false,
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
            is RouteManagementAction.UpdatePath -> {
                val existing = routes.firstOrNull { it.id == action.routeId } ?: return
                if (action.path.size < 2 || action.pixelsPerMeter <= 0f ||
                    !action.pixelsPerMeter.isFinite()
                ) return
                val matrix = DistanceMatrix(action.path.distinctBy(ControlPoint::id), action.pixelsPerMeter)
                val updated = assembleRoute(action.path, matrix).copy(id = existing.id)
                publishManagedRoutes(
                    routes = routes.map { candidate ->
                        if (candidate.id == existing.id) updated else candidate
                    },
                    metadata = current.routeMetadata,
                )
            }
            is RouteManagementAction.CreateEmpty -> {
                if (!action.pixelsPerMeter.isFinite() || action.pixelsPerMeter <= 0f) return
                val combinedEndpoints = action.points.filter {
                    it.type == ControlPointType.START_FINISH
                }
                val start = action.points.filter { it.type == ControlPointType.START }
                    .ifEmpty { combinedEndpoints }
                    .singleOrNull() ?: return
                val finish = action.points.filter { it.type == ControlPointType.FINISH }
                    .ifEmpty { combinedEndpoints }
                    .singleOrNull() ?: return
                val endpointPath = listOf(start, finish)
                val emptyRoute = assembleRoute(
                    path = endpointPath,
                    matrix = DistanceMatrix(endpointPath.distinctBy(ControlPoint::id), action.pixelsPerMeter),
                )
                val orderedRoutes = routes + emptyRoute
                val name = nextRouteName(routes.mapIndexed { index, existingRoute ->
                    current.routeMetadata[existingRoute.id]?.name?.takeIf(String::isNotBlank)
                        ?: defaultRouteName(index)
                })
                val metadata = current.routeMetadata + (emptyRoute.id to RouteMetadata(
                    name = name,
                    order = orderedRoutes.lastIndex,
                    isDisplayed = false,
                    colorIndex = nextColorIndex(current.routeMetadata),
                ))
                publishRoutes(
                    routes = orderedRoutes,
                    primary = current.route ?: emptyRoute,
                    selected = emptyRoute,
                    metadata = metadata,
                    mode = current.mode,
                    budgetMeters = current.budgetMeters,
                    targetScore = current.targetScore,
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
                    buildAlternativeRoutes(source, matrix, criteria, current.routeRestrictions)
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
        restrictions: List<RouteRestriction>,
    ): RouteCalculation {
        require(pixelsPerMeter.isFinite() && pixelsPerMeter > 0f) {
            "Map scale must be a positive number of pixels per meter"
        }
        val combinedEndpoints = points.filter { it.type == ControlPointType.START_FINISH }
        val explicitStarts = points.filter { it.type == ControlPointType.START }
        val explicitFinishes = points.filter { it.type == ControlPointType.FINISH }
        val start = uniqueEndpoint(explicitStarts.ifEmpty { combinedEndpoints }, "start")
        val finish = uniqueEndpoint(explicitFinishes.ifEmpty { combinedEndpoints }, "finish")
        validateRestrictions(points, restrictions)
        val blacklistedControlIds = restrictions
            .filter { it.type == RouteRestrictionType.BLACKLIST_CONTROL }
            .mapTo(mutableSetOf(), RouteRestriction::firstPointId)
        val controls = points.filter {
            it.type == ControlPointType.CONTROL && it.id !in blacklistedControlIds
        }
        val matrixPoints = (listOf(start, finish) + controls).distinctBy(ControlPoint::id)
        val matrix = DistanceMatrix(matrixPoints, pixelsPerMeter)

        val unrestrictedPath = when (mode) {
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
        val path = applyRestrictions(unrestrictedPath, matrix, restrictions)
        val primary = assembleRoute(path, matrix)
        if (mode == RouteMode.BEST_SCORE) {
            require(primary.totalDistanceMeters <= requireNotNull(budgetMeters) + DISTANCE_EPSILON) {
                "Mandatory route restrictions exceed the distance budget"
            }
        }
        if (mode == RouteMode.TARGET_SCORE) {
            require(primary.totalScore >= requireNotNull(targetScore)) {
                "Route restrictions prevent reaching the requested score"
            }
        }
        return RouteCalculation(primary = primary)
    }

    /** Builds distinct shortest routes that satisfy the requested absolute or relative bounds. */
    private fun buildAlternativeRoutes(
        primary: OptimizedRoute,
        matrix: DistanceMatrix,
        criteria: AlternativeRouteCriteria,
        restrictions: List<RouteRestriction>,
    ): AlternativeRouteSet {
        require(criteria.count in 1..MAX_ALTERNATIVE_ROUTES) {
            "Alternative route count must be between 1 and $MAX_ALTERNATIVE_ROUTES"
        }
        val relativeDistanceValues = criteria.useRelativeValues || criteria.useRelativeDistanceValues
        val relativeScoreValues = criteria.useRelativeValues || criteria.useRelativeScoreValues
        val minDistance = criteria.minDistanceMeters.resolveMinimumRelativeTo(
            primary.totalDistanceMeters,
            relativeDistanceValues,
        )
        val maxDistance = criteria.maxDistanceMeters.resolveMaximumRelativeTo(
            primary.totalDistanceMeters,
            relativeDistanceValues,
        )
        val minScore = criteria.minScore.resolveMinimumRelativeTo(
            primary.totalScore,
            relativeScoreValues,
        )
        val maxScore = criteria.maxScore.resolveMaximumRelativeTo(
            primary.totalScore,
            relativeScoreValues,
        )
        if (relativeDistanceValues) require(
            listOfNotNull(criteria.minDistanceMeters, criteria.maxDistanceMeters).all { it >= 0f },
        ) { "Relative distance bounds must be non-negative" }
        if (relativeScoreValues) require(
            listOfNotNull(criteria.minScore, criteria.maxScore).all { it >= 0 },
        ) { "Relative score bounds must be non-negative" }
        require(minDistance == null || maxDistance == null || minDistance <= maxDistance) {
            "Minimum distance cannot exceed maximum distance"
        }
        require(minScore == null || maxScore == null || minScore <= maxScore) {
            "Minimum score cannot exceed maximum score"
        }

        validateRestrictions(matrix.points, restrictions)
        val fixedPrefixCount = criteria.fixedPrefixPointCount.coerceIn(1, primary.path.lastIndex)
        val fixedPrefix = primary.path.take(fixedPrefixCount)
        val start = fixedPrefix.last()
        val finish = primary.path.last()
        val fixedIds = fixedPrefix.mapTo(mutableSetOf(), ControlPoint::id)
        val blacklistedControlIds = restrictions
            .filter { it.type == RouteRestrictionType.BLACKLIST_CONTROL }
            .mapTo(mutableSetOf(), RouteRestriction::firstPointId)
        val controls = matrix.points.filter {
            it.type == ControlPointType.CONTROL && it.points > 0 &&
                it.id !in fixedIds && it.id !in blacklistedControlIds
        }
        val availableScore = controls.sumOf(ControlPoint::points)
        val primaryPathKey = primary.path.pathKey()
        val candidates = alternativeScoreTargets(availableScore).asSequence()
            .mapNotNull { target ->
                runCatching {
                    val suffix = RoutingAlgorithms.shortestRouteForScore(
                        matrix, start, finish, controls, target,
                    )
                    val combined = fixedPrefix.dropLast(1) + suffix
                    val restricted = applyRestrictions(
                        combined,
                        matrix,
                        restrictions,
                        lockedPrefixPointCount = fixedPrefixCount,
                    )
                    restricted.takeIf { candidate ->
                        candidate.take(fixedPrefixCount).pathKey() == fixedPrefix.pathKey()
                    }?.let { assembleRoute(it, matrix) }
                }.getOrNull()
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

    private fun applyRestrictions(
        originalPath: List<ControlPoint>,
        matrix: DistanceMatrix,
        restrictions: List<RouteRestriction>,
        lockedPrefixPointCount: Int = 1,
    ): List<ControlPoint> {
        if (restrictions.isEmpty()) return originalPath
        val pointsById = matrix.points.associateBy(ControlPoint::id)
        val lockedCount = lockedPrefixPointCount.coerceIn(1, originalPath.size)
        val blacklistedControls = restrictions
            .filter { it.type == RouteRestrictionType.BLACKLIST_CONTROL }
            .mapTo(mutableSetOf(), RouteRestriction::firstPointId)
        require(originalPath.take(lockedCount).none { it.id in blacklistedControls }) {
            "A fixed part of the route contains a blacklisted control"
        }
        var path = originalPath.filterIndexed { index, point ->
            index < lockedCount || point.id !in blacklistedControls
        }.toMutableList()
        val mandatoryConnections = restrictions
            .filter { it.type == RouteRestrictionType.MANDATORY_CONNECTION }
            .map { it.connectionKey() }
        val blacklistedConnections = restrictions
            .filter { it.type == RouteRestrictionType.BLACKLIST_CONNECTION }
            .mapTo(mutableSetOf()) { it.connectionKey() }
        val mandatoryPointIds = buildSet {
            restrictions.filter { it.type == RouteRestrictionType.MANDATORY_CONTROL }
                .forEach { add(it.firstPointId) }
            mandatoryConnections.forEach { (first, second) -> add(first); add(second) }
        }
        mandatoryPointIds.forEach { pointId ->
            val point = pointsById[pointId] ?: return@forEach
            if (point.type == ControlPointType.CONTROL && path.none { it.id == pointId }) {
                path = insertAtCheapestAllowedEdge(
                    path, point, matrix, lockedCount, blacklistedConnections,
                ).toMutableList()
            }
        }

        val enforcedConnections = mutableSetOf<Pair<String, String>>()
        mandatoryConnections.forEach { connection ->
            path = enforceMandatoryConnection(
                path,
                connection,
                matrix,
                lockedCount,
                blacklistedConnections,
                enforcedConnections,
            ).toMutableList()
            enforcedConnections += connection
        }

        var repairPass = 0
        while (repairPass++ < MAX_RESTRICTION_REPAIR_PASSES) {
            val violations = path.zipWithNext().count { (from, to) ->
                from.id.connectionKey(to.id) in blacklistedConnections
            }
            if (violations == 0 && path.satisfiesMandatoryConnections(mandatoryConnections)) {
                return path
            }
            val candidates = buildList {
                path.indices
                    .filter { index -> index >= lockedCount && index < path.lastIndex }
                    .filter { index -> path[index].type == ControlPointType.CONTROL }
                    .forEach { movingIndex ->
                        val moving = path[movingIndex]
                        val without = path.toMutableList().also { it.removeAt(movingIndex) }
                        for (insertionIndex in lockedCount..without.lastIndex) {
                            val candidate = without.toMutableList().also {
                                it.add(insertionIndex, moving)
                            }
                            val candidateViolations = candidate.zipWithNext().count { (from, to) ->
                                from.id.connectionKey(to.id) in blacklistedConnections
                            }
                            if (
                                candidateViolations < violations &&
                                candidate.satisfiesMandatoryConnections(mandatoryConnections)
                            ) add(candidate)
                        }
                    }
            }
            val repaired = candidates.minByOrNull { routeDistance(it, matrix) } ?: break
            path = repaired.toMutableList()
        }
        require(path.zipWithNext().none { (from, to) ->
            from.id.connectionKey(to.id) in blacklistedConnections
        }) { "No route satisfies the blacklisted connections" }
        require(path.satisfiesMandatoryConnections(mandatoryConnections)) {
            "No route satisfies the mandatory connections"
        }
        return path
    }

    private fun insertAtCheapestAllowedEdge(
        path: List<ControlPoint>,
        point: ControlPoint,
        matrix: DistanceMatrix,
        lockedPrefixPointCount: Int,
        blacklistedConnections: Set<Pair<String, String>>,
    ): List<ControlPoint> = (lockedPrefixPointCount..path.lastIndex)
        .map { insertionIndex -> path.toMutableList().also { it.add(insertionIndex, point) } }
        .filter { candidate -> candidate.zipWithNext().none { (from, to) ->
            (from.id == point.id || to.id == point.id) &&
                from.id.connectionKey(to.id) in blacklistedConnections
        } }
        .minByOrNull { routeDistance(it, matrix) }
        ?: throw IllegalArgumentException("No route can include a mandatory control")

    private fun enforceMandatoryConnection(
        path: List<ControlPoint>,
        connection: Pair<String, String>,
        matrix: DistanceMatrix,
        lockedPrefixPointCount: Int,
        blacklistedConnections: Set<Pair<String, String>>,
        alreadyEnforced: Set<Pair<String, String>>,
    ): List<ControlPoint> {
        if (path.hasConnection(connection)) return path
        val existingBlacklistViolations = path.zipWithNext().count { (from, to) ->
            from.id.connectionKey(to.id) in blacklistedConnections
        }
        val candidates = buildList {
            listOf(connection.first to connection.second, connection.second to connection.first)
                .forEach { (movingId, anchorId) ->
                    val movingIndex = path.indexOfFirst { it.id == movingId }
                    if (movingIndex < lockedPrefixPointCount || movingIndex >= path.lastIndex ||
                        path.getOrNull(movingIndex)?.type != ControlPointType.CONTROL
                    ) return@forEach
                    val moving = path[movingIndex]
                    val without = path.toMutableList().also { it.removeAt(movingIndex) }
                    val anchorIndex = without.indexOfFirst { it.id == anchorId }
                    listOf(anchorIndex, anchorIndex + 1)
                        .filter { it in lockedPrefixPointCount..without.lastIndex }
                        .forEach { insertionIndex ->
                            val candidate = without.toMutableList().also { it.add(insertionIndex, moving) }
                            if (
                                candidate.hasConnection(connection) &&
                                candidate.satisfiesMandatoryConnections(alreadyEnforced) &&
                                candidate.zipWithNext().count { (from, to) ->
                                    from.id.connectionKey(to.id) in blacklistedConnections
                                } <= existingBlacklistViolations
                            ) add(candidate)
                        }
                }
        }
        return candidates.minByOrNull { routeDistance(it, matrix) }
            ?: throw IllegalArgumentException("No route satisfies a mandatory connection")
    }

    private fun validateRestrictions(
        points: List<ControlPoint>,
        restrictions: List<RouteRestriction>,
    ) {
        val pointsById = points.associateBy(ControlPoint::id)
        restrictions.forEach { restriction ->
            require(restriction.firstPointId in pointsById) { "A restricted point no longer exists" }
            if (restriction.type in CONNECTION_RESTRICTION_TYPES) {
                val secondId = requireNotNull(restriction.secondPointId) {
                    "A connection restriction needs two points"
                }
                require(secondId in pointsById && secondId != restriction.firstPointId) {
                    "A connection restriction needs two different existing points"
                }
            } else {
                require(pointsById[restriction.firstPointId]?.type == ControlPointType.CONTROL) {
                    "Control restrictions can only target control points"
                }
            }
        }
        val blacklistedControls = restrictions
            .filter { it.type == RouteRestrictionType.BLACKLIST_CONTROL }
            .mapTo(mutableSetOf(), RouteRestriction::firstPointId)
        val mandatoryPoints = restrictions
            .filter { it.type == RouteRestrictionType.MANDATORY_CONTROL }
            .mapTo(mutableSetOf(), RouteRestriction::firstPointId)
        restrictions.filter { it.type == RouteRestrictionType.MANDATORY_CONNECTION }
            .forEach { mandatoryPoints += it.pointIds }
        require(blacklistedControls.intersect(mandatoryPoints).isEmpty()) {
            "A point cannot be both blacklisted and mandatory"
        }
        val blacklistedConnections = restrictions
            .filter { it.type == RouteRestrictionType.BLACKLIST_CONNECTION }
            .mapTo(mutableSetOf()) { it.connectionKey() }
        val mandatoryConnections = restrictions
            .filter { it.type == RouteRestrictionType.MANDATORY_CONNECTION }
            .mapTo(mutableSetOf()) { it.connectionKey() }
        require(blacklistedConnections.intersect(mandatoryConnections).isEmpty()) {
            "A connection cannot be both blacklisted and mandatory"
        }
        mandatoryConnectionCounts(restrictions).forEach { (id, count) ->
            val point = pointsById.getValue(id)
            require(count <= point.mandatoryConnectionLimit) {
                val label = when (point.type) {
                    ControlPointType.CONTROL -> "Control ${point.code}"
                    ControlPointType.START -> "The start"
                    ControlPointType.FINISH -> "The finish"
                    ControlPointType.START_FINISH -> "The combined start/finish"
                }
                val connection = if (point.mandatoryConnectionLimit == 1) "connection" else "connections"
                "$label can have at most ${point.mandatoryConnectionLimit} mandatory $connection"
            }
        }
    }

    private fun alternativeScoreTargets(availableScore: Int): List<Int> =
        if (availableScore <= MAX_ALTERNATIVE_TARGET_ATTEMPTS) {
            (0..availableScore).toList()
        } else {
            (0..MAX_ALTERNATIVE_TARGET_ATTEMPTS).map { attempt ->
                (availableScore.toLong() * attempt / MAX_ALTERNATIVE_TARGET_ATTEMPTS).toInt()
            }.distinct()
        }

    private fun routeDistance(path: List<ControlPoint>, matrix: DistanceMatrix): Float =
        path.zipWithNext().sumOf { (from, to) -> matrix[from, to].toDouble() }.toFloat()

    private fun List<ControlPoint>.hasConnection(connection: Pair<String, String>): Boolean =
        zipWithNext().any { (from, to) -> from.id.connectionKey(to.id) == connection }

    private fun List<ControlPoint>.satisfiesMandatoryConnections(
        mandatory: Collection<Pair<String, String>>,
    ): Boolean = mandatory.all { connection -> hasConnection(connection) }

    private fun RouteRestriction.connectionKey(): Pair<String, String> =
        firstPointId.connectionKey(requireNotNull(secondPointId))

    private fun String.connectionKey(other: String): Pair<String, String> =
        if (this <= other) this to other else other to this

    private fun RouteRestriction.sameRuleAs(other: RouteRestriction): Boolean =
        type == other.type && when (type) {
            RouteRestrictionType.BLACKLIST_CONNECTION,
            RouteRestrictionType.MANDATORY_CONNECTION -> connectionKey() == other.connectionKey()
            else -> firstPointId == other.firstPointId
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
        com.orientesanasrekinatajs.domain.model.nextRouteColorIndex(metadata.values)

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
        const val MAX_ALTERNATIVE_TARGET_ATTEMPTS = 500
        const val MAX_RESTRICTION_REPAIR_PASSES = 64
        const val DISTANCE_EPSILON = 0.0001f
        val CONNECTION_RESTRICTION_TYPES = setOf(
            RouteRestrictionType.BLACKLIST_CONNECTION,
            RouteRestrictionType.MANDATORY_CONNECTION,
        )
    }
}
