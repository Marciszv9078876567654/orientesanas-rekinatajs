package com.orientesanasrekinatajs.domain.routing

import com.orientesanasrekinatajs.domain.model.ControlPoint

/** Deterministic routing heuristics for the application's two route modes. */
object RoutingAlgorithms {
    private const val DISTANCE_EPSILON = 0.0001f
    private const val MAX_TWO_OPT_PASSES = 64

    /** Visits every supplied control using nearest neighbor, with [finish] fixed at the end. */
    fun nearestNeighbor(
        distanceMatrix: DistanceMatrix,
        start: ControlPoint,
        finish: ControlPoint,
        controlPoints: List<ControlPoint>,
    ): List<ControlPoint> {
        validateInputs(distanceMatrix, start, finish, controlPoints)
        val remaining = controlPoints
            .filterNot { it.id == start.id || it.id == finish.id }
            .distinctBy(ControlPoint::id)
            .toMutableList()
        val route = mutableListOf(start)

        while (remaining.isNotEmpty()) {
            val current = route.last()
            val next = remaining.minWith(
                compareBy<ControlPoint> { distanceMatrix[current, it] }
                    .thenBy(ControlPoint::code)
                    .thenBy(ControlPoint::id),
            )
            route += next
            remaining.remove(next)
        }

        route += finish
        return route
    }

    /**
     * Repeatedly applies the best improving 2-opt swap while preserving the first and last point.
     */
    fun twoOpt(
        path: List<ControlPoint>,
        distanceMatrix: DistanceMatrix,
    ): List<ControlPoint> {
        require(path.all(distanceMatrix::contains)) { "Every path point must be in the matrix" }
        if (path.size < 4) return path.toList()

        val optimized = path.toMutableList()
        repeat(MAX_TWO_OPT_PASSES) {
            var bestStart = -1
            var bestEnd = -1
            var bestDelta = -DISTANCE_EPSILON
            val lastIndex = optimized.lastIndex

            for (segmentStart in 1 until lastIndex - 1) {
                for (segmentEnd in segmentStart + 1 until lastIndex) {
                    val beforeStart = optimized[segmentStart - 1]
                    val start = optimized[segmentStart]
                    val end = optimized[segmentEnd]
                    val afterEnd = optimized[segmentEnd + 1]
                    val delta = distanceMatrix[beforeStart, end] +
                        distanceMatrix[start, afterEnd] -
                        distanceMatrix[beforeStart, start] -
                        distanceMatrix[end, afterEnd]

                    if (delta < bestDelta) {
                        bestDelta = delta
                        bestStart = segmentStart
                        bestEnd = segmentEnd
                    }
                }
            }

            if (bestStart < 0) return optimized
            optimized.subList(bestStart, bestEnd + 1).reverse()
        }

        return optimized
    }

    /** Runs nearest neighbor followed by fixed-endpoint 2-opt. */
    fun shortestRoute(
        distanceMatrix: DistanceMatrix,
        start: ControlPoint,
        finish: ControlPoint,
        controlPoints: List<ControlPoint>,
    ): List<ControlPoint> = twoOpt(
        nearestNeighbor(distanceMatrix, start, finish, controlPoints),
        distanceMatrix,
    )

    /**
     * Greedily maximizes score-to-added-distance while keeping total distance within [budgetMeters].
     *
     * Returns an empty list when the direct start-to-finish leg itself is not feasible.
     */
    fun bestScoreRoute(
        distanceMatrix: DistanceMatrix,
        start: ControlPoint,
        finish: ControlPoint,
        controlPoints: List<ControlPoint>,
        budgetMeters: Float,
    ): List<ControlPoint> {
        validateInputs(distanceMatrix, start, finish, controlPoints)
        require(budgetMeters.isFinite() && budgetMeters >= 0f) {
            "budgetMeters must be finite and non-negative"
        }

        var route = mutableListOf(start, finish)
        var currentDistance = totalDistance(route, distanceMatrix)
        if (currentDistance > budgetMeters + DISTANCE_EPSILON) return emptyList()

        val remaining = controlPoints
            .filter { point ->
                point.id != start.id && point.id != finish.id && point.points > 0
            }
            .distinctBy(ControlPoint::id)
            .toMutableList()

        while (remaining.isNotEmpty()) {
            var bestInsertion: Insertion? = null
            remaining.forEach { point ->
                for (edgeIndex in 0 until route.lastIndex) {
                    val from = route[edgeIndex]
                    val to = route[edgeIndex + 1]
                    val addedDistance = (
                        distanceMatrix[from, point] +
                            distanceMatrix[point, to] -
                            distanceMatrix[from, to]
                        ).coerceAtLeast(0f)
                    if (currentDistance + addedDistance <= budgetMeters + DISTANCE_EPSILON) {
                        val ratio = if (addedDistance <= DISTANCE_EPSILON) {
                            Float.POSITIVE_INFINITY
                        } else {
                            point.points / addedDistance
                        }
                        val insertion = Insertion(point, edgeIndex, addedDistance, ratio)
                        if (bestInsertion == null || insertion.isBetterThan(bestInsertion!!)) {
                            bestInsertion = insertion
                        }
                    }
                }
            }

            val selected = bestInsertion ?: break
            route.add(selected.edgeIndex + 1, selected.point)
            remaining.removeAll { it.id == selected.point.id }
            currentDistance += selected.addedDistance
        }

        return twoOpt(route, distanceMatrix)
    }

    /**
     * Finds a short route whose collected control score is at least [targetScore].
     * Controls are inserted by added-distance cost per useful score point, then unnecessary
     * controls are pruned while the requested score remains satisfied.
     */
    fun shortestRouteForScore(
        distanceMatrix: DistanceMatrix,
        start: ControlPoint,
        finish: ControlPoint,
        controlPoints: List<ControlPoint>,
        targetScore: Int,
    ): List<ControlPoint> {
        validateInputs(distanceMatrix, start, finish, controlPoints)
        require(targetScore >= 0) { "targetScore must be non-negative" }
        val remaining = controlPoints
            .filter { it.id != start.id && it.id != finish.id && it.points > 0 }
            .distinctBy(ControlPoint::id)
            .toMutableList()
        require(remaining.sumOf(ControlPoint::points) >= targetScore) {
            "The requested score is higher than the available control score"
        }
        var route = mutableListOf(start, finish)
        var collectedScore = 0

        while (collectedScore < targetScore) {
            val neededScore = targetScore - collectedScore
            var best: TargetInsertion? = null
            remaining.forEach { point ->
                for (edgeIndex in 0 until route.lastIndex) {
                    val from = route[edgeIndex]
                    val to = route[edgeIndex + 1]
                    val addedDistance = (
                        distanceMatrix[from, point] + distanceMatrix[point, to] - distanceMatrix[from, to]
                        ).coerceAtLeast(0f)
                    val usefulScore = minOf(point.points, neededScore)
                    val costPerPoint = if (addedDistance <= DISTANCE_EPSILON) {
                        0f
                    } else {
                        addedDistance / usefulScore
                    }
                    val insertion = TargetInsertion(
                        point = point,
                        edgeIndex = edgeIndex,
                        addedDistance = addedDistance,
                        costPerPoint = costPerPoint,
                        overshoot = (point.points - neededScore).coerceAtLeast(0),
                    )
                    if (best == null || insertion.isBetterThan(best!!)) best = insertion
                }
            }
            val selected = requireNotNull(best) { "The requested score cannot be reached" }
            route.add(selected.edgeIndex + 1, selected.point)
            remaining.removeAll { it.id == selected.point.id }
            collectedScore += selected.point.points
        }

        while (true) {
            val removable = route
                .withIndex()
                .filter { (_, point) ->
                    point.type == com.orientesanasrekinatajs.domain.model.ControlPointType.CONTROL &&
                        collectedScore - point.points >= targetScore
                }
                .map { (index, point) ->
                    val distanceSaving = distanceMatrix[route[index - 1], point] +
                        distanceMatrix[point, route[index + 1]] -
                        distanceMatrix[route[index - 1], route[index + 1]]
                    Triple(index, point, distanceSaving)
                }
                .maxWithOrNull(
                    compareBy<Triple<Int, ControlPoint, Float>> { it.third }
                        .thenBy { it.second.points }
                        .thenByDescending { it.second.code },
                )
                ?: break
            if (removable.third <= DISTANCE_EPSILON) break
            collectedScore -= removable.second.points
            route.removeAt(removable.first)
        }
        return twoOpt(route, distanceMatrix)
    }

    fun totalDistance(
        path: List<ControlPoint>,
        distanceMatrix: DistanceMatrix,
    ): Float {
        require(path.all(distanceMatrix::contains)) { "Every path point must be in the matrix" }
        return path.zipWithNext().sumOf { (from, to) ->
            distanceMatrix[from, to].toDouble()
        }.toFloat()
    }

    private fun validateInputs(
        distanceMatrix: DistanceMatrix,
        start: ControlPoint,
        finish: ControlPoint,
        controlPoints: List<ControlPoint>,
    ) {
        require(distanceMatrix.contains(start)) { "Start point must be in the matrix" }
        require(distanceMatrix.contains(finish)) { "Finish point must be in the matrix" }
        require(controlPoints.all(distanceMatrix::contains)) {
            "Every control point must be in the matrix"
        }
    }

    private data class Insertion(
        val point: ControlPoint,
        val edgeIndex: Int,
        val addedDistance: Float,
        val scorePerMeter: Float,
    ) {
        fun isBetterThan(other: Insertion): Boolean = when {
            scorePerMeter > other.scorePerMeter -> true
            scorePerMeter < other.scorePerMeter -> false
            point.points > other.point.points -> true
            point.points < other.point.points -> false
            addedDistance < other.addedDistance - DISTANCE_EPSILON -> true
            addedDistance > other.addedDistance + DISTANCE_EPSILON -> false
            point.code < other.point.code -> true
            point.code > other.point.code -> false
            point.id < other.point.id -> true
            point.id > other.point.id -> false
            else -> edgeIndex < other.edgeIndex
        }
    }

    private data class TargetInsertion(
        val point: ControlPoint,
        val edgeIndex: Int,
        val addedDistance: Float,
        val costPerPoint: Float,
        val overshoot: Int,
    ) {
        fun isBetterThan(other: TargetInsertion): Boolean = when {
            costPerPoint < other.costPerPoint - DISTANCE_EPSILON -> true
            costPerPoint > other.costPerPoint + DISTANCE_EPSILON -> false
            addedDistance < other.addedDistance - DISTANCE_EPSILON -> true
            addedDistance > other.addedDistance + DISTANCE_EPSILON -> false
            overshoot < other.overshoot -> true
            overshoot > other.overshoot -> false
            point.code < other.point.code -> true
            point.code > other.point.code -> false
            else -> edgeIndex < other.edgeIndex
        }
    }
}
