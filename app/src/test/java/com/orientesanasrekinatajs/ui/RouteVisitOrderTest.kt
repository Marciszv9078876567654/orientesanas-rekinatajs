package com.orientesanasrekinatajs.ui

import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.ui.components.routeVisitOrderLabels
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteVisitOrderTest {
    @Test
    fun collectionOrderFollowsRouteAndDoesNotCountEndpoints() {
        val start = point("start", ControlPointType.START_FINISH)
        val first = point("65")
        val second = point("31")
        assertEquals(mapOf("65" to "1", "31" to "2"),
            routeVisitOrderLabels(listOf(start, first, second, start)))
        assertEquals(mapOf("31" to "1", "65" to "2"),
            routeVisitOrderLabels(listOf(start, second, first, start)))
    }

    @Test
    fun repeatedVisitsKeepEveryPosition() {
        val first = point("65")
        val second = point("31")
        assertEquals(mapOf("65" to "1/3", "31" to "2"),
            routeVisitOrderLabels(listOf(first, second, first)))
        assertEquals(emptyMap<String, String>(), routeVisitOrderLabels(emptyList()))
    }

    private fun point(id: String, type: ControlPointType = ControlPointType.CONTROL) =
        ControlPoint(id = id, code = id.toIntOrNull() ?: 0, center = Point2D(0f, 0f), type = type)
}
