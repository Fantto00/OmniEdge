package com.ml.shubham0204.docqa.domain.asr

import org.junit.Assert.assertEquals
import org.junit.Test

class Pcm16MonoNormalizerTest {
    @Test
    fun keepsSixteenKhzMonoPcmUnchanged() {
        val normalized = Pcm16MonoNormalizer(16_000, 1).normalize(pcm16(100, -200, 300))

        assertEquals(listOf<Short>(100, -200, 300), samples(normalized))
    }

    @Test
    fun averagesStereoFramesToMono() {
        val normalized = Pcm16MonoNormalizer(16_000, 2).normalize(pcm16(200, 100, -200, 0))

        assertEquals(listOf<Short>(150, -100), samples(normalized))
    }

    @Test
    fun upsamplesEightKhzPcmToSixteenKhz() {
        val normalized = Pcm16MonoNormalizer(8_000, 1).normalize(pcm16(100, 200))

        assertEquals(listOf<Short>(100, 100, 200, 200), samples(normalized))
    }

    private fun pcm16(vararg values: Short): ByteArray =
        ByteArray(values.size * Short.SIZE_BYTES).also { output ->
            values.forEachIndexed { index, value ->
                output[index * Short.SIZE_BYTES] = (value.toInt() and 0xff).toByte()
                output[index * Short.SIZE_BYTES + 1] = ((value.toInt() ushr 8) and 0xff).toByte()
            }
        }

    private fun samples(bytes: ByteArray): List<Short> =
        bytes
            .asList()
            .chunked(Short.SIZE_BYTES)
            .map { pair -> ((pair[1].toInt() shl 8) or (pair[0].toInt() and 0xff)).toShort() }
}
