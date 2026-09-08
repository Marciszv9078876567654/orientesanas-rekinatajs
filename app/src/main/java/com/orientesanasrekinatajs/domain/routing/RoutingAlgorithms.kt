package com.orientesanasrekinatajs.domain.routing

import com.orientesanasrekinatajs.domain.model.ControlPoint

/** Exact search for small problems, with deterministic heuristics for larger ones. */
object RoutingAlgorithms {
    private const val DISTANCE_EPSILON = 0.0001f
    private const val MAX_TWO_OPT_PASSES = 64
    private const val MAX_EXACT_CONTROLS = 15
    private const val MAX_ROUTE_SEEDS = 8

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

    /** Finds the optimum up to 15 controls; otherwise compares several 2-opt starting routes. */
    fun shortestRoute(
        distanceMatrix: DistanceMatrix,
        start: ControlPoint,
        finish: ControlPoint,
        controlPoints: List<ControlPoint>,
    ): List<ControlPoint> {
        validateInputs(distanceMatrix, start, finish, controlPoints)
        val controls = eligibleControls(start, finish, controlPoints)
        if (controls.size <= MAX_EXACT_CONTROLS) {
            return exactRoute(distanceMatrix, start, finish, controls)
        }
        var best = twoOpt(nearestNeighbor(distanceMatrix, start, finish, controls), distanceMatrix)
        var bestDistance = totalDistance(best, distanceMatrix)
        val seeds = controls.sortedWith(compareBy<ControlPoint> { distanceMatrix[start, it] }
            .thenBy(ControlPoint::code).thenBy(ControlPoint::id))
        // Spread a bounded number of seeds across nearby and distant first controls.
        repeat(MAX_ROUTE_SEEDS) { index ->
            val seed = seeds[index * seeds.lastIndex / (MAX_ROUTE_SEEDS - 1)]
            val candidate = twoOpt(
                listOf(start) + nearestNeighbor(distanceMatrix, seed, finish, controls),
                distanceMatrix,
            )
            val distance = totalDistance(candidate, distanceMatrix)
            if (distance < bestDistance) {
                best = candidate
                bestDistance = distance
            }
        }
        return best
    }

    /**
     * Maximizes score within [budgetMeters], exactly up to 15 positive-score controls.
     * Larger problems use score-to-added-distance insertion with route reoptimization.
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
        val controls = eligibleControls(start, finish, controlPoints).filter { it.points > 0 }
        if (controls.size <= MAX_EXACT_CONTROLS) {
            return exactRoute(distanceMatrix, start, finish, controls, budgetMeters = budgetMeters)
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
            // Shortening now may make another control feasible on the next iteration.
            route = twoOpt(route, distanceMatrix).toMutableList()
            currentDistance = totalDistance(route, distanceMatrix)
        }

        return twoOpt(route, distanceMatrix)
    }

    /**
     * Finds a short route whose collected control score is at least [targetScore].
     * Solves exactly up to 15 positive-score controls. Larger problems use insertion and pruning.
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
        if (remaining.size <= MAX_EXACT_CONTROLS) {
            return exactRoute(
                distanceMatrix, start, finish,
                eligibleControls(start, finish, remaining), targetScore = targetScore,
            )
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
                .filter { (index, point) ->
                    index > 0 && index < route.lastIndex &&
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

    private fun eligibleControls(
        start: ControlPoint,
        finish: ControlPoint,
        controls: List<ControlPoint>,
    ) = controls.filterNot { it.id == start.id || it.id == finish.id }
        .distinctBy(ControlPoint::id)
        .sortedWith(compareBy(ControlPoint::code, ControlPoint::id))

    /**
     * Held–Karp subset search: retain the shortest path for each visited set and last control.
     * Every subset can then be evaluated for either score objective without greedy selection.
     * At 15 controls the distance/parent tables occupy under 5 MiB; larger inputs stay heuristic.
     */
    private fun exactRoute(
        matrix: DistanceMatrix,
        start: ControlPoint,
        finish: ControlPoint,
        controls: List<ControlPoint>,
        budgetMeters: Float? = null,
        targetScore: Int? = null,
    ): List<ControlPoint> {
        val count = controls.size
        val setCount = 1 shl count
        val distances = DoubleArray(setCount * count) { Double.POSITIVE_INFINITY }
        val parents = ByteArray(setCount * count) { -1 }
        val scores = LongArray(setCount)
        val legs = Array(count) { from -> DoubleArray(count) { to ->
            matrix[controls[from], controls[to]].toDouble()
        } }
        val finishLegs = DoubleArray(count) { matrix[controls[it], finish].toDouble() }
        for (last in controls.indices) {
            distances[(1 shl last) * count + last] = matrix[start, controls[last]].toDouble()
        }

        var bestMask = -1
        var bestLast = -1
        var bestDistance = Double.POSITIVE_INFINITY
        var bestScore = -1L
        fun consider(mask: Int, last: Int, distance: Double) {
            val score = scores[mask]
            if (budgetMeters != null && distance > budgetMeters.toDouble() + DISTANCE_EPSILON) return
            if (targetScore != null && score < targetScore) return
            if (budgetMeters == null && targetScore == null && mask != setCount - 1) return
            val better = if (budgetMeters != null) {
                score > bestScore || (score == bestScore && distance < bestDistance)
            } else {
                distance < bestDistance
            }
            if (better) {
                bestMask = mask
                bestLast = last
                bestDistance = distance
                bestScore = score
            }
        }
        consider(0, -1, matrix[start, finish].toDouble())
        for (mask in 1 until setCount) {
            val bit = Integer.numberOfTrailingZeros(mask)
            scores[mask] = scores[mask xor (1 shl bit)] + controls[bit].points
            for (last in controls.indices) {
                if (mask and (1 shl last) == 0) continue
                val previousMask = mask xor (1 shl last)
                val state = mask * count + last
                if (previousMask != 0) {
                    for (previous in controls.indices) {
                        if (previousMask and (1 shl previous) == 0) continue
                        val distance = distances[previousMask * count + previous] + legs[previous][last]
                        if (distance < distances[state]) {
                            distances[state] = distance
                            parents[state] = previous.toByte()
                        }
                    }
                }
                consider(mask, last, distances[state] + finishLegs[last])
            }
        }
        if (bestMask < 0) return emptyList()
        val reversed = mutableListOf(finish)
        var mask = bestMask
        var last = bestLast
        while (last >= 0) {
            reversed += controls[last]
            val previous = parents[mask * count + last].toInt()
            mask = mask xor (1 shl last)
            last = previous
        }
        reversed += start
        return reversed.asReversed()
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
