package com.orientesanasrekinatajs.domain.model

/**
 * The result of a route calculation: an ordered sequence of points together with its
 * aggregate statistics and per-leg breakdown.
 *
 * @property path                 Points in visit order, including the start and finish.
 * @property totalDistanceMeters  Total physical length of the route, in meters.
 * @property totalScore           Total score collected along the route.
 * @property segments             Per-leg breakdown, suitable for the step-by-step table UI.
 */
data class OptimizedRoute(
    val path: List<ControlPoint>,
    val totalDistanceMeters: Float,
    val totalScore: Int,
    val segments: List<RouteSegment>,
)
