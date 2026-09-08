package com.orientesanasrekinatajs.ui.components

import com.orientesanasrekinatajs.domain.model.ControlPoint
import com.orientesanasrekinatajs.domain.model.ControlPointType

/** Numbers collected controls from one, retaining every visit when a control is revisited. */
internal fun routeVisitOrderLabels(path: List<ControlPoint>): Map<String, String> = path
    .filter { it.type == ControlPointType.CONTROL }
    .mapIndexed { index, point -> point.id to (index + 1) }
    .groupBy({ it.first }, { it.second })
    .mapValues { (_, visits) -> visits.joinToString("/") }
