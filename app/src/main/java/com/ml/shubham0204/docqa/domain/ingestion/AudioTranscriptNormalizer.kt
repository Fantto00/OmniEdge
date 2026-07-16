package com.ml.shubham0204.docqa.domain.ingestion

/** Normalizes Vosk token spacing for indexing without changing non-CJK word boundaries. */
object AudioTranscriptNormalizer {
    private val chinesePunctuation = setOf('，', '。', '！', '？', '；', '：', '、')

    fun normalizeForIndexing(transcript: String): String {
        val normalized = StringBuilder()
        var index = 0
        while (index < transcript.length) {
            val character = transcript[index]
            if (character.isWhitespace()) {
                val previous = normalized.lastOrNull()
                val next = transcript.drop(index + 1).firstOrNull { !it.isWhitespace() }
                if (previous != null && next != null && shouldRemoveWhitespace(previous, next)) {
                    index++
                    continue
                }
                if (normalized.lastOrNull() != ' ') {
                    normalized.append(' ')
                }
            } else {
                normalized.append(character)
            }
            index++
        }
        return normalized.toString().trim()
    }

    private fun shouldRemoveWhitespace(
        previous: Char,
        next: Char,
    ): Boolean =
        (isChineseOrDigit(previous) && isChineseOrDigit(next)) ||
            (isChineseOrDigit(previous) && next in chinesePunctuation) ||
            (previous in chinesePunctuation && isChineseOrDigit(next))

    private fun isChineseOrDigit(character: Char): Boolean =
        character.isDigit() || character in '\u3400'..'\u9fff'
}
