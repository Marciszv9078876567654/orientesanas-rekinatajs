package com.orientesanasrekinatajs.imageprocessing

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** On-device OCR operations for extracting orienteering control numbers. */
object OcrUtils {
    private val controlNumberPattern = Regex("""\b\d{2,3}\b""")
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Recognizes a two- or three-digit control number in [bitmap].
     *
     * When several candidates are present, the candidate whose text bounds are closest to the
     * bitmap center is returned. This aligns selection with an ROI cropped around a symbol.
     */
    suspend fun extractControlNumber(bitmap: Bitmap): Int? {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizedText = recognize(image)
        return selectClosestCandidate(recognizedText, bitmap.width, bitmap.height)
    }

    internal fun controlNumbersIn(text: String): List<Int> =
        controlNumberPattern.findAll(text).mapNotNull { match ->
            match.value.toIntOrNull()
        }.toList()

    private suspend fun recognize(image: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    if (continuation.isActive) continuation.resume(result)
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) continuation.resumeWithException(exception)
                }
        }

    private fun selectClosestCandidate(
        recognizedText: Text,
        imageWidth: Int,
        imageHeight: Int,
    ): Int? {
        val imageCenterX = imageWidth / 2.0
        val imageCenterY = imageHeight / 2.0
        val candidates = buildList {
            recognizedText.textBlocks.forEach { block ->
                block.lines.forEach { line ->
                    val elementCandidates = line.elements.flatMap { element ->
                        val bounds = element.boundingBox ?: line.boundingBox ?: block.boundingBox
                        controlNumbersIn(element.text).map { number ->
                            OcrCandidate(number, bounds?.centerX(), bounds?.centerY())
                        }
                    }
                    if (elementCandidates.isNotEmpty()) {
                        addAll(elementCandidates)
                    } else {
                        val bounds = line.boundingBox ?: block.boundingBox
                        controlNumbersIn(line.text).forEach { number ->
                            add(OcrCandidate(number, bounds?.centerX(), bounds?.centerY()))
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) return controlNumbersIn(recognizedText.text).firstOrNull()
        return candidates.minBy { candidate ->
            val centerX = candidate.centerX?.toDouble() ?: imageCenterX
            val centerY = candidate.centerY?.toDouble() ?: imageCenterY
            val dx = centerX - imageCenterX
            val dy = centerY - imageCenterY
            dx * dx + dy * dy
        }.number
    }

    private data class OcrCandidate(
        val number: Int,
        val centerX: Int?,
        val centerY: Int?,
    )
}
