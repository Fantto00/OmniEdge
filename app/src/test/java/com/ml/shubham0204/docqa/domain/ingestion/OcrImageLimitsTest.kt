package com.ml.shubham0204.docqa.domain.ingestion

import org.junit.Test

class OcrImageLimitsTest {
    @Test
    fun acceptsImageAtPixelLimit() {
        OcrImageLimits.requireSupportedDimensions(width = 4_000, height = 4_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsImageOverPixelLimit() {
        OcrImageLimits.requireSupportedDimensions(width = 4_001, height = 4_000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidImageDimensions() {
        OcrImageLimits.requireSupportedDimensions(width = 0, height = 1_000)
    }
}
