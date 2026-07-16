package com.ml.shubham0204.docqa.domain.asr

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.ml.shubham0204.docqa.domain.ingestion.ContentSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.core.annotation.Single

data class DecodedAudio(
    val mimeType: String,
    val declaredDurationMs: Long?,
    val normalizedDurationMs: Long,
)

@Single
class PlatformAudioDecoder(
    private val context: Context,
) {
    suspend fun decode(
        source: ContentSource.Audio,
        onPcm: suspend (ByteArray) -> Unit,
        onProgress: (String) -> Unit = {},
    ): DecodedAudio {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, source.uri, null)
            val trackIndex = findAudioTrack(extractor)
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mimeType =
                requireNotNull(inputFormat.getString(MediaFormat.KEY_MIME)) {
                    "The selected audio has no MIME type."
                }
            val durationUs = inputFormat.durationUsOrNull()
            durationUs?.let(AudioPocLimits::requireSupportedDuration)

            codec = MediaCodec.createDecoderByType(mimeType)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            var inputEnded = false
            var outputEnded = false
            var normalizer: Pcm16MonoNormalizer? = null
            var normalizedPcmBytes = 0L
            val bufferInfo = MediaCodec.BufferInfo()

            while (!outputEnded) {
                currentCoroutineContext().ensureActive()
                if (!inputEnded) {
                    val inputBufferIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = requireNotNull(codec.getInputBuffer(inputBufferIndex))
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                when (val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        normalizer = createNormalizer(codec.outputFormat)
                        onProgress("Decoding audio with the Android platform codec...")
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> {
                        if (outputBufferIndex >= 0) {
                            try {
                                if (bufferInfo.size > 0) {
                                    val outputBuffer = requireNotNull(codec.getOutputBuffer(outputBufferIndex))
                                    outputBuffer.position(bufferInfo.offset)
                                    outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                    val decodedPcm = ByteArray(bufferInfo.size)
                                    outputBuffer.get(decodedPcm)
                                    val normalizedPcm =
                                        requireNotNull(normalizer) {
                                            "The platform decoder did not provide an output PCM format."
                                        }.normalize(decodedPcm)
                                    normalizedPcmBytes += normalizedPcm.size
                                    AudioPocLimits.requireSupportedPcmSize(normalizedPcmBytes)
                                    if (normalizedPcm.isNotEmpty()) {
                                        onPcm(normalizedPcm)
                                    }
                                }
                            } finally {
                                codec.releaseOutputBuffer(outputBufferIndex, false)
                            }
                            outputEnded =
                                bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        }
                    }
                }
            }
            return DecodedAudio(
                mimeType = mimeType,
                declaredDurationMs = durationUs?.div(1_000L),
                normalizedDurationMs =
                    normalizedPcmBytes * 1_000L /
                        (AudioPocLimits.TARGET_SAMPLE_RATE_HZ * Short.SIZE_BYTES),
            )
        } finally {
            codec?.let { decoder ->
                runCatching { decoder.stop() }
                decoder.release()
            }
            extractor.release()
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int =
        (0 until extractor.trackCount).firstOrNull { trackIndex ->
            extractor
                .getTrackFormat(trackIndex)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith(AUDIO_MIME_PREFIX) == true
        } ?: error("The selected file has no audio track supported by the Android platform decoder.")

    private fun createNormalizer(outputFormat: MediaFormat): Pcm16MonoNormalizer {
        val pcmEncoding =
            if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
        require(pcmEncoding == AudioFormat.ENCODING_PCM_16BIT) {
            "The Android decoder did not produce 16-bit PCM audio."
        }
        return Pcm16MonoNormalizer(
            inputSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
            inputChannelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
        )
    }

    private fun MediaFormat.durationUsOrNull(): Long? =
        if (containsKey(MediaFormat.KEY_DURATION)) getLong(MediaFormat.KEY_DURATION) else null

    private companion object {
        const val AUDIO_MIME_PREFIX = "audio/"
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
