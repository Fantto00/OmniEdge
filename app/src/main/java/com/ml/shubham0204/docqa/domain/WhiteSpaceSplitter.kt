package com.ml.shubham0204.docqa.domain

import kotlin.math.max
import kotlin.math.min

class WhiteSpaceSplitter {
    companion object {
        private val sentenceDelimiters = setOf('。', '！', '？', '；', '.', '!', '?', ';')

        fun createChunks(
            docText: String,
            chunkSize: Int,
            chunkOverlap: Int,
            separatorParagraph: String = "\n\n",
            separator: String = " ",
        ): List<String> {
            val textChunks = ArrayList<String>()
            docText.split(separatorParagraph).forEach { paragraph ->
                val chunks =
                    if (paragraph.none(Char::isWhitespace)) {
                        createWhitespaceFreeChunks(paragraph, chunkSize)
                    } else {
                        createWhitespaceChunks(paragraph, chunkSize, separator)
                    }

                val overlappingChunks = ArrayList<String>(chunks)
                if (chunkOverlap > 1 && chunks.isNotEmpty()) {
                    for (i in 0..<chunks.size - 1) {
                        val overlapStart = max(0, chunks[i].length - chunkOverlap)
                        val overlapEnd = min(chunkOverlap, chunks[i + 1].length)
                        overlappingChunks.add(
                            chunks[i].substring(overlapStart) +
                                " " +
                                chunks[i + 1].substring(0..<overlapEnd),
                        )
                    }
                }

                textChunks.addAll(overlappingChunks)
            }
            return textChunks
        }

        private fun createWhitespaceChunks(
            paragraph: String,
            chunkSize: Int,
            separator: String,
        ): List<String> {
            var currChunk = ""
            val chunks = ArrayList<String>()
            paragraph.split(separator).forEach { word ->
                val newChunk =
                    currChunk +
                        (
                            if (currChunk.isNotEmpty()) {
                                separator
                            } else {
                                ""
                            }
                        ) +
                        word
                if (newChunk.length <= chunkSize) {
                    currChunk = newChunk
                } else {
                    if (currChunk.isNotEmpty()) {
                        chunks.add(currChunk)
                    }
                    currChunk = word
                }
            }
            if (currChunk.isNotEmpty()) {
                chunks.add(currChunk)
            }
            return chunks
        }

        private fun createWhitespaceFreeChunks(
            paragraph: String,
            chunkSize: Int,
        ): List<String> {
            if (paragraph.isEmpty()) {
                return emptyList()
            }

            val chunks = ArrayList<String>()
            var start = 0
            while (start < paragraph.length) {
                val end = min(start + chunkSize, paragraph.length)
                val boundary =
                    (end - 1 downTo start + chunkSize / 2)
                        .firstOrNull { paragraph[it] in sentenceDelimiters }
                val chunkEnd =
                    if (end == paragraph.length) {
                        end
                    } else {
                        (boundary ?: end - 1) + 1
                    }
                chunks.add(paragraph.substring(start, chunkEnd).trim())
                start = chunkEnd
            }
            return chunks
        }
    }
}
