package com.ml.shubham0204.docqa.domain.ingestion

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * PDF参数限制
 */
object ScannedPdfOcrLimits {
    const val MIN_EMBEDDED_TEXT_CHARACTERS = 80
    const val MAX_PAGES = 10
    const val MAX_RENDER_EDGE_PIXELS = 2_048
    const val MAX_TOTAL_RENDER_PIXELS = 40_000_000L
    const val MAX_OCR_DURATION_MS = 60_000L

    fun shouldUseOcr(extractedText: String): Boolean =
        extractedText.trim().length < MIN_EMBEDDED_TEXT_CHARACTERS

    fun renderDimensions(
        width: Int,
        height: Int,
    ): Pair<Int, Int> {
        require(width > 0 && height > 0) { "The PDF page has invalid dimensions." }
        val scale =
            minOf(
                1.0,
                MAX_RENDER_EDGE_PIXELS.toDouble() / max(width, height).toDouble(),
            )
        return Pair(
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
        )
    }

    fun requireSupportedPageCount(pageCount: Int) {
        require(pageCount in 1..MAX_PAGES) {
            "Scanned PDF OCR supports up to $MAX_PAGES pages."
        }
    }

    fun requireSupportedTotalPixels(totalPixels: Long) {
        require(totalPixels <= MAX_TOTAL_RENDER_PIXELS) {
            "Scanned PDF OCR exceeds the ${MAX_TOTAL_RENDER_PIXELS / 1_000_000} MP render limit."
        }
    }
}
