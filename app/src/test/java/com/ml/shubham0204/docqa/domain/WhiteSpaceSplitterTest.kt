package com.ml.shubham0204.docqa.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class WhiteSpaceSplitterTest {
    @Test
    fun createsChunksForSpaceSeparatedText() {
        val chunks =
            WhiteSpaceSplitter.createChunks(
                docText = "alpha beta gamma delta",
                chunkSize = 16,
                chunkOverlap = 0,
            )

        assertEquals(listOf("alpha beta gamma", "delta"), chunks)
    }

    @Test
    fun createsSentenceBoundedChunksForWhitespaceFreeText() {
        val chunks =
            WhiteSpaceSplitter.createChunks(
                docText = "第一句内容。第二句内容。第三句内容。",
                chunkSize = 8,
                chunkOverlap = 0,
            )

        assertEquals(listOf("第一句内容。", "第二句内容。", "第三句内容。"), chunks)
    }

    @Test
    fun createsLengthBoundedChunksWhenWhitespaceFreeTextHasNoSentenceBoundary() {
        val chunks =
            WhiteSpaceSplitter.createChunks(
                docText = "abcdefghijkl",
                chunkSize = 5,
                chunkOverlap = 0,
            )

        assertEquals(listOf("abcde", "fghij", "kl"), chunks)
    }
}
