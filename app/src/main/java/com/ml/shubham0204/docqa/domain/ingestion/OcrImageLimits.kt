package com.ml.shubham0204.docqa.domain.ingestion

object OcrImageLimits {
    const val MAX_IMAGE_PIXELS = 16_000_000L

    fun requireSupportedDimensions(
        width: Int,
        height: Int,
    ) {
        require(width > 0 && height > 0) {
            "The selected image has invalid dimensions."
        }
        require(width.toLong() * height <= MAX_IMAGE_PIXELS) {
            "The selected image exceeds the ${MAX_IMAGE_PIXELS / 1_000_000} MP OCR limit."
        }
    }
}
