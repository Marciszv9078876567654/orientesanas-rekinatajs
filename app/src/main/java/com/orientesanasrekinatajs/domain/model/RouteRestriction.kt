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
