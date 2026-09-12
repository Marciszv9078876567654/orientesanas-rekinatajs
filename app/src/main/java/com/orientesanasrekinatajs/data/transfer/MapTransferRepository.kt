package com.orientesanasrekinatajs.data.transfer

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.orientesanasrekinatajs.data.local.SavedMap
import com.orientesanasrekinatajs.data.local.SavedMapDraft
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.domain.model.RouteRestriction
import com.orientesanasrekinatajs.domain.model.RouteRestrictionType
import com.orientesanasrekinatajs.domain.model.RouteSegment
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

data class PdfRouteLayer(
    val points: List<ControlPoint>,
    val colorIndex: Int,
    val isActive: Boolean,
    val isPinned: Boolean,
    val strokeWidth: Float = 4f,
)

data class PdfMapDraft(
    val bitmap: Bitmap,
    val points: List<ControlPoint>,
    val routes: List<PdfRouteLayer>,
    val rotationDegrees: Float,
    val imageQuality: Int = 80,
)

class MapTransferRepository(
    private val contentResolver: ContentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun export(uri: Uri, draft: SavedMapDraft, includeRoutes: Boolean) =
        withContext(ioDispatcher) {
            val manifest = createManifest(draft, includeRoutes).toString().toByteArray(Charsets.UTF_8)
            contentResolver.openOutputStream(uri, "w")?.buffered()?.use { output ->
                ZipOutputStream(output).use { zip ->
                    zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    zip.write(manifest)
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry(MAP_IMAGE_ENTRY))
                    check(draft.bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, zip)) {
                        "Could not encode the map image"
                    }
                    zip.closeEntry()
                }
            } ?: error("Could not open the export destination")
        }

    suspend fun exportPdf(uri: Uri, draft: PdfMapDraft) = withContext(ioDispatcher) {
        val radians = Math.toRadians(draft.rotationDegrees.toDouble())
        val imageWidth = draft.bitmap.width * abs(cos(radians)).toFloat() +
            draft.bitmap.height * abs(sin(radians)).toFloat()
        val imageHeight = draft.bitmap.width * abs(sin(radians)).toFloat() +
            draft.bitmap.height * abs(cos(radians)).toFloat()
        val pageScale = minOf(1f, PDF_MAX_DIMENSION / maxOf(
            imageWidth,
            imageHeight,
        ))
        val pageWidth = (imageWidth * pageScale).toInt().coerceAtLeast(1)
        val pageHeight = (imageHeight * pageScale).toInt().coerceAtLeast(1)
        val pdfBitmap = compressedPdfBitmap(draft.bitmap, draft.imageQuality, pageScale)
        val document = PdfDocument()
        try {
            val page = document.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create(),
            )
            drawPdfMap(page.canvas, draft, pdfBitmap, imageWidth, imageHeight, pageScale)
            document.finishPage(page)
            contentResolver.openOutputStream(uri, "w")?.use(document::writeTo)
                ?: error("Could not open the PDF destination")
        } finally {
            document.close()
            pdfBitmap.recycle()
        }
    }

    private fun compressedPdfBitmap(source: Bitmap, quality: Int, pageScale: Float): Bitmap {
        val safeQuality = quality.coerceIn(MIN_PDF_QUALITY, 100)
        val qualityScale = 0.35f + safeQuality / 100f * 0.65f
        val bitmapScale = minOf(1f, pageScale * qualityScale)
        val scaled = Bitmap.createScaledBitmap(
            source,
            (source.width * bitmapScale).toInt().coerceAtLeast(1),
            (source.height * bitmapScale).toInt().coerceAtLeast(1),
            true,
        )
        val opaque = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        Canvas(opaque).run {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        if (scaled !== source) scaled.recycle()
        val encoded = ByteArrayOutputStream()
        check(opaque.compress(Bitmap.CompressFormat.JPEG, safeQuality, encoded)) {
            "Could not compress the PDF map image"
        }
        opaque.recycle()
        return requireNotNull(
            BitmapFactory.decodeByteArray(encoded.toByteArray(), 0, encoded.size()),
        ) { "Could not decode the compressed PDF map image" }
    }

    private fun drawPdfMap(
        canvas: Canvas,
        draft: PdfMapDraft,
        mapBitmap: Bitmap,
        pageWidth: Float,
        pageHeight: Float,
        pageScale: Float,
    ) {
        canvas.drawColor(Color.WHITE)
        canvas.save()
        canvas.scale(pageScale, pageScale)
        val offsetX = (pageWidth - draft.bitmap.width) / 2f
        val offsetY = (pageHeight - draft.bitmap.height) / 2f
        val centerX = pageWidth / 2f
        val centerY = pageHeight / 2f
        val visualScale = 1f / pageScale

        canvas.save()
        canvas.rotate(draft.rotationDegrees, centerX, centerY)
        canvas.drawBitmap(
            mapBitmap,
            null,
            android.graphics.RectF(
                offsetX,
                offsetY,
                offsetX + draft.bitmap.width,
                offsetY + draft.bitmap.height,
            ),
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
        )
        draft.routes.filter { it.isActive }.forEach { layer ->
            drawPdfRoute(
                canvas, layer, offsetX, offsetY, 1f, 7f * visualScale,
                dashScale = visualScale, dashed = false,
            )
        }
        draft.routes.filter { it.isPinned && !it.isActive }.asReversed().forEach { layer ->
            drawPdfRoute(
                canvas, layer, offsetX, offsetY, 1f, layer.strokeWidth * visualScale,
                dashScale = visualScale, dashed = true,
            )
        }
        canvas.restore()

        val visitedIds = draft.routes.flatMap(PdfRouteLayer::points).mapTo(mutableSetOf()) { it.id }
        val highlightedColors = buildMap {
            draft.routes.filter(PdfRouteLayer::isActive).forEach { layer ->
                layer.points.forEach { put(it.id, routeColor(layer.colorIndex)) }
            }
            draft.routes.filter(PdfRouteLayer::isPinned).forEach { layer ->
                layer.points.forEach { put(it.id, routeColor(layer.colorIndex)) }
            }
        }
        draft.points.forEach { point ->
            val screen = transformedPoint(
                offsetX + point.center.x,
                offsetY + point.center.y,
                centerX,
                centerY,
                draft.rotationDegrees,
                1f,
                0f,
                0f,
            )
            val muted = point.id !in visitedIds
            val markerColor = if (!muted && point.type == ControlPointType.CONTROL) {
                highlightedColors[point.id] ?: typeColor(point.type)
            } else if (muted) Color.rgb(122, 122, 122) else typeColor(point.type)
            val pointScale = visualScale
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = if (muted) 166 else 255
            }
            canvas.drawCircle(screen.first, screen.second, 13f * pointScale, fill)
            fill.color = markerColor
            fill.alpha = 255
            canvas.drawCircle(screen.first, screen.second, 9f * pointScale, fill)
            val label = when (point.type) {
                ControlPointType.START -> "S"
                ControlPointType.FINISH -> "F"
                ControlPointType.START_FINISH -> "S/F"
                ControlPointType.CONTROL -> point.code.toString()
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(0, 73, 87)
                textSize = 18f * pointScale
                isFakeBoldText = true
                alpha = if (muted) 125 else 255
            }
            val outline = Paint(labelPaint).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 3f
                alpha = if (muted) 150 else 255
            }
            val labelX = screen.first + 13f * pointScale
            val labelY = screen.second - 10f * pointScale
            canvas.drawText(label, labelX, labelY, outline)
            canvas.drawText(label, labelX, labelY, labelPaint)
        }
        canvas.restore()
    }

    private fun drawPdfRoute(
        canvas: Canvas,
        layer: PdfRouteLayer,
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        strokeWidth: Float,
        dashScale: Float,
        dashed: Boolean,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = routeColor(layer.colorIndex)
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
            if (dashed) {
                alpha = 209
                pathEffect = DashPathEffect(
                    when (layer.colorIndex.mod(4)) {
                        0 -> floatArrayOf(18f, 12f)
                        1 -> floatArrayOf(2f, 10f)
                        2 -> floatArrayOf(18f, 8f, 2f, 8f)
                        else -> floatArrayOf(28f, 10f)
                    }.map { it * dashScale }.toFloatArray(),
                    0f,
                )
            }
        }
        layer.points.zipWithNext().forEach { (start, end) ->
            canvas.drawLine(
                offsetX + start.center.x * scale,
                offsetY + start.center.y * scale,
                offsetX + end.center.x * scale,
                offsetY + end.center.y * scale,
                paint,
            )
        }
    }

    private fun transformedPoint(
        x: Float,
        y: Float,
        centerX: Float,
        centerY: Float,
        degrees: Float,
        zoom: Float,
        panX: Float,
        panY: Float,
    ): Pair<Float, Float> {
        val radians = Math.toRadians(degrees.toDouble())
        val dx = x - centerX
        val dy = y - centerY
        val rotatedX = dx * cos(radians).toFloat() - dy * sin(radians).toFloat()
        val rotatedY = dx * sin(radians).toFloat() + dy * cos(radians).toFloat()
        return centerX + rotatedX * zoom + panX to centerY + rotatedY * zoom + panY
    }

    private fun typeColor(type: ControlPointType): Int = when (type) {
        ControlPointType.START -> Color.rgb(46, 125, 50)
        ControlPointType.FINISH -> Color.rgb(198, 40, 40)
        ControlPointType.START_FINISH -> Color.rgb(106, 27, 154)
        ControlPointType.CONTROL -> Color.rgb(233, 30, 99)
    }

    private fun routeColor(index: Int): Int = intArrayOf(
        0xFF1565C0.toInt(), 0xFFD81B60.toInt(), 0xFF00897B.toInt(), 0xFFF57C00.toInt(),
        0xFF7B1FA2.toInt(), 0xFF00ACC1.toInt(), 0xFF558B2F.toInt(), 0xFFE64A19.toInt(),
        0xFF5E35B1.toInt(), 0xFFC0A000.toInt(),
    )[index.mod(10)]

    suspend fun import(uri: Uri): SavedMap = withContext(ioDispatcher) {
        var manifestBytes: ByteArray? = null
        var imageBytes: ByteArray? = null
        contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        MANIFEST_ENTRY -> manifestBytes = zip.readEntryLimited(MAX_MANIFEST_BYTES)
                        MAP_IMAGE_ENTRY, LEGACY_MAP_IMAGE_ENTRY -> imageBytes = zip.readEntryLimited(MAX_IMAGE_BYTES)
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Could not open the imported map")
        val manifest = JSONObject(
            requireNotNull(manifestBytes) { "The map manifest is missing" }.toString(Charsets.UTF_8),
        )
        require(manifest.optString("format") == FORMAT_NAME && manifest.optInt("version") == FORMAT_VERSION) {
            "Unsupported map export format"
        }
        val bitmap = requireNotNull(
            BitmapFactory.decodeByteArray(
                requireNotNull(imageBytes) { "The map image is missing" },
                0,
                imageBytes!!.size,
            ),
        ) { "The exported map image is invalid" }
        decodeManifest(manifest, bitmap)
    }

    private fun createManifest(draft: SavedMapDraft, includeRoutes: Boolean): JSONObject =
        JSONObject().apply {
            put("format", FORMAT_NAME)
            put("version", FORMAT_VERSION)
            put("name", draft.name.orEmpty())
            put("pixelsPerMeter", draft.pixelsPerMeter)
            put("rotationQuarterTurns", draft.rotationQuarterTurns.mod(4))
            putPoint("lineStart", draft.lineStart)
            putPoint("lineEnd", draft.lineEnd)
            draft.lineDistanceMeters?.let { put("lineDistanceMeters", it) }
            put("points", JSONArray().apply {
                draft.points.forEach { point -> put(point.toJson()) }
            })
            val routes = if (includeRoutes) listOfNotNull(draft.route) + draft.alternativeRoutes else emptyList()
            put("routes", JSONArray().apply {
                routes.forEach { route ->
                    put(JSONObject().apply {
                        put("id", route.id)
                        put("path", JSONArray(route.path.map(ControlPoint::id)))
                    })
                }
            })
            put("selectedRouteId", if (includeRoutes) draft.selectedRoute?.id.orEmpty() else "")
            put("detailsPointsPerKilometer", draft.detailsPointsPerKilometer)
            put("detailsRelativeValues", draft.detailsRelativeValues)
            put("detailsPointNumbering", draft.detailsPointNumbering)
            put("routeMode", draft.routeMode)
            draft.routeBudgetMeters?.let { put("routeBudgetMeters", it) }
            draft.routeTargetScore?.let { put("routeTargetScore", it) }
            put("routeMetadata", JSONObject().apply {
                if (includeRoutes) routes.forEach { route ->
                    draft.routeMetadata[route.id]?.let { put(route.id, it.toJson()) }
                }
            })
            put("routeRestrictions", JSONArray().apply {
                if (includeRoutes) draft.routeRestrictions.forEach { put(it.toJson()) }
            })
        }

    private fun decodeManifest(manifest: JSONObject, bitmap: Bitmap): SavedMap {
        val scale = manifest.getDouble("pixelsPerMeter").toFloat()
        require(scale.isFinite() && scale > 0f) { "The exported map scale is invalid" }
        val pointsArray = manifest.getJSONArray("points")
        val points = buildList {
            for (index in 0 until pointsArray.length()) add(pointsArray.getJSONObject(index).toPoint())
        }
        require(points.map(ControlPoint::id).distinct().size == points.size) {
            "The exported map contains duplicate point IDs"
        }
        val pointsById = points.associateBy(ControlPoint::id)
        val routesArray = manifest.optJSONArray("routes") ?: JSONArray()
        val routes = buildList {
            for (index in 0 until routesArray.length()) {
                val value = routesArray.getJSONObject(index)
                val pathIds = value.getJSONArray("path")
                val path = buildList {
                    for (pathIndex in 0 until pathIds.length()) {
                        add(requireNotNull(pointsById[pathIds.getString(pathIndex)]) {
                            "A route refers to a missing point"
                        })
                    }
                }
                require(path.size >= 2) { "An exported route has fewer than two points" }
                add(buildRoute(value.getString("id"), path, scale))
            }
        }
        val primary = routes.firstOrNull()
        val metadataObject = manifest.optJSONObject("routeMetadata") ?: JSONObject()
        val metadata = routes.associateNotNull { route ->
            metadataObject.optJSONObject(route.id)?.let { route.id to it.toRouteMetadata() }
        }
        val restrictionsArray = manifest.optJSONArray("routeRestrictions") ?: JSONArray()
        val restrictions = buildList {
            for (index in 0 until restrictionsArray.length()) {
                add(restrictionsArray.getJSONObject(index).toRouteRestriction())
            }
        }
        val selectedId = manifest.optString("selectedRouteId").takeIf { id -> routes.any { it.id == id } }
            ?: primary?.id
        val selected = routes.firstOrNull { it.id == selectedId }
        return SavedMap(
            id = "",
            name = manifest.optString("name").ifBlank { "Imported map" },
            bitmap = bitmap,
            pixelsPerMeter = scale,
            points = points,
            route = primary,
            alternativeRoutes = routes.drop(1),
            routeMetadata = metadata,
            routeRestrictions = restrictions,
            selectedRoutePointIds = selected?.path?.map(ControlPoint::id).orEmpty(),
            selectedRouteId = selectedId,
            detailsPointsPerKilometer = manifest.optBoolean("detailsPointsPerKilometer"),
            detailsRelativeValues = manifest.optBoolean("detailsRelativeValues"),
            detailsPointNumbering = manifest.optBoolean("detailsPointNumbering"),
            routeMode = manifest.optString("routeMode", "SHORTEST"),
            routeBudgetMeters = manifest.optDoubleOrNull("routeBudgetMeters")?.toFloat(),
            routeTargetScore = manifest.optIntOrNull("routeTargetScore"),
            lineStart = manifest.optPoint("lineStart"),
            lineEnd = manifest.optPoint("lineEnd"),
            lineDistanceMeters = manifest.optDoubleOrNull("lineDistanceMeters")?.toFloat(),
            rotationQuarterTurns = manifest.optInt("rotationQuarterTurns").mod(4),
        )
    }

    private fun buildRoute(id: String, path: List<ControlPoint>, scale: Float): OptimizedRoute {
        var distanceTotal = 0f
        var scoreTotal = 0
        val segments = path.zipWithNext { from, to ->
            val distance = hypot(to.center.x - from.center.x, to.center.y - from.center.y) / scale
            distanceTotal += distance
            if (to.type == ControlPointType.CONTROL) scoreTotal += to.points
            RouteSegment(from, to, distance, distanceTotal, scoreTotal)
        }
        return OptimizedRoute(path, distanceTotal, scoreTotal, segments, id)
    }

    private fun ZipInputStream.readEntryLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "The imported map file is too large" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun JSONObject.putPoint(name: String, point: Point2D?) {
        point?.let { put(name, JSONObject().put("x", it.x).put("y", it.y)) }
    }

    private fun JSONObject.optPoint(name: String): Point2D? = optJSONObject(name)?.let {
        Point2D(it.getDouble("x").toFloat(), it.getDouble("y").toFloat())
    }

    private fun ControlPoint.toJson() = JSONObject()
        .put("id", id).put("code", code).put("points", points)
        .put("x", center.x).put("y", center.y).put("type", type.name)
        .put("needsReview", needsReview)

    private fun JSONObject.toPoint() = ControlPoint(
        id = getString("id"), code = getInt("code"), points = getInt("points"),
        center = Point2D(getDouble("x").toFloat(), getDouble("y").toFloat()),
        type = ControlPointType.valueOf(getString("type")),
        // Optional keeps imports from the existing manifest version backward compatible.
        needsReview = optBoolean("needsReview", false),
    )

    private fun RouteMetadata.toJson() = JSONObject()
        .put("name", name).put("starred", isStarred).put("hidden", isHidden)
        .put("displayed", isDisplayed).put("colorIndex", colorIndex)
        .put("alternative", isAlternative).apply {
            order?.let { put("order", it) }
            parentRouteId?.let { put("parentRouteId", it) }
        }

    private fun JSONObject.toRouteMetadata() = RouteMetadata(
        name = optString("name"), isStarred = optBoolean("starred"),
        order = optInt("order", -1).takeIf { it >= 0 }, isHidden = optBoolean("hidden"),
        isDisplayed = optBoolean("displayed"), colorIndex = optInt("colorIndex"),
        isAlternative = optBoolean("alternative"),
        parentRouteId = optString("parentRouteId").takeIf(String::isNotBlank),
    )

    private fun RouteRestriction.toJson() = JSONObject()
        .put("id", id).put("type", type.name).put("firstPointId", firstPointId)
        .put("starred", isStarred).apply { secondPointId?.let { put("secondPointId", it) } }

    private fun JSONObject.toRouteRestriction() = RouteRestriction(
        id = getString("id"), type = RouteRestrictionType.valueOf(getString("type")),
        firstPointId = getString("firstPointId"),
        secondPointId = optString("secondPointId").takeIf(String::isNotBlank),
        isStarred = optBoolean("starred"),
    )

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (has(name) && !isNull(name)) getDouble(name) else null

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (has(name) && !isNull(name)) getInt(name) else null

    private inline fun <T, K, V> Iterable<T>.associateNotNull(
        transform: (T) -> Pair<K, V>?,
    ): Map<K, V> = buildMap {
        this@associateNotNull.forEach { item -> transform(item)?.let { put(it.first, it.second) } }
    }

    companion object {
        const val FILE_EXTENSION = "ormap"
        const val MIME_TYPE = "application/zip"
        const val PDF_MIME_TYPE = "application/pdf"
        private const val PDF_MAX_DIMENSION = 2_000f
        private const val MIN_PDF_QUALITY = 10
        private const val FORMAT_NAME = "orienteering-map"
        private const val FORMAT_VERSION = 1
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val MAP_IMAGE_ENTRY = "map.webp"
        private const val LEGACY_MAP_IMAGE_ENTRY = "map.png"
        private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 64 * 1024 * 1024

        fun create(context: Context) = MapTransferRepository(context.contentResolver)
    }
}
