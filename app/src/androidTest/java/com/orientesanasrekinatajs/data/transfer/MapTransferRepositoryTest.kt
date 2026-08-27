package com.orientesanasrekinatajs.data.transfer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.orientesanasrekinatajs.data.local.SavedMapDraft
import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.OptimizedRoute
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.RouteMetadata
import com.orientesanasrekinatajs.domain.model.RouteRestriction
import com.orientesanasrekinatajs.domain.model.RouteRestrictionType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapTransferRepositoryTest {
    @Test
    fun exportAndImport_preservesMapPointsRoutesAndRestrictions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = MapTransferRepository.create(context)
        val target = File(context.cacheDir, "transfer-${System.nanoTime()}.ormap")
        val start = ControlPoint("start", 0, 0, Point2D(1f, 1f), ControlPointType.START)
        val control = ControlPoint("control", 65, 6, Point2D(8f, 5f), ControlPointType.CONTROL)
        val finish = ControlPoint("finish", 0, 0, Point2D(15f, 9f), ControlPointType.FINISH)
        val route = OptimizedRoute(listOf(start, control, finish), 12f, 6, emptyList(), "route")
        val draft = SavedMapDraft(
            bitmap = Bitmap.createBitmap(20, 12, Bitmap.Config.ARGB_8888),
            pixelsPerMeter = 2f,
            points = listOf(start, control, finish),
            route = route,
            routeMetadata = mapOf(route.id to RouteMetadata(name = "Blue route", colorIndex = 3)),
            routeRestrictions = listOf(
                RouteRestriction(
                    type = RouteRestrictionType.MANDATORY_CONTROL,
                    firstPointId = control.id,
                    isStarred = true,
                ),
            ),
            lineStart = Point2D(1f, 2f),
            lineEnd = Point2D(11f, 2f),
            lineDistanceMeters = 5f,
            rotationQuarterTurns = 1,
            name = "Exported map",
        )

        try {
            repository.export(Uri.fromFile(target), draft, includeRoutes = true)
            val imported = repository.import(Uri.fromFile(target))

            assertEquals("Exported map", imported.name)
            assertEquals(3, imported.points.size)
            assertEquals(listOf("start", "control", "finish"), imported.route?.path?.map(ControlPoint::id))
            assertEquals("Blue route", imported.routeMetadata.getValue("route").name)
            assertEquals(3, imported.routeMetadata.getValue("route").colorIndex)
            assertTrue(imported.routeRestrictions.single().isStarred)
            assertEquals(control.id, imported.routeRestrictions.single().firstPointId)
            assertEquals(1, imported.rotationQuarterTurns)
        } finally {
            target.delete()
        }
    }

    @Test
    fun mapOnlyExport_keepsPointsAndOmitsAllRouteData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = MapTransferRepository.create(context)
        val target = File(context.cacheDir, "map-only-${System.nanoTime()}.ormap")
        val start = ControlPoint("start", 0, 0, Point2D(1f, 1f), ControlPointType.START)
        val finish = ControlPoint("finish", 0, 0, Point2D(15f, 9f), ControlPointType.FINISH)
        val route = OptimizedRoute(listOf(start, finish), 8f, 0, emptyList(), "route")
        val draft = SavedMapDraft(
            bitmap = Bitmap.createBitmap(20, 12, Bitmap.Config.ARGB_8888),
            pixelsPerMeter = 2f,
            points = listOf(start, finish),
            route = route,
            routeMetadata = mapOf(route.id to RouteMetadata(name = "Route")),
            routeRestrictions = listOf(
                RouteRestriction(RouteRestrictionType.BLACKLIST_CONNECTION, start.id, finish.id),
            ),
            lineStart = null,
            lineEnd = null,
            lineDistanceMeters = null,
            rotationQuarterTurns = 0,
        )

        try {
            repository.export(Uri.fromFile(target), draft, includeRoutes = false)
            val imported = repository.import(Uri.fromFile(target))

            assertEquals(2, imported.points.size)
            assertNull(imported.route)
            assertTrue(imported.alternativeRoutes.isEmpty())
            assertTrue(imported.routeMetadata.isEmpty())
            assertTrue(imported.routeRestrictions.isEmpty())
        } finally {
            target.delete()
        }
    }
}
