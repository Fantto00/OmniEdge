package com.ml.shubham0204.docqa.domain.ingestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannedPdfOcrLimitsTest {
    @Test
    fun usesOcrOnlyWhenEmbeddedTextIsInsufficient() {
        assertTrue(ScannedPdfOcrLimits.shouldUseOcr("short text"))
        assertFalse(ScannedPdfOcrLimits.shouldUseOcr("a".repeat(80)))
    }

    @Test
    fun downscalesLongPageEdge() {
        assertEquals(Pair(2_048, 1_024), ScannedPdfOcrLimits.renderDimensions(4_096, 2_048))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPdfWithTooManyPages() {
        ScannedPdfOcrLimits.requireSupportedPageCount(11)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTotalPixelsOverLimit() {
        ScannedPdfOcrLimits.requireSupportedTotalPixels(40_000_001)
    }
}
