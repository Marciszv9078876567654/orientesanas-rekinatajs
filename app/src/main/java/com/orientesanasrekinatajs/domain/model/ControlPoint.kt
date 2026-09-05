package com.orientesanasrekinatajs.domain.model

import java.util.UUID

/**
 * A single point detected on a map: a control, start, or finish.
 *
 * Properties are mutable because several of them are refined after initial detection —
 * the [code] is filled in by OCR, and both [code]/[points] and [type] can be corrected
 * manually by the user in the editing UI.
 *
 * @property id       Stable unique identifier (a UUID), used as the primary key in storage.
 * @property code     The printed control number, e.g. 65 or 101.
 * @property points   The score value, derived as integer division `code / 10`.
 * @property center   The center of the symbol in the rectified map's pixel space.
 * @property type     The legend type of the symbol.
 * @property needsReview Whether automatic recognition needs user confirmation.
 */
data class ControlPoint(
    val id: String = UUID.randomUUID().toString(),
    var code: Int,
    var points: Int = code / 10,
    var center: Point2D,
    var type: ControlPointType = ControlPointType.CONTROL,
    var needsReview: Boolean = false,
)
