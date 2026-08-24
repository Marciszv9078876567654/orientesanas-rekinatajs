package com.orientesanasrekinatajs.domain.model

/**
 * One leg of a computed route, from [from] to [to], with its running totals.
 *
 * @property from                          The control point this leg starts at.
 * @property to                            The control point this leg ends at.
 * @property distanceMeters                The physical length of this leg, in meters.
 * @property accumulatedDistanceMeters     Total distance from the route start to the end of
 *                                         this leg, in meters.
 * @property accumulatedPoints             Total score collected up to and including [to].
 */
data class RouteSegment(
    val from: ControlPoint,
    val to: ControlPoint,
    val distanceMeters: Float,
    val accumulatedDistanceMeters: Float,
    val accumulatedPoints: Int,
)
