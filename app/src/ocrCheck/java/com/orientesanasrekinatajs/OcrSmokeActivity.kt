package com.orientesanasrekinatajs

import android.app.Activity
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.orientesanasrekinatajs.domain.model.Point2D
import com.orientesanasrekinatajs.imageprocessing.ImageCropUtils
import com.orientesanasrekinatajs.imageprocessing.OcrUtils
import com.orientesanasrekinatajs.imageprocessing.OpenCVUtils
import kotlinx.coroutines.runBlocking

/** Runs inside the fully optimized app, without a separate test APK altering its classes. */
class OcrSmokeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Thread {
            try {
                runBlocking {
                    val bitmap = Bitmap.createBitmap(200, 160, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.rgb(170, 100, 155)
                        textSize = 18f
                    }
                    canvas.drawText("65", 106f, 85f, ink)
                    check(OcrUtils.extractControlNumber(bitmap) == 65) { "Plain OCR failed" }
                    ink.style = Paint.Style.STROKE
                    ink.strokeWidth = 2f
                    canvas.drawCircle(90f, 80f, 12f, ink)
                    val crop = ImageCropUtils.cropRegionOfInterest(bitmap, Point2D(90f, 80f),
                        13f, eraseSymbol = true)
                    val isolated = OpenCVUtils.isolateInkColor(crop)
                    check(OcrUtils.extractControlNumber(isolated) == 65) { "Label pipeline failed" }
                    isolated.recycle()
                    crop.recycle()
                    bitmap.recycle()
                    Log.i("OcrSmoke", "PASS: plain OCR and control label pipeline")
                }
            } catch (failure: Throwable) {
                Log.e("OcrSmoke", "FAIL", failure)
            } finally {
                runOnUiThread { finish() }
            }
        }.start()
    }
}
