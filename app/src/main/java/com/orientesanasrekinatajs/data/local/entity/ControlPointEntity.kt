package com.orientesanasrekinatajs.data.local.entity

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A persisted control point (start, finish, or scored control) belonging to a [ScannedMapEntity].
 *
 * The symbol geometry is stored as the flat [x]/[y] center in the rectified map's pixel space, and
 * the legend [type] is stored as the name of the corresponding
 * `com.orientesanasrekinatajs.domain.model.ControlPointType` enum value (kept as a `String` so the
 * table stays independent of the domain type; a Room type converter can map it later).
 *
 * Rows are cascade-deleted when their parent [ScannedMapEntity] is removed.
 *
 * @property id       Stable unique identifier (a UUID).
 * @property mapId    Foreign key to the owning [ScannedMapEntity.id].
 * @property code     The printed control number, e.g. 65 or 101.
 * @property points   The score value, derived as integer division `code / 10`.
 * @property x        Horizontal center coordinate in the rectified map's pixel space.
 * @property y        Vertical center coordinate in the rectified map's pixel space.
 * @property type     `ControlPointType.name` of the symbol.
 * @property needsReview Whether the recognized code needs user confirmation.
 */
@Entity(
    tableName = "control_points",
    foreignKeys = [
        ForeignKey(
            entity = ScannedMapEntity::class,
            parentColumns = ["id"],
            childColumns = ["mapId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["mapId"])],
)
data class ControlPointEntity(
    @PrimaryKey val id: String,
    val mapId: String,
    val code: Int,
    val points: Int,
    val x: Float,
    val y: Float,
    val type: String,
    @ColumnInfo(defaultValue = "0") val needsReview: Boolean = false,
)
