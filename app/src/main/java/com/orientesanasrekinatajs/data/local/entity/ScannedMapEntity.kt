package com.orientesanasrekinatajs.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A persisted, user-scanned orienteering map.
 *
 * A [ScannedMapEntity] represents one digitized map image together with the scale calibration
 * needed to convert on-map pixel distances into real-world distances. The map's detected
 * [ControlPointEntity]s reference this row through their [ControlPointEntity.mapId] foreign key
 * (cascade-deleted when the map is removed).
 *
 * @property id              Stable unique identifier (a UUID).
 * @property timestamp       Epoch milliseconds at which the map was scanned and saved.
 * @property imageFilePath   Absolute path of the rectified map image, stored in `Context.filesDir`.
 * @property pixelsPerMeter  Scale calibration: how many image pixels correspond to one real meter.
 */
@Entity(tableName = "scanned_maps")
data class ScannedMapEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val imageFilePath: String,
    val pixelsPerMeter: Float,
)
