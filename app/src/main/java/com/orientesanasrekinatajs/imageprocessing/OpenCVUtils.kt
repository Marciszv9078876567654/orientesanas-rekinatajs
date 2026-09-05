package com.orientesanasrekinatajs.imageprocessing

import android.graphics.Bitmap
import com.orientesanasrekinatajs.domain.model.MapBoundary
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.ControlPointType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Pure image-processing operations used by the map scanning pipeline. */
object OpenCVUtils {
    private const val MAX_PROCESSING_DIMENSION = 3_000.0
    private const val MIN_BOUNDARY_AREA_FRACTION = 0.05
    private const val QUADRILATERAL_EPSILON_FACTOR = 0.02
    private const val MIN_RING_HOLE_AREA_RATIO = 0.35
    private const val MAX_RING_HOLE_AREA_RATIO = 0.95
    private const val MAX_RING_CENTER_OFFSET_RADIUS_FRACTION = 0.30
    // Printed course ink is commonly much paler than its digital source, especially in photos.
    private const val DEFAULT_HUE_MIN = 125.0
    private const val DEFAULT_HUE_MAX = 175.0
    private const val DEFAULT_SATURATION_MIN = 45.0
    private const val DEFAULT_VALUE_MIN = 40.0
    private const val HUE_TOLERANCE = 7.5
    private const val SATURATION_TOLERANCE_DOWN = 55.0
    private const val SATURATION_TOLERANCE_UP = 30.0
    private const val VALUE_TOLERANCE_DOWN = 70.0
    private const val VALUE_TOLERANCE_UP = 40.0
    private const val MIN_CALIBRATED_SATURATION = 35.0
    private const val MIN_CALIBRATED_VALUE = 30.0
    private const val SAMPLE_WINDOW_RADIUS = 64
    private const val SAMPLE_MIN_RADIUS = 6
    private const val SAMPLE_HUE_MIN = 115
    private const val SAMPLE_HUE_MAX = 179
    private const val SAMPLE_HUE_DIFFERENCE = 10.0
    private const val SAMPLE_ANGLE_BUCKETS = 36
    private const val SAMPLE_HUE_BUCKET_TOLERANCE = 4
    private const val MIN_SAMPLE_ANGLE_COVERAGE = 10
    private const val UNBACKED_RADIUS_MIN_FACTOR = 0.68
    private const val UNBACKED_RADIUS_MAX_FACTOR = 1.35
    private const val HOUGH_RADIUS_MIN_FACTOR = 0.82
    private const val HOUGH_RADIUS_MAX_FACTOR = 1.20
    private const val TRIANGLE_RADIUS_MIN_FACTOR = 0.75
    private const val TRIANGLE_RADIUS_MAX_FACTOR = 1.45
    private const val MIN_TRIANGLE_SIDE_RATIO = 0.68
    private const val MIN_HOUGH_RING_COVERAGE = 0.34
    private const val MAX_HOUGH_CENTER_INK_FRACTION = 0.22

    private val isOpenCvLoaded: Boolean by lazy { OpenCVLoader.initLocal() }

    /**
     * Detects the largest convex quadrilateral in [bitmap].
     *
     * Processing is capped at 3000 pixels on the longest axis. Returned coordinates are
     * scaled back into the original bitmap's pixel space.
     */
    fun detectBoundaries(bitmap: Bitmap): MapBoundary? {
        requireOpenCv()

        val source = Mat()
        val working = Mat()
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()

        return try {
            Utils.bitmapToMat(bitmap, source)
            val scale = minOf(
                1.0,
                MAX_PROCESSING_DIMENSION / max(bitmap.width, bitmap.height).toDouble(),
            )
            if (scale < 1.0) {
                Imgproc.resize(
                    source,
                    working,
                    Size(source.cols() * scale, source.rows() * scale),
                    0.0,
                    0.0,
                    Imgproc.INTER_AREA,
                )
            } else {
                source.copyTo(working)
            }

            Imgproc.cvtColor(working, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)
            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE,
            )

            val minimumArea = working.rows().toDouble() * working.cols() *
                MIN_BOUNDARY_AREA_FRACTION
            var largestArea = 0.0
            var largestQuadrilateral: Array<Point>? = null

            contours.forEach { contour ->
                val contour2f = MatOfPoint2f(*contour.toArray())
                val approximation = MatOfPoint2f()
                try {
                    val perimeter = Imgproc.arcLength(contour2f, true)
                    Imgproc.approxPolyDP(
                        contour2f,
                        approximation,
                        QUADRILATERAL_EPSILON_FACTOR * perimeter,
                        true,
                    )
                    val area = Imgproc.contourArea(approximation)
                    if (
                        approximation.total() == 4L &&
                        area >= minimumArea &&
                        area > largestArea
                    ) {
                        val integerApproximation = MatOfPoint(*approximation.toArray())
                        val isConvex = try {
                            Imgproc.isContourConvex(integerApproximation)
                        } finally {
                            integerApproximation.release()
                        }
                        if (isConvex) {
                            largestArea = area
                            largestQuadrilateral = approximation.toArray()
                        }
                    }
                } finally {
                    contour2f.release()
                    approximation.release()
                }
            }

            largestQuadrilateral?.let { quadrilateral ->
                val originalPoints = quadrilateral.map { point ->
                    Point(point.x / scale, point.y / scale)
                }
                orderedBoundary(originalPoints)
            }
        } finally {
            contours.forEach { it.release() }
            hierarchy.release()
            edges.release()
            blurred.release()
            gray.release()
            working.release()
            source.release()
        }
    }

    /** Rectifies [boundary] into a front-facing rectangular bitmap. */
    fun warpPerspective(bitmap: Bitmap, boundary: MapBoundary): Bitmap {
        requireOpenCv()
        require(boundary.corners().all { it.x.isFinite() && it.y.isFinite() }) {
            "Boundary coordinates must be finite"
        }

        val outputWidth = maxOf(
            boundary.topLeft.distanceTo(boundary.topRight),
            boundary.bottomLeft.distanceTo(boundary.bottomRight),
        ).roundToInt().coerceAtLeast(1)
        val outputHeight = maxOf(
            boundary.topLeft.distanceTo(boundary.bottomLeft),
            boundary.topRight.distanceTo(boundary.bottomRight),
        ).roundToInt().coerceAtLeast(1)
        require(outputWidth > 1 && outputHeight > 1) { "Boundary is too small to rectify" }

        val source = Mat()
        val warped = Mat()
        val sourceCorners = MatOfPoint2f(
            Point(boundary.topLeft.x.toDouble(), boundary.topLeft.y.toDouble()),
            Point(boundary.topRight.x.toDouble(), boundary.topRight.y.toDouble()),
            Point(boundary.bottomRight.x.toDouble(), boundary.bottomRight.y.toDouble()),
            Point(boundary.bottomLeft.x.toDouble(), boundary.bottomLeft.y.toDouble()),
        )
        val targetCorners = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((outputWidth - 1).toDouble(), 0.0),
            Point((outputWidth - 1).toDouble(), (outputHeight - 1).toDouble()),
            Point(0.0, (outputHeight - 1).toDouble()),
        )
        val transform = Imgproc.getPerspectiveTransform(sourceCorners, targetCorners)

        return try {
            Utils.bitmapToMat(bitmap, source)
            Imgproc.warpPerspective(
                source,
                warped,
                transform,
                Size(outputWidth.toDouble(), outputHeight.toDouble()),
                Imgproc.INTER_LINEAR,
                Core.BORDER_REPLICATE,
            )
            Bitmap.createBitmap(
                outputWidth,
                outputHeight,
                Bitmap.Config.ARGB_8888,
            ).also { Utils.matToBitmap(warped, it) }
        } finally {
            transform.release()
            targetCorners.release()
            sourceCorners.release()
            warped.release()
            source.release()
        }
    }

    /** Projects points from one rectified boundary through source-image space into another. */
    fun reprojectPoints(
        points: List<Point2D>,
        oldBoundary: MapBoundary,
        newBoundary: MapBoundary,
    ): List<Point2D?> {
        if (points.isEmpty()) return emptyList()
        requireOpenCv()
        val oldSize = rectifiedSize(oldBoundary)
        val newSize = rectifiedSize(newBoundary)
        val oldRectangle = rectangleCorners(oldSize.first, oldSize.second)
        val oldSource = boundaryCorners(oldBoundary)
        val newSource = boundaryCorners(newBoundary)
        val newRectangle = rectangleCorners(newSize.first, newSize.second)
        val toSource = Imgproc.getPerspectiveTransform(oldRectangle, oldSource)
        val toNewRectangle = Imgproc.getPerspectiveTransform(newSource, newRectangle)
        val input = MatOfPoint2f(*points.map { Point(it.x.toDouble(), it.y.toDouble()) }.toTypedArray())
        val sourcePoints = MatOfPoint2f()
        val projected = MatOfPoint2f()
        return try {
            Core.perspectiveTransform(input, sourcePoints, toSource)
            Core.perspectiveTransform(sourcePoints, projected, toNewRectangle)
            projected.toArray().map { point ->
                if (
                    point.x.isFinite() && point.y.isFinite() &&
                    point.x in 0.0..newSize.first.toDouble() &&
                    point.y in 0.0..newSize.second.toDouble()
                ) {
                    Point2D(point.x.toFloat(), point.y.toFloat())
                } else {
                    null
                }
            }
        } finally {
            projected.release()
            sourcePoints.release()
            input.release()
            toNewRectangle.release()
            toSource.release()
            newRectangle.release()
            newSource.release()
            oldSource.release()
            oldRectangle.release()
        }
    }

    private fun rectifiedSize(boundary: MapBoundary): Pair<Int, Int> =
        maxOf(
            boundary.topLeft.distanceTo(boundary.topRight),
            boundary.bottomLeft.distanceTo(boundary.bottomRight),
        ).roundToInt().coerceAtLeast(1) to maxOf(
            boundary.topLeft.distanceTo(boundary.bottomLeft),
            boundary.topRight.distanceTo(boundary.bottomRight),
        ).roundToInt().coerceAtLeast(1)

    private fun boundaryCorners(boundary: MapBoundary) = MatOfPoint2f(
        Point(boundary.topLeft.x.toDouble(), boundary.topLeft.y.toDouble()),
        Point(boundary.topRight.x.toDouble(), boundary.topRight.y.toDouble()),
        Point(boundary.bottomRight.x.toDouble(), boundary.bottomRight.y.toDouble()),
        Point(boundary.bottomLeft.x.toDouble(), boundary.bottomLeft.y.toDouble()),
    )

    private fun rectangleCorners(width: Int, height: Int) = MatOfPoint2f(
        Point(0.0, 0.0),
        Point((width - 1).toDouble(), 0.0),
        Point((width - 1).toDouble(), (height - 1).toDouble()),
        Point(0.0, (height - 1).toDouble()),
    )

    /**
     * Detects magenta control symbols and returns their centers in bitmap pixel space.
     *
     * Both the outer and inner edges of outlined symbols may produce contours. Overlapping
     * candidates are therefore merged, which also makes finish double-circles emit once.
     */
    fun detectControlPoints(bitmap: Bitmap): List<Point2D> =
        detectControlSymbols(bitmap).map(DetectedControlSymbol::center)

    /**
     * Detects control centers together with the symbol type needed by routing orchestration.
     *
     * [requireRingHole] is intended for diagnostics and tests. By default, hierarchy-backed
     * rings establish the expected symbol size. Equally sized circular contours without a
     * preserved hole are retained because map detail, a course line, or faded print can break a
     * real ring's contour. Contours with a clearly non-ring child are still rejected as digits.
     */
    fun detectControlSymbols(
        bitmap: Bitmap,
        requireRingHole: Boolean = false,
        colorCalibration: ColorCalibrationSample? = null,
    ): List<DetectedControlSymbol> {
        requireOpenCv()

        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val mask = Mat()
        val closedMask = Mat()
        val blurredMask = Mat()
        val houghCircles = Mat()
        val hierarchy = Mat()
        val contours = mutableListOf<MatOfPoint>()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))

        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            val (lowerColor, upperColor) = colorBounds(colorCalibration)
            Core.inRange(
                hsv,
                lowerColor,
                upperColor,
                mask,
            )
            Imgproc.morphologyEx(mask, closedMask, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.findContours(
                closedMask,
                contours,
                hierarchy,
                Imgproc.RETR_TREE,
                Imgproc.CHAIN_APPROX_SIMPLE,
            )

            val minimumDimension = minOf(bitmap.width, bitmap.height).toDouble()
            // Calibration primarily identifies ink color. A broken ring or an off-center tap can
            // distort its sampled radius, so never use that estimate to reject contour sizes.
            val minimumRadius = max(4.0, minimumDimension * 0.004)
            val maximumRadius = minimumDimension * 0.20
            val allCandidates = contours.mapIndexedNotNull { index, contour ->
                val childIndex = hierarchyEntry(hierarchy, index)?.getOrNull(2)?.toInt() ?: -1
                val childContour = contours.getOrNull(childIndex)
                symbolCandidate(contour, childContour, minimumRadius, maximumRadius)
            }
            val ringRadii = allCandidates
                .filter { it.shape == SymbolShape.CIRCLE && it.ringEvidence == RingEvidence.MATCH }
                .map(SymbolCandidate::radius)
                .sorted()
            val referenceRadius = ringRadii.takeIf(List<Float>::isNotEmpty)
                ?.let { radii -> radii[radii.size / 2] }
                ?: colorCalibration?.estimatedRadius
            val recoveredCircles = referenceRadius?.let { expectedRadius ->
                Imgproc.GaussianBlur(mask, blurredMask, Size(5.0, 5.0), 1.4)
                val houghMinimumRadius = max(
                    minimumRadius,
                    expectedRadius * HOUGH_RADIUS_MIN_FACTOR,
                ).roundToInt()
                val houghMaximumRadius = minOf(
                    maximumRadius,
                    expectedRadius * HOUGH_RADIUS_MAX_FACTOR,
                ).roundToInt()
                Imgproc.HoughCircles(
                    blurredMask,
                    houghCircles,
                    Imgproc.HOUGH_GRADIENT,
                    1.2,
                    expectedRadius * 1.5,
                    80.0,
                    max(8.0, expectedRadius * 0.45),
                    houghMinimumRadius,
                    houghMaximumRadius,
                )
                (0 until houghCircles.cols()).mapNotNull { index ->
                    val circle = houghCircles.get(0, index) ?: return@mapNotNull null
                    val center = Point2D(circle[0].toFloat(), circle[1].toFloat())
                    val radius = circle[2].toFloat()
                    val nearbyContours = allCandidates.filter { candidate ->
                        candidate.center.distanceTo(center) <=
                            max(candidate.radius, radius) * 0.40f
                    }
                    val overlapsAcceptedCircle = nearbyContours.any { candidate ->
                        candidate.shape == SymbolShape.CIRCLE &&
                            candidate.ringEvidence != RingEvidence.MISMATCH
                    }
                    val overlapsTriangle = nearbyContours.any {
                        it.shape == SymbolShape.TRIANGLE
                    }
                    val concentricRejectedCircles = nearbyContours.count {
                        it.shape == SymbolShape.CIRCLE &&
                            it.ringEvidence == RingEvidence.MISMATCH
                    }
                    if (overlapsAcceptedCircle || overlapsTriangle) {
                        return@mapNotNull null
                    }
                    val coverage = ringInkCoverage(mask, center, radius)
                    if (coverage < MIN_HOUGH_RING_COVERAGE ||
                        diskInkFraction(mask, center, radius * 0.25f) >
                        MAX_HOUGH_CENTER_INK_FRACTION
                    ) {
                        return@mapNotNull null
                    }
                    SymbolCandidate(
                        center = center,
                        radius = radius,
                        shape = SymbolShape.CIRCLE,
                        ringEvidence = RingEvidence.NONE,
                        shapeConfidence = coverage,
                        ringMultiplicity = if (concentricRejectedCircles >= 1) 3 else 1,
                    )
                }
            }.orEmpty()
            val circleCandidates = (allCandidates + recoveredCircles).mapNotNull { candidate ->
                if (candidate.shape != SymbolShape.CIRCLE) return@mapNotNull null
                val concentricInnerRing = allCandidates.any { inner ->
                    inner !== candidate && inner.shape == SymbolShape.CIRCLE &&
                        inner.radius <= candidate.radius * 0.70f &&
                        inner.radius >= candidate.radius * 0.35f &&
                        inner.center.distanceTo(candidate.center) <= candidate.radius * 0.15f
                }
                when {
                    candidate.ringEvidence == RingEvidence.MATCH -> candidate
                    candidate.ringEvidence == RingEvidence.MISMATCH && concentricInnerRing ->
                        candidate.copy(ringMultiplicity = 3)
                    requireRingHole || candidate.ringEvidence == RingEvidence.MISMATCH -> null
                    referenceRadius == null -> candidate
                    candidate.radius in
                        (referenceRadius * UNBACKED_RADIUS_MIN_FACTOR)..
                        (referenceRadius * UNBACKED_RADIUS_MAX_FACTOR) -> candidate
                    else -> null
                }
            }
            // A course has one start. Requiring an equilateral, control-sized triangle prevents
            // digits and short angular fragments in the ink mask from becoming dozens of starts.
            val startCandidate = referenceRadius?.let { expectedRadius ->
                allCandidates.asSequence()
                    .filter { candidate ->
                        candidate.shape == SymbolShape.TRIANGLE &&
                            candidate.radius in
                            (expectedRadius * TRIANGLE_RADIUS_MIN_FACTOR)..
                            (expectedRadius * TRIANGLE_RADIUS_MAX_FACTOR)
                    }
                    .maxByOrNull(SymbolCandidate::shapeConfidence)
            }
            val candidates = (circleCandidates + listOfNotNull(startCandidate))
                .sortedByDescending(SymbolCandidate::radius)

            val symbolGroups = mutableListOf<MutableList<SymbolCandidate>>()
            candidates.forEach { candidate ->
                val group = symbolGroups.firstOrNull { existing ->
                    val largest = existing.maxBy(SymbolCandidate::radius)
                    candidate.center.distanceTo(largest.center) <=
                        max(3f, largest.radius + candidate.radius)
                }
                if (group == null) symbolGroups += mutableListOf(candidate) else group += candidate
            }

            val detectedSymbols = symbolGroups.map { group ->
                val largest = group.maxBy(SymbolCandidate::radius)
                val hasTriangle = group.any { it.shape == SymbolShape.TRIANGLE }
                val circleCount = group.filter { it.shape == SymbolShape.CIRCLE }
                    .sumOf(SymbolCandidate::ringMultiplicity)
                val type = when {
                    circleCount >= 3 -> ControlPointType.FINISH
                    hasTriangle && circleCount > 0 -> ControlPointType.START_FINISH
                    hasTriangle -> ControlPointType.START
                    else -> ControlPointType.CONTROL
                }
                DetectedControlSymbol(largest.center, largest.radius, type)
            }.sortedWith(compareBy({ it.center.y }, { it.center.x }))

            // A double circle can occasionally contribute a triangular approximation at one
            // contour level. When a separate start triangle exists, that combined-looking symbol
            // is unambiguously the course finish rather than a second start.
            if (
                detectedSymbols.any { it.type == ControlPointType.START } &&
                detectedSymbols.none { it.type == ControlPointType.FINISH }
            ) {
                detectedSymbols.map { symbol ->
                    if (symbol.type == ControlPointType.START_FINISH) {
                        symbol.copy(type = ControlPointType.FINISH)
                    } else {
                        symbol
                    }
                }
            } else {
                detectedSymbols
            }
        } finally {
            contours.forEach { it.release() }
            houghCircles.release()
            blurredMask.release()
            kernel.release()
            hierarchy.release()
            closedMask.release()
            mask.release()
            hsv.release()
            rgb.release()
            rgba.release()
        }
    }

    private fun symbolCandidate(
        contour: MatOfPoint,
        childContour: MatOfPoint?,
        minimumRadius: Double,
        maximumRadius: Double,
    ): SymbolCandidate? {
        val contour2f = MatOfPoint2f(*contour.toArray())
        val approximation = MatOfPoint2f()
        return try {
            val perimeter = Imgproc.arcLength(contour2f, true)
            if (perimeter <= 0.0) return null

            val area = Imgproc.contourArea(contour)
            val center = Point()
            val radius = FloatArray(1)
            Imgproc.minEnclosingCircle(contour2f, center, radius)
            if (radius[0] !in minimumRadius.toFloat()..maximumRadius.toFloat()) return null

            val circleArea = PI * radius[0] * radius[0]
            val circularity = 4.0 * PI * area / (perimeter * perimeter)
            val circleFillRatio = area / circleArea
            val isCircle = circularity >= 0.68 && circleFillRatio >= 0.66

            Imgproc.approxPolyDP(contour2f, approximation, perimeter * 0.04, true)
            val integerApproximation = MatOfPoint(*approximation.toArray())
            var triangleSideRatio = 0.0
            val isTriangle = try {
                val vertices = approximation.toArray()
                val sides = if (vertices.size == 3) {
                    listOf(
                        pointDistance(vertices[0], vertices[1]),
                        pointDistance(vertices[1], vertices[2]),
                        pointDistance(vertices[2], vertices[0]),
                    )
                } else {
                    emptyList()
                }
                triangleSideRatio = sides.minOrNull()?.div(sides.maxOrNull() ?: 1.0) ?: 0.0
                approximation.total() == 3L &&
                    Imgproc.isContourConvex(integerApproximation) &&
                    triangleSideRatio >= MIN_TRIANGLE_SIDE_RATIO &&
                    circleFillRatio in 0.30..0.52 &&
                    area >= minimumRadius * minimumRadius
            } finally {
                integerApproximation.release()
            }

            if (!isCircle && !isTriangle) return null
            SymbolCandidate(
                center = Point2D(center.x.toFloat(), center.y.toFloat()),
                radius = radius[0],
                shape = if (isCircle) SymbolShape.CIRCLE else SymbolShape.TRIANGLE,
                ringEvidence = when {
                    !isCircle -> RingEvidence.NONE
                    childContour == null -> RingEvidence.NONE
                    hasConcentricRingHole(contour, childContour, radius[0]) -> RingEvidence.MATCH
                    else -> RingEvidence.MISMATCH
                },
                shapeConfidence = if (isTriangle) triangleSideRatio else circularity,
                ringMultiplicity = 1,
            )
        } finally {
            approximation.release()
            contour2f.release()
        }
    }

    /**
     * Samples the circular ink surrounding [tapPoint] and returns tolerant HSV/radius bounds.
     *
     * This deliberately does not flood-fill from one seed pixel: real control rings are often
     * interrupted by map detail, and the most saturated nearby pixel can belong to a contour or
     * vegetation symbol. Instead, hue/radius candidates are scored by angular coverage around
     * the tapped center. A line has little angular coverage while a broken ring still has plenty.
     */
    fun sampleControlPointColor(
        bitmap: Bitmap,
        tapPoint: Point2D,
    ): ColorCalibrationSample? {
        requireOpenCv()
        if (tapPoint.x !in 0f..<bitmap.width.toFloat() || tapPoint.y !in 0f..<bitmap.height.toFloat()) {
            return null
        }

        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)

            val centerX = tapPoint.x.roundToInt()
            val centerY = tapPoint.y.roundToInt()
            val left = (centerX - SAMPLE_WINDOW_RADIUS).coerceAtLeast(0)
            val top = (centerY - SAMPLE_WINDOW_RADIUS).coerceAtLeast(0)
            val right = (centerX + SAMPLE_WINDOW_RADIUS).coerceAtMost(bitmap.width - 1)
            val bottom = (centerY + SAMPLE_WINDOW_RADIUS).coerceAtMost(bitmap.height - 1)
            val maximumRadius = minOf(
                SAMPLE_WINDOW_RADIUS - 4,
                minOf(bitmap.width, bitmap.height) / 5,
            )
            if (maximumRadius < SAMPLE_MIN_RADIUS) return null
            val coverage = Array(180) { LongArray(maximumRadius + 1) }
            for (y in top..bottom) {
                for (x in left..right) {
                    val pixel = hsv.get(y, x) ?: continue
                    if (pixel[1] < MIN_CALIBRATED_SATURATION || pixel[2] < MIN_CALIBRATED_VALUE) {
                        continue
                    }
                    val dx = (x - tapPoint.x).toDouble()
                    val dy = (y - tapPoint.y).toDouble()
                    val radius = sqrt(dx * dx + dy * dy).roundToInt()
                    if (radius !in 1..maximumRadius) continue
                    val hue = pixel[0].roundToInt().coerceIn(0, 179)
                    val angle = ((atan2(dy, dx) + PI) / (2.0 * PI) * SAMPLE_ANGLE_BUCKETS)
                        .toInt().coerceIn(0, SAMPLE_ANGLE_BUCKETS - 1)
                    coverage[hue][radius] = coverage[hue][radius] or (1L shl angle)
                }
            }

            var bestHue = -1
            var bestRadius = -1
            var bestCoverage = 0
            fun combinedAngles(hue: Int, radius: Int, radialTolerance: Int): Long {
                var angles = 0L
                for (hueOffset in -SAMPLE_HUE_BUCKET_TOLERANCE..SAMPLE_HUE_BUCKET_TOLERANCE) {
                    val nearbyHue = (hue + hueOffset + 180) % 180
                    for (nearbyRadius in (radius - radialTolerance).coerceAtLeast(1)..
                        (radius + radialTolerance).coerceAtMost(maximumRadius)) {
                        angles = angles or coverage[nearbyHue][nearbyRadius]
                    }
                }
                return angles
            }
            for (hue in SAMPLE_HUE_MIN..SAMPLE_HUE_MAX) {
                for (radius in SAMPLE_MIN_RADIUS..maximumRadius) {
                    val radialTolerance = (radius * 0.22).roundToInt().coerceIn(3, 8)
                    val angles = combinedAngles(hue, radius, radialTolerance)
                    val angleCount = java.lang.Long.bitCount(angles)
                    val innerRadius = (radius * 0.4).roundToInt().coerceAtLeast(1)
                    val innerAngles = combinedAngles(hue, innerRadius, 2)
                    val innerAngleCount = java.lang.Long.bitCount(innerAngles)
                    // A filled patch or thick line can surround a tiny radius, but a ring has
                    // substantially less matching ink near its center than on its perimeter.
                    if (innerAngleCount >= angleCount * 0.7) continue
                    if (angleCount > bestCoverage) {
                        bestCoverage = angleCount
                        bestHue = hue
                        bestRadius = radius
                    }
                }
            }
            if (bestCoverage < MIN_SAMPLE_ANGLE_COVERAGE) return null

            // Broad radial tolerance finds faded/broken rings, but can make many radii tie. Refine
            // with a narrow annulus and take the middle of the strongest contiguous band; this
            // avoids calibrating to an oversized radius when only part of a ring is enclosed.
            val exactScores = (SAMPLE_MIN_RADIUS..maximumRadius).map { radius ->
                radius to java.lang.Long.bitCount(combinedAngles(bestHue, radius, 1))
            }
            val maximumExactCoverage = exactScores.maxOf(Pair<Int, Int>::second)
            val strongRadii = exactScores
                .filter { (_, score) -> score >= maxOf(MIN_SAMPLE_ANGLE_COVERAGE, maximumExactCoverage - 2) }
                .map(Pair<Int, Int>::first)
            if (strongRadii.isEmpty()) return null
            val radiusBands = mutableListOf<MutableList<Int>>()
            strongRadii.forEach { radius ->
                val band = radiusBands.lastOrNull()
                if (band == null || radius > band.last() + 1) {
                    radiusBands += mutableListOf(radius)
                } else {
                    band += radius
                }
            }
            val strongestBand = radiusBands.maxBy { band ->
                band.sumOf { radius -> exactScores[radius - SAMPLE_MIN_RADIUS].second }
            }
            bestRadius = strongestBand[strongestBand.size / 2]

            val radialTolerance = (bestRadius * 0.22).roundToInt().coerceIn(3, 8)
            val sampled = mutableListOf<SamplePixel>()
            for (y in top..bottom) {
                for (x in left..right) {
                    val pixel = hsv.get(y, x) ?: continue
                    val dx = (x - tapPoint.x).toDouble()
                    val dy = (y - tapPoint.y).toDouble()
                    val radius = sqrt(dx * dx + dy * dy)
                    val hueDifference = abs(pixel[0] - bestHue).let { minOf(it, 180.0 - it) }
                    if (hueDifference <= SAMPLE_HUE_DIFFERENCE &&
                        abs(radius - bestRadius) <= radialTolerance &&
                        pixel[1] >= MIN_CALIBRATED_SATURATION && pixel[2] >= MIN_CALIBRATED_VALUE
                    ) {
                        sampled += SamplePixel(x, y, pixel[0], pixel[1], pixel[2])
                    }
                }
            }
            if (sampled.size < 12) return null
            val sampledRadius = percentile(
                sampled.map { pixel ->
                    val dx = pixel.x - tapPoint.x
                    val dy = pixel.y - tapPoint.y
                    sqrt((dx * dx + dy * dy).toDouble())
                },
                0.95,
            ).toFloat()

            val hueRange = percentile(sampled.map(SamplePixel::hue), 0.05)..
                percentile(sampled.map(SamplePixel::hue), 0.95)
            val saturationRange = percentile(sampled.map(SamplePixel::saturation), 0.05)..
                percentile(sampled.map(SamplePixel::saturation), 0.95)
            val valueRange = percentile(sampled.map(SamplePixel::value), 0.05)..
                percentile(sampled.map(SamplePixel::value), 0.95)
            ColorCalibrationSample(
                hueRange = (hueRange.start - HUE_TOLERANCE).coerceAtLeast(0.0)..
                    (hueRange.endInclusive + HUE_TOLERANCE).coerceAtMost(179.0),
                saturationRange = (saturationRange.start - SATURATION_TOLERANCE_DOWN)
                    .coerceAtLeast(MIN_CALIBRATED_SATURATION)..
                    (saturationRange.endInclusive + SATURATION_TOLERANCE_UP).coerceAtMost(255.0),
                valueRange = (valueRange.start - VALUE_TOLERANCE_DOWN)
                    .coerceAtLeast(MIN_CALIBRATED_VALUE)..
                    (valueRange.endInclusive + VALUE_TOLERANCE_UP).coerceAtMost(255.0),
                estimatedRadius = sampledRadius,
            )
        } finally {
            hsv.release()
            rgb.release()
            rgba.release()
        }
    }

    /** Returns a black-on-white bitmap containing only ink in the active detection range. */
    fun isolateInkColor(
        bitmap: Bitmap,
        colorCalibration: ColorCalibrationSample? = null,
    ): Bitmap {
        requireOpenCv()
        val rgba = Mat()
        val rgb = Mat()
        val hsv = Mat()
        val mask = Mat()
        val isolated = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4, Scalar(255.0, 255.0, 255.0, 255.0))
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            val (lowerColor, upperColor) = colorBounds(colorCalibration)
            Core.inRange(hsv, lowerColor, upperColor, mask)
            isolated.setTo(Scalar(0.0, 0.0, 0.0, 255.0), mask)
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888).also { output ->
                Utils.matToBitmap(isolated, output)
            }
        } finally {
            isolated.release()
            mask.release()
            hsv.release()
            rgb.release()
            rgba.release()
        }
    }

    private fun colorBounds(calibration: ColorCalibrationSample?): Pair<Scalar, Scalar> =
        if (calibration == null) {
            Scalar(DEFAULT_HUE_MIN, DEFAULT_SATURATION_MIN, DEFAULT_VALUE_MIN) to
                Scalar(DEFAULT_HUE_MAX, 255.0, 255.0)
        } else {
            Scalar(
                calibration.hueRange.start,
                calibration.saturationRange.start,
                calibration.valueRange.start,
            ) to Scalar(
                calibration.hueRange.endInclusive,
                calibration.saturationRange.endInclusive,
                calibration.valueRange.endInclusive,
            )
        }

    private fun percentile(values: List<Double>, fraction: Double): Double {
        val sorted = values.sorted()
        return sorted[((sorted.lastIndex * fraction).roundToInt()).coerceIn(sorted.indices)]
    }

    /** Reads `[next, previous, firstChild, parent]` for a contour from OpenCV's hierarchy. */
    private fun hierarchyEntry(hierarchy: Mat, contourIndex: Int): DoubleArray? =
        if (hierarchy.rows() == 1) {
            hierarchy.get(0, contourIndex)
        } else {
            hierarchy.get(contourIndex, 0)
        }

    /** Returns whether [childContour] is a large, centered hole inside [outerContour]. */
    private fun hasConcentricRingHole(
        outerContour: MatOfPoint,
        childContour: MatOfPoint,
        outerRadius: Float,
    ): Boolean {
        val outerArea = Imgproc.contourArea(outerContour)
        if (outerArea <= 0.0) return false
        val holeAreaRatio = Imgproc.contourArea(childContour) / outerArea
        if (holeAreaRatio !in MIN_RING_HOLE_AREA_RATIO..MAX_RING_HOLE_AREA_RATIO) return false

        val outerMoments = Imgproc.moments(outerContour)
        val childMoments = Imgproc.moments(childContour)
        if (outerMoments.m00 == 0.0 || childMoments.m00 == 0.0) return false
        val centerOffset = Point2D(
            (outerMoments.m10 / outerMoments.m00).toFloat(),
            (outerMoments.m01 / outerMoments.m00).toFloat(),
        ).distanceTo(
            Point2D(
                (childMoments.m10 / childMoments.m00).toFloat(),
                (childMoments.m01 / childMoments.m00).toFloat(),
            ),
        )
        return centerOffset <= outerRadius * MAX_RING_CENTER_OFFSET_RADIUS_FRACTION
    }

    /** Fraction of angular samples that encounter selected ink near a proposed circumference. */
    private fun ringInkCoverage(mask: Mat, center: Point2D, radius: Float): Double {
        val samples = 72
        val radialTolerance = (radius * 0.18f).roundToInt().coerceIn(2, 5)
        var covered = 0
        repeat(samples) { sample ->
            val angle = sample * 2.0 * PI / samples
            val hasInk = (-radialTolerance..radialTolerance).any { radialOffset ->
                val sampledRadius = radius + radialOffset
                val x = (center.x + cos(angle) * sampledRadius).roundToInt()
                val y = (center.y + sin(angle) * sampledRadius).roundToInt()
                x in 0 until mask.cols() && y in 0 until mask.rows() &&
                    (mask.get(y, x)?.getOrNull(0) ?: 0.0) > 0.0
            }
            if (hasInk) covered++
        }
        return covered.toDouble() / samples
    }

    private fun diskInkFraction(mask: Mat, center: Point2D, radius: Float): Double {
        val integerRadius = radius.roundToInt().coerceAtLeast(1)
        var total = 0
        var ink = 0
        for (dy in -integerRadius..integerRadius) {
            for (dx in -integerRadius..integerRadius) {
                if (dx * dx + dy * dy > integerRadius * integerRadius) continue
                val x = center.x.roundToInt() + dx
                val y = center.y.roundToInt() + dy
                if (x !in 0 until mask.cols() || y !in 0 until mask.rows()) continue
                total++
                if ((mask.get(y, x)?.getOrNull(0) ?: 0.0) > 0.0) ink++
            }
        }
        return if (total == 0) 1.0 else ink.toDouble() / total
    }

    private fun pointDistance(first: Point, second: Point): Double {
        val dx = first.x - second.x
        val dy = first.y - second.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun orderedBoundary(points: List<Point>): MapBoundary {
        require(points.size == 4)
        val centerX = points.sumOf { it.x } / points.size
        val centerY = points.sumOf { it.y } / points.size
        val clockwise = points.sortedBy { atan2(it.y - centerY, it.x - centerX) }
        val firstIndex = clockwise.indices.minBy { index ->
            clockwise[index].x + clockwise[index].y
        }
        val ordered = List(4) { offset -> clockwise[(firstIndex + offset) % 4] }

        return MapBoundary(
            topLeft = ordered[0].toDomainPoint(),
            topRight = ordered[1].toDomainPoint(),
            bottomRight = ordered[2].toDomainPoint(),
            bottomLeft = ordered[3].toDomainPoint(),
        )
    }

    private fun Point.toDomainPoint() = Point2D(x.toFloat(), y.toFloat())

    private fun requireOpenCv() {
        check(isOpenCvLoaded) { "OpenCV native library could not be loaded" }
    }

    private data class SymbolCandidate(
        val center: Point2D,
        val radius: Float,
        val shape: SymbolShape,
        val ringEvidence: RingEvidence,
        val shapeConfidence: Double,
        val ringMultiplicity: Int,
    )

    private data class SamplePixel(
        val x: Int,
        val y: Int,
        val hue: Double,
        val saturation: Double,
        val value: Double,
    )

    private enum class SymbolShape { CIRCLE, TRIANGLE }
    private enum class RingEvidence { MATCH, MISMATCH, NONE }
}

data class DetectedControlSymbol(
    val center: Point2D,
    val radius: Float,
    val type: ControlPointType,
)

data class ColorCalibrationSample(
    val hueRange: ClosedFloatingPointRange<Double>,
    val saturationRange: ClosedFloatingPointRange<Double>,
    val valueRange: ClosedFloatingPointRange<Double>,
    val estimatedRadius: Float,
)
