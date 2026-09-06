package com.orientesanasrekinatajs.imageprocessing

import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.domain.model.ControlPointType
import org.junit.Assert.assertTrue
import org.junit.Test

/** Run with -PincludeMapExampleTests=true to package the local example photo. */
class MapExampleDetectionTest {
    @Test
    fun detectsControlsAndCombinedStartFinishInExamplePhoto() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val bitmap = context.assets.open("example_map_full.jpg").use {
            android.graphics.BitmapFactory.decodeStream(it)
        }
        try {
            val symbols = OpenCVUtils.detectControlSymbols(bitmap)
            android.util.Log.i("ExampleDetection", symbols.toString())
            assertTrue(symbols.toString(), symbols.size >= 20)
            listOf(Point2D(498f, 134f), Point2D(328f, 190f), Point2D(850f, 234f),
                Point2D(434f, 401f), Point2D(737f, 878f)).forEach { expected ->
                assertTrue("Missing $expected: $symbols", symbols.any {
                    it.center.distanceTo(expected) < 8f
                })
            }
            assertTrue("Missing combined start/finish: $symbols", symbols.any {
                it.type == ControlPointType.START_FINISH &&
                    it.center.distanceTo(Point2D(343f, 541f)) < 15f
            })
        } finally {
            bitmap.recycle()
        }
    }

}
