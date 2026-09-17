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

/** Connections are undirected: A–B and B–A represent the same rule. */
internal fun String.connectionKey(other: String): Pair<String, String> =
    if (this <= other) this to other else other to this

internal fun RouteRestriction.connectionKey(): Pair<String, String> =
    firstPointId.connectionKey(requireNotNull(secondPointId))

internal fun blacklistedControlIds(restrictions: List<RouteRestriction>): Set<String> =
    restrictions.filter { it.type == RouteRestrictionType.BLACKLIST_CONTROL }
        .mapTo(mutableSetOf(), RouteRestriction::firstPointId)

internal fun mandatoryPointIds(restrictions: List<RouteRestriction>): Set<String> =
    restrictions.filter { it.type == RouteRestrictionType.MANDATORY_CONTROL }
        .mapTo(mutableSetOf(), RouteRestriction::firstPointId) +
        restrictions.filter { it.type == RouteRestrictionType.MANDATORY_CONNECTION }.flatMap { it.pointIds }

internal fun blacklistedConnectionKeys(restrictions: List<RouteRestriction>): Set<Pair<String, String>> =
    restrictions.filter { it.type == RouteRestrictionType.BLACKLIST_CONNECTION }
        .mapTo(mutableSetOf()) { it.connectionKey() }

internal fun mandatoryConnectionKeys(restrictions: List<RouteRestriction>): Set<Pair<String, String>> =
    restrictions.filter { it.type == RouteRestrictionType.MANDATORY_CONNECTION }
        .mapTo(mutableSetOf()) { it.connectionKey() }
