package com.orientesanasrekinatajs.domain.model

/**
 * The kind of a point on an orienteering map, as defined by the IOF legend.
 *
 * [START] and [FINISH] anchor a route; [CONTROL] points are scored.
 */
enum class ControlPointType {
    /** A single start symbol (triangle). */
    START,

    /** A single finish symbol (double circle). */
    FINISH,

    /** A combined start/finish symbol (triangle inside a circle). */
    START_FINISH,

    /** A standard scored control (single magenta circle). */
    CONTROL,
}
