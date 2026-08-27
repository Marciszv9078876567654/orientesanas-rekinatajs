package com.orientesanasrekinatajs.data.transfer

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import org.json.JSONArray
import org.json.JSONObject

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
                    check(draft.bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)) {
                        "Could not encode the map image"
                    }
                    zip.closeEntry()
                }
            } ?: error("Could not open the export destination")
        }

    suspend fun import(uri: Uri): SavedMap = withContext(ioDispatcher) {
        var manifestBytes: ByteArray? = null
        var imageBytes: ByteArray? = null
        contentResolver.openInputStream(uri)?.buffered()?.use { input ->
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when (entry.name) {
                        MANIFEST_ENTRY -> manifestBytes = zip.readEntryLimited(MAX_MANIFEST_BYTES)
                        MAP_IMAGE_ENTRY -> imageBytes = zip.readEntryLimited(MAX_IMAGE_BYTES)
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

    private fun JSONObject.toPoint() = ControlPoint(
        id = getString("id"), code = getInt("code"), points = getInt("points"),
        center = Point2D(getDouble("x").toFloat(), getDouble("y").toFloat()),
        type = ControlPointType.valueOf(getString("type")),
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
        private const val FORMAT_NAME = "orienteering-map"
        private const val FORMAT_VERSION = 1
        private const val MANIFEST_ENTRY = "manifest.json"
        private const val MAP_IMAGE_ENTRY = "map.png"
        private const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 64 * 1024 * 1024

        fun create(context: Context) = MapTransferRepository(context.contentResolver)
    }
}
