package com.orientesanasrekinatajs.data.local

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orientesanasrekinatajs.data.local.entity.ControlPointEntity
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.domain.model.RouteRestriction
import com.orientesanasrekinatajs.domain.model.RouteRestrictionType
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapDaoTest {
    private lateinit var database: OrienteeringDatabase
    private lateinit var mapsDirectory: File

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            OrienteeringDatabase::class.java,
        ).allowMainThreadQueries().build()
        mapsDirectory = File(context.cacheDir, "saved-map-repository-test-${System.nanoTime()}")
    }

    @After
    fun closeDatabase() {
        database.close()
        mapsDirectory.deleteRecursively()
    }

    @Test
    fun insertMapWithPointsPersistsRelationship() = runBlocking {
        val map = testMap()
        val point = testPoint(map.id)

        database.mapDao().insertMapWithPoints(map, listOf(point))

        val stored = database.mapDao().getMapWithPoints(map.id)
        assertEquals(map, stored?.map)
        assertEquals(listOf(point), stored?.points)
    }

    @Test
    fun upsertingMapPreservesExistingPoints() = runBlocking {
        val map = testMap()
        val point = testPoint(map.id)
        database.mapDao().insertMapWithPoints(map, listOf(point))

        database.mapDao().insertMap(map.copy(pixelsPerMeter = 3.5f))

        assertEquals(listOf(point), database.mapDao().observePoints(map.id).first())
    }

    @Test
    fun deletingMapCascadesToPoints() = runBlocking {
        val map = testMap()
        database.mapDao().insertMapWithPoints(map, listOf(testPoint(map.id)))

        database.mapDao().deleteMap(map.id)

        assertEquals(emptyList<ControlPointEntity>(), database.mapDao().observePoints(map.id).first())
        assertNull(database.mapDao().getMapWithPoints(map.id))
    }

    @Test
    fun repositorySavesAndReopensBitmapScaleAndPoints() = runBlocking {
        val repository = SavedMapRepository(mapsDirectory, database.mapDao())
        val bitmap = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)
        val point = ControlPoint(code = 65, center = Point2D(8f, 9f), needsReview = true)
        val start = ControlPoint(
            code = 0,
            center = Point2D(1f, 1f),
            type = ControlPointType.START,
        )
        val finish = ControlPoint(
            code = 0,
            center = Point2D(20f, 12f),
            type = ControlPointType.FINISH,
        )
        val primaryRoute = OptimizedRoute(listOf(start, point, finish), 0f, point.points, emptyList())
        val alternativeRoute = OptimizedRoute(listOf(start, finish), 0f, 0, emptyList())

        repository.save(
            SavedMapDraft(
                bitmap = bitmap,
                pixelsPerMeter = 2.5f,
                points = listOf(start, point, finish),
                route = primaryRoute,
                selectedRoute = alternativeRoute,
                detailsPointsPerKilometer = true,
                detailsRelativeValues = true,
                detailsPointNumbering = true,
                alternativeRoutes = listOf(alternativeRoute),
                routeMetadata = mapOf(
                    alternativeRoute.id to RouteMetadata(
                        name = "Direct",
                        isStarred = true,
                        isDisplayed = true,
                        order = 0,
                    ),
                ),
                routeRestrictions = listOf(
                    RouteRestriction(
                        type = RouteRestrictionType.MANDATORY_CONTROL,
                        firstPointId = point.id,
                        isStarred = true,
                    ),
                ),
                lineStart = Point2D(1f, 2f),
                lineEnd = Point2D(11f, 2f),
                lineDistanceMeters = 4f,
                rotationQuarterTurns = 1,
                name = "Training route",
                routeMode = "TARGET_SCORE",
                routeTargetScore = 12,
            ),
        )
        val stored = repository.maps.first().single()
        val reopened = repository.load(stored.id)
        val reopenedRoute = requireNotNull(reopened.route)

        assertEquals(24, reopened.bitmap.width)
        assertEquals(16, reopened.bitmap.height)
        assertEquals(2.5f, reopened.pixelsPerMeter)
        assertEquals(point.code, reopened.points.single { it.type == ControlPointType.CONTROL }.code)
        assertEquals(point.center, reopened.points.single { it.type == ControlPointType.CONTROL }.center)
        assertTrue(reopened.points.single { it.type == ControlPointType.CONTROL }.needsReview)
        assertEquals("Training route", reopened.name)
        assertEquals(Point2D(1f, 2f), reopened.lineStart)
        assertEquals(Point2D(11f, 2f), reopened.lineEnd)
        assertEquals(4f, reopened.lineDistanceMeters)
        assertEquals(1, reopened.rotationQuarterTurns)
        assertEquals(3, reopenedRoute.path.size)
        assertEquals(1, reopened.alternativeRoutes.size)
        assertEquals(2, reopened.alternativeRoutes.single().path.size)
        assertEquals("Direct", reopened.routeMetadata.values.single().name)
        assertTrue(reopened.routeMetadata.values.single().isStarred)
        assertTrue(reopened.routeMetadata.values.single().isDisplayed)
        assertEquals(0, reopened.routeMetadata.values.single().order)
        assertEquals(RouteRestrictionType.MANDATORY_CONTROL, reopened.routeRestrictions.single().type)
        assertEquals(
            reopened.points.single { it.type == ControlPointType.CONTROL }.id,
            reopened.routeRestrictions.single().firstPointId,
        )
        assertTrue(reopened.routeRestrictions.single().isStarred)
        assertEquals(alternativeRoute.id, reopened.selectedRouteId)
        assertEquals(reopened.alternativeRoutes.single().path.map(ControlPoint::id), reopened.selectedRoutePointIds)
        assertTrue(reopened.detailsPointsPerKilometer)
        assertTrue(reopened.detailsRelativeValues)
        assertTrue(reopened.detailsPointNumbering)
        // Older maps have no display-options entry and retain the previous defaults.
        database.mapDao().updateMap(stored.copy(routeMetadataJson = org.json.JSONObject(stored.routeMetadataJson)
            .apply { remove("__routeDisplay") }.toString()))
        val legacy = repository.load(stored.id)
        assertEquals(false, legacy.detailsPointsPerKilometer)
        assertEquals(false, legacy.detailsRelativeValues)
        assertEquals(false, legacy.detailsPointNumbering)
        assertEquals("TARGET_SCORE", reopened.routeMode)
        assertEquals(12, reopened.routeTargetScore)

        assertEquals("Renamed", repository.rename(stored.id, "Renamed").name)
        repository.clearAll()
        assertEquals(emptyList<ScannedMapEntity>(), repository.maps.first())
        assertTrue(!File(stored.imageFilePath).exists())
    }

    @Test
    fun repositorySavesAndReopensMapWithoutRoutes() = runBlocking {
        val repository = SavedMapRepository(mapsDirectory, database.mapDao())
        val bitmap = Bitmap.createBitmap(24, 16, Bitmap.Config.ARGB_8888)

        val stored = repository.save(
            SavedMapDraft(
                bitmap = bitmap,
                pixelsPerMeter = 2.5f,
                points = emptyList(),
                lineStart = Point2D(1f, 2f),
                lineEnd = Point2D(11f, 2f),
                lineDistanceMeters = 4f,
                rotationQuarterTurns = 0,
                name = "Empty map",
            ),
        )
        val reopened = repository.load(stored.id)

        assertEquals("Empty map", reopened.name)
        assertTrue(reopened.points.isEmpty())
        assertNull(reopened.route)
        assertTrue(reopened.alternativeRoutes.isEmpty())
        assertNull(reopened.selectedRouteId)
        assertTrue(reopened.selectedRoutePointIds.isEmpty())
    }

    @Test
    fun migrationOneToEightPreservesExistingDataAndDefaultsReviewFlag() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "migration-${System.nanoTime()}.db"
        context.openOrCreateDatabase(databaseName, Context.MODE_PRIVATE, null).use { legacy ->
            legacy.execSQL(
                "CREATE TABLE scanned_maps (id TEXT NOT NULL, timestamp INTEGER NOT NULL, " +
                    "imageFilePath TEXT NOT NULL, pixelsPerMeter REAL NOT NULL, PRIMARY KEY(id))",
            )
            legacy.execSQL(
                "CREATE TABLE control_points (id TEXT NOT NULL, mapId TEXT NOT NULL, code INTEGER NOT NULL, " +
                    "points INTEGER NOT NULL, x REAL NOT NULL, y REAL NOT NULL, type TEXT NOT NULL, " +
                    "PRIMARY KEY(id), FOREIGN KEY(mapId) REFERENCES scanned_maps(id) ON UPDATE NO ACTION ON DELETE CASCADE)",
            )
            legacy.execSQL("CREATE INDEX index_control_points_mapId ON control_points(mapId)")
            legacy.execSQL("INSERT INTO scanned_maps VALUES ('old', 1, '/old.png', 2.0)")
            legacy.execSQL(
                "INSERT INTO control_points VALUES " +
                    "('point', 'old', 65, 6, 10.0, 20.0, 'CONTROL')",
            )
            legacy.version = 1
        }
        val migrated = Room.databaseBuilder(context, OrienteeringDatabase::class.java, databaseName)
            .addMigrations(
                OrienteeringDatabase.MIGRATION_1_2,
                OrienteeringDatabase.MIGRATION_2_3,
                OrienteeringDatabase.MIGRATION_3_4,
                OrienteeringDatabase.MIGRATION_4_5,
                OrienteeringDatabase.MIGRATION_5_6,
                OrienteeringDatabase.MIGRATION_6_7,
                OrienteeringDatabase.MIGRATION_7_8,
            )
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Route", migrated.mapDao().getMap("old")?.name)
            assertEquals("", migrated.mapDao().getMap("old")?.routePointIds)
            assertEquals("", migrated.mapDao().getMap("old")?.selectedRoutePointIds)
            assertEquals("SHORTEST", migrated.mapDao().getMap("old")?.routeMode)
            assertEquals("", migrated.mapDao().getMap("old")?.alternativeRoutePointIds)
            assertEquals("{}", migrated.mapDao().getMap("old")?.routeMetadataJson)
            assertTrue(!migrated.mapDao().observePoints("old").first().single().needsReview)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun copyingMapPreservesContentAndSurvivesDeletingOriginal() = runBlocking {
        mapsDirectory.mkdirs()
        val image = File(mapsDirectory, "original.png")
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val original = testMap().copy(
            imageFilePath = image.absolutePath,
            routePointIds = "point-1",
            selectedRoutePointIds = "point-1",
            alternativeRoutePointIds = "point-1;point-1",
            routeMetadataJson = """{"route":{"name":"Forest"},"__routeRestrictions":[{"firstPointId":"point-1","secondPointId":"point-1"}]}""",
        )
        database.mapDao().insertMapWithPoints(original, listOf(testPoint(original.id)))
        val repository = SavedMapRepository(mapsDirectory, database.mapDao())

        val copied = repository.copy(original.id, "Forest copy")
        val stored = requireNotNull(database.mapDao().getMapWithPoints(copied.id))
        val point = stored.points.single()
        assertTrue(copied.id != original.id)
        assertTrue(point.id != "point-1")
        assertEquals(copied.id, point.mapId)
        assertEquals("Forest copy", copied.name)
        assertEquals(original.pixelsPerMeter, copied.pixelsPerMeter)
        assertEquals(point.id, copied.routePointIds)
        assertEquals(point.id, copied.selectedRoutePointIds)
        assertEquals("${point.id};${point.id}", copied.alternativeRoutePointIds)
        val metadata = org.json.JSONObject(copied.routeMetadataJson)
        assertEquals("Forest", metadata.getJSONObject("route").getString("name"))
        val restriction = metadata.getJSONArray("__routeRestrictions").getJSONObject(0)
        assertEquals(point.id, restriction.getString("firstPointId"))
        assertEquals(point.id, restriction.getString("secondPointId"))
        assertEquals(original, database.mapDao().getMap(original.id))
        assertTrue(image.readBytes().contentEquals(File(copied.imageFilePath).readBytes()))

        repository.delete(original.id)
        assertTrue(File(copied.imageFilePath).exists())
        assertEquals(listOf(point), database.mapDao().getMapWithPoints(copied.id)?.points)
    }

    private fun testMap() = ScannedMapEntity(
        id = "map-1",
        timestamp = 1L,
        imageFilePath = "/files/map.png",
        pixelsPerMeter = 2.0f,
    )

    private fun testPoint(mapId: String) = ControlPointEntity(
        id = "point-1",
        mapId = mapId,
        code = 65,
        points = 6,
        x = 10.0f,
        y = 20.0f,
        type = "CONTROL",
    )
}
