package com.orientesanasrekinatajs.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.orientesanasrekinatajs.data.local.dao.MapDao
import com.orientesanasrekinatajs.data.local.entity.ControlPointEntity
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.RouteSegment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class SavedMapDraft(
    val bitmap: Bitmap,
    val pixelsPerMeter: Float,
    val points: List<ControlPoint>,
    val route: OptimizedRoute,
    val selectedRoute: OptimizedRoute = route,
    val routeMode: String = "SHORTEST",
    val routeBudgetMeters: Float? = null,
    val routeTargetScore: Int? = null,
    val lineStart: Point2D?,
    val lineEnd: Point2D?,
    val lineDistanceMeters: Float?,
    val rotationQuarterTurns: Int,
    val existingId: String? = null,
    val name: String? = null,
)

data class SavedMap(
    val id: String,
    val name: String,
    val bitmap: Bitmap,
    val pixelsPerMeter: Float,
    val points: List<ControlPoint>,
    val route: OptimizedRoute,
    val selectedRoutePointIds: List<String>,
    val routeMode: String,
    val routeBudgetMeters: Float?,
    val routeTargetScore: Int?,
    val lineStart: Point2D?,
    val lineEnd: Point2D?,
    val lineDistanceMeters: Float?,
    val rotationQuarterTurns: Int,
)

/** Owns the image-file and Room parts of saving and reopening a processed route. */
class SavedMapRepository(
    private val mapsDirectory: File,
    private val dao: MapDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    val maps: Flow<List<ScannedMapEntity>> = dao.observeAllMaps()

    suspend fun save(draft: SavedMapDraft): ScannedMapEntity = withContext(ioDispatcher) {
        require(draft.pixelsPerMeter.isFinite() && draft.pixelsPerMeter > 0f)
        check(mapsDirectory.exists() || mapsDirectory.mkdirs()) { "Could not create the maps folder" }
        val previous = draft.existingId?.let { dao.getMap(it) }
        val mapId = previous?.id ?: UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        val imageFile = File(mapsDirectory, "$mapId-$timestamp.png")
        val persistedPoints = draft.points.map { point ->
            point to ControlPointEntity(
                id = UUID.randomUUID().toString(),
                mapId = mapId,
                code = point.code,
                points = point.points,
                x = point.center.x,
                y = point.center.y,
                type = point.type.name,
            )
        }
        val persistedIds = persistedPoints.associate { (domain, entity) -> domain.id to entity.id }
        val routeIds = draft.route.path.mapNotNull { persistedIds[it.id] }.joinToString(",")
        val selectedRouteIds = draft.selectedRoute.path
            .mapNotNull { persistedIds[it.id] }
            .joinToString(",")
        val map = ScannedMapEntity(
            id = mapId,
            timestamp = timestamp,
            imageFilePath = imageFile.absolutePath,
            pixelsPerMeter = draft.pixelsPerMeter,
            name = draft.name?.trim()?.takeIf(String::isNotEmpty)
                ?: previous?.name
                ?: defaultName(timestamp),
            calibrationStartX = draft.lineStart?.x,
            calibrationStartY = draft.lineStart?.y,
            calibrationEndX = draft.lineEnd?.x,
            calibrationEndY = draft.lineEnd?.y,
            lineDistanceMeters = draft.lineDistanceMeters,
            routePointIds = routeIds,
            selectedRoutePointIds = selectedRouteIds,
            routeMode = draft.routeMode,
            routeBudgetMeters = draft.routeBudgetMeters,
            routeTargetScore = draft.routeTargetScore,
            routeTotalDistanceMeters = draft.route.totalDistanceMeters,
            routeTotalScore = draft.route.totalScore,
            rotationQuarterTurns = draft.rotationQuarterTurns.mod(4),
        )
        try {
            imageFile.outputStream().buffered().use { output ->
                check(draft.bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not save the map image"
                }
            }
            dao.replaceMapWithPoints(map, persistedPoints.map { it.second })
            previous?.imageFilePath?.takeIf { it != imageFile.absolutePath }?.let(::File)?.delete()
            map
        } catch (error: Throwable) {
            imageFile.delete()
            throw error
        }
    }

    suspend fun load(id: String): SavedMap = withContext(ioDispatcher) {
        val stored = requireNotNull(dao.getMapWithPoints(id)) { "Saved map not found" }
        val bitmap = requireNotNull(BitmapFactory.decodeFile(stored.map.imageFilePath)) {
            "Saved map image could not be opened"
        }
        val domainById = stored.points.associate { point ->
            point.id to ControlPoint(
                id = point.id,
                code = point.code,
                points = point.points,
                center = Point2D(point.x, point.y),
                type = ControlPointType.valueOf(point.type),
            )
        }
        val points = domainById.values.toList()
        val routePath = stored.map.routePointIds.split(',')
            .filter(String::isNotBlank)
            .mapNotNull(domainById::get)
            .ifEmpty { legacyRoutePath(points) }
        val selectedRoutePointIds = stored.map.selectedRoutePointIds.split(',')
            .filter(String::isNotBlank)
            .takeIf(List<String>::isNotEmpty)
            ?: routePath.map(ControlPoint::id)
        SavedMap(
            id = stored.map.id,
            name = stored.map.name,
            bitmap = bitmap,
            pixelsPerMeter = stored.map.pixelsPerMeter,
            points = points,
            route = buildRoute(
                routePath,
                stored.map.pixelsPerMeter,
                stored.map.routeTotalDistanceMeters,
                stored.map.routeTotalScore,
            ),
            selectedRoutePointIds = selectedRoutePointIds,
            routeMode = stored.map.routeMode,
            routeBudgetMeters = stored.map.routeBudgetMeters,
            routeTargetScore = stored.map.routeTargetScore,
            lineStart = stored.map.calibrationStartX?.let { x ->
                stored.map.calibrationStartY?.let { y -> Point2D(x, y) }
            },
            lineEnd = stored.map.calibrationEndX?.let { x ->
                stored.map.calibrationEndY?.let { y -> Point2D(x, y) }
            },
            lineDistanceMeters = stored.map.lineDistanceMeters,
            rotationQuarterTurns = stored.map.rotationQuarterTurns,
        )
    }

    suspend fun rename(id: String, name: String): ScannedMapEntity = withContext(ioDispatcher) {
        val current = requireNotNull(dao.getMap(id)) { "Saved map not found" }
        val renamed = current.copy(name = name.trim().takeIf(String::isNotEmpty) ?: current.name)
        dao.updateMap(renamed)
        renamed
    }

    suspend fun delete(id: String) = withContext(ioDispatcher) {
        val map = dao.getMap(id) ?: return@withContext
        dao.deleteMap(id)
        File(map.imageFilePath).delete()
    }

    suspend fun clearAll() = withContext(ioDispatcher) {
        val stored = dao.getAllMaps()
        dao.deleteAllMaps()
        stored.forEach { File(it.imageFilePath).delete() }
    }

    private fun buildRoute(
        path: List<ControlPoint>,
        pixelsPerMeter: Float,
        storedDistance: Float,
        storedScore: Int,
    ): OptimizedRoute {
        var accumulatedDistance = 0f
        var accumulatedScore = 0
        val segments = path.zipWithNext { from, to ->
            val distance = hypot(to.center.x - from.center.x, to.center.y - from.center.y) / pixelsPerMeter
            accumulatedDistance += distance
            if (to.type == ControlPointType.CONTROL) accumulatedScore += to.points
            RouteSegment(from, to, distance, accumulatedDistance, accumulatedScore)
        }
        return OptimizedRoute(
            path = path,
            totalDistanceMeters = storedDistance.takeIf { it > 0f } ?: accumulatedDistance,
            totalScore = storedScore.takeIf { it > 0 } ?: accumulatedScore,
            segments = segments,
        )
    }

    private fun legacyRoutePath(points: List<ControlPoint>): List<ControlPoint> {
        val start = points.firstOrNull { it.type == ControlPointType.START }
            ?: points.firstOrNull { it.type == ControlPointType.START_FINISH }
        val finish = points.firstOrNull { it.type == ControlPointType.FINISH }
            ?: points.firstOrNull { it.type == ControlPointType.START_FINISH }
        val controls = points.filter { it.type == ControlPointType.CONTROL }.sortedBy { it.code }
        return listOfNotNull(start) + controls + listOfNotNull(finish)
    }

    private fun defaultName(timestamp: Long): String =
        "Route ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))}"

    companion object {
        fun create(context: Context): SavedMapRepository = SavedMapRepository(
            mapsDirectory = File(context.filesDir, "saved_maps"),
            dao = OrienteeringDatabase.getInstance(context).mapDao(),
        )
    }
}
