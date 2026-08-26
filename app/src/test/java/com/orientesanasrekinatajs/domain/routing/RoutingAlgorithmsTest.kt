package com.orientesanasrekinatajs.domain.routing

import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.Point2D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingAlgorithmsTest {

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
