package com.orientesanasrekinatajs.domain.model

data class RouteMetadata(
    val name: String = "",
    val isStarred: Boolean = false,
    val order: Int? = null,
    val isHidden: Boolean = false,
    val isDisplayed: Boolean = false,
    val colorIndex: Int = 0,
    val isAlternative: Boolean = false,
    val parentRouteId: String? = null,
)
