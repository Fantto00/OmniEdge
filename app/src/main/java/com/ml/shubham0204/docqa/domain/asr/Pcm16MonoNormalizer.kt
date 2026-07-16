package com.ml.shubham0204.docqa.domain.asr

import java.io.ByteArrayOutputStream
import kotlin.math.floor

/** Converts decoded PCM-16LE frames to the Vosk-required 16 kHz mono stream without buffering audio. */
class Pcm16MonoNormalizer(
    private val inputSampleRate: Int,
    private val inputChannelCount: Int,
) {
    private val sourceFramesPerOutputFrame =
        inputSampleRate.toDouble() / AudioPocLimits.TARGET_SAMPLE_RATE_HZ
    private var nextOutputSourceFrame = 0.0
    private var sourceFrameIndex = 0L
    private var previousFrame: Short? = null

    init {
        require(inputSampleRate > 0) { "Decoded audio has an invalid sample rate." }
        require(inputChannelCount in 1..2) { "Audio transcription POC supports mono or stereo audio only." }
    }

    fun normalize(inputPcm: ByteArray): ByteArray {
        val bytesPerFrame = inputChannelCount * Short.SIZE_BYTES
        require(inputPcm.size % bytesPerFrame == 0) { "Decoded audio is not PCM-16 frame aligned." }

        val output = ByteArrayOutputStream()
        var byteOffset = 0
        while (byteOffset < inputPcm.size) {
            val currentFrame = averageChannels(inputPcm, byteOffset)
            while (floor(nextOutputSourceFrame).toLong() <= sourceFrameIndex) {
                val sourceFrame = floor(nextOutputSourceFrame).toLong()
                val outputSample =
                    when (sourceFrame) {
                        sourceFrameIndex -> currentFrame
                        sourceFrameIndex - 1 -> previousFrame ?: currentFrame
                        else -> error("PCM resampler lost its input frame boundary.")
                    }
                output.write(outputSample.toInt() and 0xff)
                output.write((outputSample.toInt() ushr 8) and 0xff)
                nextOutputSourceFrame += sourceFramesPerOutputFrame
            }
            previousFrame = currentFrame
            sourceFrameIndex++
            byteOffset += bytesPerFrame
        }
        return output.toByteArray()
    }

    private fun averageChannels(
        inputPcm: ByteArray,
        byteOffset: Int,
    ): Short {
        var total = 0
        repeat(inputChannelCount) { channel ->
            val offset = byteOffset + channel * Short.SIZE_BYTES
            val sample =
                ((inputPcm[offset + 1].toInt() shl 8) or (inputPcm[offset].toInt() and 0xff)).toShort()
            total += sample.toInt()
        }
        return (total / inputChannelCount).toShort()
    }
}
