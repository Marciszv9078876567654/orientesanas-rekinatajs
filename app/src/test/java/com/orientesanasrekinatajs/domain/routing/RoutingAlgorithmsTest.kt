package com.orientesanasrekinatajs.domain.routing

import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RoutingAlgorithmsTest {

    @Test
    fun bestScoreCanRejectAnAttractiveEarlyControlForAHigherTotal() {
        val start = point("start", 0f, 0f)
        val finish = point("finish", 0f, 0f)
        val near = point("near", 1f, 0f, points = 2)
        val valuable = point("valuable", -5f, 0f, points = 9)
        val matrix = DistanceMatrix(listOf(start, finish, near, valuable), 1f)

        val route = RoutingAlgorithms.bestScoreRoute(matrix, start, finish, listOf(near, valuable), 10f)

        assertEquals(listOf(start, valuable, finish), route)
    }

    @Test
    fun smallProblemsMatchExhaustiveSearchInEveryMode() {
        val random = Random(142)
        repeat(20) { sample ->
            val start = point("start", 0f, 0f)
            // Include round trips and distinct endpoints.
            val finish = if (sample % 2 == 0) start else point("finish", 20f, 10f)
            val controls = List(6) { index ->
                point("c$index", random.nextFloat() * 40 - 10, random.nextFloat() * 40 - 10,
                    points = random.nextInt(1, 10))
            }
            val matrix = DistanceMatrix((listOf(start, finish) + controls).distinctBy { it.id }, 1f)
            val budget = 65f
            val target = 15
            var shortest = Float.POSITIVE_INFINITY
            var targetDistance = Float.POSITIVE_INFINITY
            var bestScore = -1
            var scoreDistance = Float.POSITIVE_INFINITY
            fun enumerate(path: List<ControlPoint>, remaining: List<ControlPoint>, score: Int) {
                val distance = RoutingAlgorithms.totalDistance(path + finish, matrix)
                if (remaining.isEmpty()) shortest = minOf(shortest, distance)
                if (score >= target) targetDistance = minOf(targetDistance, distance)
                if (distance <= budget && (score > bestScore || score == bestScore && distance < scoreDistance)) {
                    bestScore = score
                    scoreDistance = distance
                }
                remaining.forEach { next ->
                    enumerate(path + next, remaining - next, score + next.points)
                }
            }
            enumerate(listOf(start), controls, 0)

            fun checkRoute(route: List<ControlPoint>) {
                assertEquals(start, route.first())
                assertEquals(finish, route.last())
                val visited = route.drop(1).dropLast(1)
                assertEquals(visited.size, visited.map { it.id }.distinct().size)
                assertTrue(controls.containsAll(visited))
            }
            val allRoute = RoutingAlgorithms.shortestRoute(matrix, start, finish, controls)
            checkRoute(allRoute)
            assertEquals(controls.size + 2, allRoute.size)
            assertEquals(shortest, RoutingAlgorithms.totalDistance(allRoute, matrix), 0.0001f)
            val scoreRoute = RoutingAlgorithms.bestScoreRoute(matrix, start, finish, controls, budget)
            checkRoute(scoreRoute)
            assertEquals(bestScore, scoreRoute.drop(1).dropLast(1).sumOf { it.points })
            assertEquals(scoreDistance, RoutingAlgorithms.totalDistance(scoreRoute, matrix), 0.0001f)
            if (controls.sumOf { it.points } >= target) {
                val targetRoute = RoutingAlgorithms.shortestRouteForScore(matrix, start, finish, controls, target)
                checkRoute(targetRoute)
                assertTrue(targetRoute.drop(1).dropLast(1).sumOf { it.points } >= target)
                assertEquals(targetDistance, RoutingAlgorithms.totalDistance(targetRoute, matrix), 0.0001f)
            }
            assertEquals(allRoute, RoutingAlgorithms.shortestRoute(matrix, start, finish, controls.reversed()))
        }
    }

    @Test
    fun exactSearchHandlesEmptyControlsDuplicatesAndZeroTarget() {
        val start = point("start", 0f, 0f)
        val finish = point("finish", 10f, 0f)
        val control = point("control", 5f, 1f)
        val matrix = DistanceMatrix(listOf(start, finish, control), 1f)
        assertEquals(listOf(start, finish), RoutingAlgorithms.shortestRoute(matrix, start, finish, emptyList()))
        assertEquals(listOf(start, control, finish), RoutingAlgorithms.shortestRoute(
            matrix, start, finish, listOf(start, control, control, finish)))
        assertEquals(listOf(start, finish), RoutingAlgorithms.shortestRouteForScore(
            matrix, start, finish, listOf(control), 0))
    }

    @Test
    fun exactSearchAtLimitAndHeuristicAboveItKeepEndpointsAndScore() {
        for (count in listOf(15, 16)) {
            // Endpoint roles come from the arguments, even if their type is CONTROL.
            val start = point("start", 0f, 0f)
            val finish = point("finish", 20f, 0f)
            val controls = List(count) { point("c$it", it + 1f, 0f, points = 1) }
            val matrix = DistanceMatrix(listOf(start, finish) + controls, 1f)
            val all = RoutingAlgorithms.shortestRoute(matrix, start, finish, controls)
            assertEquals(listOf(start) + controls + finish, all)
            val score = RoutingAlgorithms.bestScoreRoute(matrix, start, finish, controls, 20f)
            assertEquals(count, score.drop(1).dropLast(1).sumOf { it.points })
            assertEquals(20f, RoutingAlgorithms.totalDistance(score, matrix), 0.0001f)
            val target = RoutingAlgorithms.shortestRouteForScore(matrix, start, finish, controls, 8)
            assertEquals(start, target.first())
            assertEquals(finish, target.last())
            assertTrue(target.drop(1).dropLast(1).sumOf { it.points } >= 8)
            assertEquals(20f, RoutingAlgorithms.totalDistance(target, matrix), 0.0001f)
        }
    }

    @Test
    fun largerSearchPreservesControlsAndNeverWorsensOriginalShortestHeuristic() {
        val random = Random(321)
        val start = point("start", 0f, 0f)
        val finish = point("finish", 100f, 100f)
        val controls = List(24) { point("c$it", random.nextFloat() * 100, random.nextFloat() * 100) }
        val matrix = DistanceMatrix(listOf(start, finish) + controls, 1f)
        val original = RoutingAlgorithms.twoOpt(
            RoutingAlgorithms.nearestNeighbor(matrix, start, finish, controls), matrix)
        val improved = RoutingAlgorithms.shortestRoute(matrix, start, finish, controls)
        assertEquals(start, improved.first())
        assertEquals(finish, improved.last())
        assertEquals(controls.toSet(), improved.drop(1).dropLast(1).toSet())
        assertEquals(controls.size + 2, improved.size)
        assertTrue(RoutingAlgorithms.totalDistance(improved, matrix) <= RoutingAlgorithms.totalDistance(original, matrix))
        val scoreRoute = RoutingAlgorithms.bestScoreRoute(matrix, start, finish, controls, 300f)
        assertTrue(RoutingAlgorithms.totalDistance(scoreRoute, matrix) <= 300.0001f)
        assertEquals(start, scoreRoute.first())
        assertEquals(finish, scoreRoute.last())
    }

    @Test
    fun nearestNeighborVisitsAllControlsAndKeepsFinishLast() {
        val start = point("start", 0f, 0f, type = ControlPointType.START)
        val first = point("first", 2f, 0f, code = 32)
        val second = point("second", 6f, 0f, code = 64)
        val finish = point("finish", 7f, 0f, type = ControlPointType.FINISH)
        val matrix = DistanceMatrix(listOf(start, first, second, finish), 1f)

        val route = RoutingAlgorithms.nearestNeighbor(
            matrix,
            start,
            finish,
            listOf(second, finish, first, start),
        )

        assertEquals(listOf("start", "first", "second", "finish"), route.map { it.id })
    }

    @Test
    fun twoOptUntanglesCrossingWithoutMovingEndpoints() {
        val start = point("start", 0f, 0f)
        val upperRight = point("upper-right", 10f, 10f)
        val upperLeft = point("upper-left", 0f, 10f)
        val finish = point("finish", 10f, 0f)
        val points = listOf(start, upperRight, upperLeft, finish)
        val matrix = DistanceMatrix(points, 1f)

        val optimized = RoutingAlgorithms.twoOpt(points, matrix)

        assertEquals(
            listOf("start", "upper-left", "upper-right", "finish"),
            optimized.map { it.id },
        )
        assertEquals(start, optimized.first())
        assertEquals(finish, optimized.last())
        assertTrue(
            RoutingAlgorithms.totalDistance(optimized, matrix) <
                RoutingAlgorithms.totalDistance(points, matrix),
        )
    }

    @Test
    fun bestScoreRouteStaysWithinBudget() {
        val start = point("start", 0f, 0f)
        val near = point("near", 5f, 1f, code = 55, points = 5)
        val infeasible = point("infeasible", 5f, 5f, code = 101, points = 50)
        val finish = point("finish", 10f, 0f)
        val matrix = DistanceMatrix(listOf(start, near, infeasible, finish), 1f)

        val route = RoutingAlgorithms.bestScoreRoute(
            matrix,
            start,
            finish,
            listOf(near, infeasible),
            budgetMeters = 11f,
        )

        assertEquals(listOf("start", "near", "finish"), route.map { it.id })
        assertTrue(RoutingAlgorithms.totalDistance(route, matrix) <= 11f)
    }

    @Test
    fun bestScoreRouteIncludesPositiveZeroCostInsertion() {
        val start = point("start", 0f, 0f)
        val onRoute = point("on-route", 5f, 0f, points = 8)
        val zeroScore = point("zero-score", 7f, 0f, points = 0)
        val finish = point("finish", 10f, 0f)
        val matrix = DistanceMatrix(listOf(start, onRoute, zeroScore, finish), 1f)

        val route = RoutingAlgorithms.bestScoreRoute(
            matrix,
            start,
            finish,
            listOf(zeroScore, onRoute),
            budgetMeters = 10f,
        )

        assertEquals(listOf("start", "on-route", "finish"), route.map { it.id })
        assertEquals(10f, RoutingAlgorithms.totalDistance(route, matrix), 0.0001f)
    }

    @Test
    fun bestScoreRouteReturnsEmptyWhenDirectLegExceedsBudget() {
        val start = point("start", 0f, 0f)
        val finish = point("finish", 10f, 0f)
        val matrix = DistanceMatrix(listOf(start, finish), 1f)

        val route = RoutingAlgorithms.bestScoreRoute(
            matrix,
            start,
            finish,
            emptyList(),
            budgetMeters = 9.9f,
        )

        assertTrue(route.isEmpty())
    }

    @Test
    fun invalidBudgetIsRejected() {
        val start = point("start", 0f, 0f)
        val finish = point("finish", 10f, 0f)
        val matrix = DistanceMatrix(listOf(start, finish), 1f)

        assertThrows(IllegalArgumentException::class.java) {
            RoutingAlgorithms.bestScoreRoute(
                matrix,
                start,
                finish,
                emptyList(),
                budgetMeters = Float.NaN,
            )
        }
    }

    @Test
    fun shortestRouteForScoreMeetsTargetAndPrunesUnneededControl() {
        val start = point("start", 0f, 0f, type = ControlPointType.START)
        val near = point("near", 5f, 1f, points = 5)
        val higherScore = point("higher", 5f, 5f, points = 10)
        val finish = point("finish", 10f, 0f, type = ControlPointType.FINISH)
        val matrix = DistanceMatrix(listOf(start, near, higherScore, finish), 1f)

        val route = RoutingAlgorithms.shortestRouteForScore(
            matrix,
            start,
            finish,
            listOf(near, higherScore),
            targetScore = 8,
        )

        assertEquals(listOf("start", "higher", "finish"), route.map(ControlPoint::id))
        assertTrue(route.filter { it.type == ControlPointType.CONTROL }.sumOf(ControlPoint::points) >= 8)
    }

    private fun point(
        id: String,
        x: Float,
        y: Float,
        code: Int = 31,
        points: Int = code / 10,
        type: ControlPointType = ControlPointType.CONTROL,
    ) = ControlPoint(
        id = id,
        code = code,
        points = points,
        center = Point2D(x, y),
        type = type,
    )
}
