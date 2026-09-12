package com.orientesanasrekinatajs.domain.model

import java.util.UUID

enum class RouteRestrictionType {
    BLACKLIST_CONTROL,
    BLACKLIST_CONNECTION,
    MANDATORY_CONTROL,
    MANDATORY_CONNECTION,
}

data class RouteRestriction(
    val type: RouteRestrictionType,
    val firstPointId: String,
    val secondPointId: String? = null,
    val isStarred: Boolean = false,
    val id: String = UUID.randomUUID().toString(),
) {
    val pointIds: Set<String>
        get() = setOfNotNull(firstPointId, secondPointId)
}

/** Each route endpoint has one neighbour; controls and a shared start/finish have two. */
internal val ControlPoint.mandatoryConnectionLimit: Int
    get() = when (type) {
        ControlPointType.START, ControlPointType.FINISH -> 1
        ControlPointType.CONTROL, ControlPointType.START_FINISH -> 2
    }

internal fun mandatoryConnectionCounts(restrictions: List<RouteRestriction>): Map<String, Int> =
    restrictions.filter { it.type == RouteRestrictionType.MANDATORY_CONNECTION }
        .flatMap { it.pointIds }.groupingBy { it }.eachCount()
