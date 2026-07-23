package com.ml.shubham0204.docqa.domain.asr

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceQueryTranscriptAccumulatorTest {
    @Test
    fun joinsOnlyNonBlankFinalSegments() {
        val accumulator = VoiceQueryTranscriptAccumulator()

        accumulator.addFinalSegment("第一段")
        accumulator.addFinalSegment("   ")
        accumulator.addFinalSegment("第二段")

        assertEquals("第一段 第二段", accumulator.value())
    }
}
