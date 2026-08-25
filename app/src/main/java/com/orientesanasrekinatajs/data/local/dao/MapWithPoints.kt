package com.orientesanasrekinatajs.data.local.dao

import androidx.room.Embedded
import androidx.room.Relation
import com.orientesanasrekinatajs.data.local.entity.ControlPointEntity
import com.orientesanasrekinatajs.data.local.entity.ScannedMapEntity

/**
 * A [ScannedMapEntity] together with all of its [ControlPointEntity]s.
 *
 * This plain object backs the "map with points" queries of [MapDao]. Room resolves it
 * via [Embedded] (the map row) plus [Relation] (the child point rows), running both
 * statements inside a single transaction so the parent and its points are always
 * observed/returned consistently.
 *
 * @property map     The parent scanned map.
 * @property points  All control points that reference [map] via their `mapId`.
 */
data class MapWithPoints(
    @Embedded val map: ScannedMapEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "mapId",
    )
    val points: List<ControlPointEntity>,
)
