package com.orientesanasrekinatajs.imageprocessing

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** On-device OCR operations for extracting orienteering control numbers. */
object OcrUtils {
    private const val MIN_INPUT_DIMENSION = 32
    private const val TARGET_TEXT_DIMENSION = 240
    private const val MAX_UPSCALE_FACTOR = 8f
    private val controlNumberPattern = Regex(
        """(?<![A-Za-z0-9])(?:\d{2,3}|\d(?:\s\d){1,2})(?![A-Za-z0-9])""",
    )
    private val ambiguousControlNumberPattern = Regex(
        """(?<![A-Za-z0-9])(?:[0-9BOSIl]{2,3}|[0-9BOSIl](?:\s[0-9BOSIl]){1,2})(?![A-Za-z0-9])""",
    )
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
        if (bitmap.width < MIN_INPUT_DIMENSION || bitmap.height < MIN_INPUT_DIMENSION) return null
        val minimumDimension = minOf(bitmap.width, bitmap.height)
        val scale = (TARGET_TEXT_DIMENSION.toFloat() / minimumDimension)
            .coerceIn(1f, MAX_UPSCALE_FACTOR)
        val prepared = if (scale > 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt(),
                (bitmap.height * scale).roundToInt(),
                true,
            )
        } else {
            bitmap
        }
        return try {
            // Printed labels may be sideways or upside down relative to the source bitmap.
            // Keep the common upright path fast, then retry the remaining right-angle rotations
            // only when it contains no plausible control number.
            listOf(0, 180, 90, 270).firstNotNullOfOrNull { rotation ->
                val recognizedText = recognize(InputImage.fromBitmap(prepared, rotation))
                val width = if (rotation % 180 == 0) prepared.width else prepared.height
                val height = if (rotation % 180 == 0) prepared.height else prepared.width
                selectClosestCandidate(recognizedText, width, height)
            }
        } finally {
            if (prepared !== bitmap && !prepared.isRecycled) prepared.recycle()
        }
    }

    internal fun controlNumbersIn(text: String): List<Int> {
        val exact = controlNumberPattern.findAll(text).mapNotNull { match ->
            match.value.filter(Char::isDigit).toIntOrNull()
        }
        val normalized = ambiguousControlNumberPattern.findAll(text).mapNotNull { match ->
            val compact = match.value.filterNot(Char::isWhitespace)
            if (compact.all(Char::isDigit)) return@mapNotNull null
            compact.map { character ->
                when (character) {
                    'B' -> '8'
                    'O' -> '0'
                    'S' -> '5'
                    'I', 'l' -> '1'
                    else -> character
                }
            }.joinToString("").toIntOrNull()
        }
        return (exact + normalized).distinct().toList()
    }

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
