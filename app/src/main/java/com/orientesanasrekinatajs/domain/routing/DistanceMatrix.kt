package com.orientesanasrekinatajs.domain.routing

import com.orientesanasrekinatajs.domain.model.ControlPoint

/**
 * Immutable lookup table of physical distances between control points.
 *
 * Pixel distances are divided by [pixelsPerMeter] when the matrix is built, so every value
 * returned by this class is expressed in meters.
 */
class DistanceMatrix(
    points: List<ControlPoint>,
    val pixelsPerMeter: Float,
) {
    val points: List<ControlPoint> = points.toList()
    val size: Int = points.size

    private val indexById: Map<String, Int>
    private val distances: Array<FloatArray>

    init {
        require(pixelsPerMeter.isFinite() && pixelsPerMeter > 0f) {
            "pixelsPerMeter must be finite and positive"
        }
        require(points.all { it.center.x.isFinite() && it.center.y.isFinite() }) {
            "Control point coordinates must be finite"
        }
        require(points.map(ControlPoint::id).distinct().size == points.size) {
            "Control point IDs must be unique"
        }

        indexById = points.mapIndexed { index, point -> point.id to index }.toMap()
        distances = Array(size) { FloatArray(size) }
        for (fromIndex in points.indices) {
            for (toIndex in fromIndex + 1 until size) {
                val distanceMeters = points[fromIndex].center
                    .distanceTo(points[toIndex].center) / pixelsPerMeter
                distances[fromIndex][toIndex] = distanceMeters
                distances[toIndex][fromIndex] = distanceMeters
            }
        }
    }

    operator fun get(fromIndex: Int, toIndex: Int): Float {
        require(fromIndex in 0 until size) { "fromIndex is outside the matrix" }
        require(toIndex in 0 until size) { "toIndex is outside the matrix" }
        return distances[fromIndex][toIndex]
    }

    operator fun get(from: ControlPoint, to: ControlPoint): Float =
        get(indexOf(from), indexOf(to))

    fun contains(point: ControlPoint): Boolean = point.id in indexById

    private fun indexOf(point: ControlPoint): Int =
        requireNotNull(indexById[point.id]) {
            "Control point '${point.id}' is not part of this distance matrix"
        }
}
