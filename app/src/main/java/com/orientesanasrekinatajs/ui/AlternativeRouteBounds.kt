package com.orientesanasrekinatajs.ui

/** Intersects absolute limits with relative limits already resolved against the source route. */
internal fun <T : Comparable<T>> intersectAlternativeBounds(
    absoluteMinimum: T?,
    absoluteMaximum: T?,
    relativeMinimum: T?,
    relativeMaximum: T?,
): Pair<T?, T?> =
    listOfNotNull(absoluteMinimum, relativeMinimum).maxOrNull() to
        listOfNotNull(absoluteMaximum, relativeMaximum).minOrNull()
