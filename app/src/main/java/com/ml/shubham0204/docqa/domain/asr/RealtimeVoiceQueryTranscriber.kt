package com.ml.shubham0204.docqa.domain.asr

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.core.annotation.Single
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Transcribes a foreground microphone session into a chat query without storing audio.
 */
@Single
class RealtimeVoiceQueryTranscriber(
    private val context: Context,
    private val speechModelManager: SpeechModelManager,
) {
    private val recorderLock = Any()

    @Volatile private var stopRequested = false
    private var activeRecorder: AudioRecord? = null

    fun isModelInstalled(): Boolean = speechModelManager.isModelInstalled()

    /** Prepares one session so a stop request made immediately after start is still observed. */
    fun prepareForRecording() {
        synchronized(recorderLock) {
            check(activeRecorder == null) { "A voice query is already running." }
            stopRequested = false
        }
    }

    fun stop() {
        val recorder =
            synchronized(recorderLock) {
                stopRequested = true
                activeRecorder
            }
        runCatching { recorder?.stop() }
    }

    suspend fun transcribe(onPartialText: (String) -> Unit): String =
        withContext(Dispatchers.Default) {
            require(
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED,
            ) {
                "Microphone permission is required for voice queries."
            }
            val modelDirectory =
                requireNotNull(speechModelManager.installedModelDirectory()) {
                    "Set up the Chinese ASR model before using voice queries."
                }
            val minimumBufferSize =
                AudioRecord.getMinBufferSize(
                    AudioPocLimits.TARGET_SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                )
            require(minimumBufferSize > 0) { "The device does not support the voice query recording format." }

            val recorder =
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    AudioPocLimits.TARGET_SAMPLE_RATE_HZ,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minimumBufferSize, DEFAULT_BUFFER_SIZE),
                )
            require(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "Unable to initialize microphone recording."
            }

            var model: Model? = null
            var recognizer: Recognizer? = null
            try {
                model = Model(modelDirectory.absolutePath)
                recognizer = Recognizer(requireNotNull(model), AudioPocLimits.TARGET_SAMPLE_RATE_HZ.toFloat())
                val activeRecognizer = requireNotNull(recognizer)
                val transcript = VoiceQueryTranscriptAccumulator()
                synchronized(recorderLock) {
                    activeRecorder = recorder
                }
                recorder.startRecording()
                require(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    "Unable to start microphone recording."
                }

                val buffer = ByteArray(maxOf(minimumBufferSize, DEFAULT_BUFFER_SIZE))
                while (!stopRequested && currentCoroutineContext().isActive) {
                    when (val bytesRead = recorder.read(buffer, 0, buffer.size)) {
                        in 1..Int.MAX_VALUE -> {
                            if (activeRecognizer.acceptWaveForm(buffer, bytesRead)) {
                                transcript.addFinalSegment(parseResultText(activeRecognizer.result, RESULT_TEXT_KEY))
                            } else {
                                parseResultText(activeRecognizer.partialResult, PARTIAL_TEXT_KEY)
                                    .takeIf(String::isNotBlank)
                                    ?.let(onPartialText)
                            }
                        }

                        AudioRecord.ERROR_INVALID_OPERATION,
                        AudioRecord.ERROR_BAD_VALUE,
                        -> if (!stopRequested) error("Microphone recording failed.")

                        else -> Unit
                    }
                }
                transcript.addFinalSegment(parseResultText(activeRecognizer.finalResult, RESULT_TEXT_KEY))
                transcript.value()
            } finally {
                synchronized(recorderLock) {
                    if (activeRecorder === recorder) {
                        activeRecorder = null
                    }
                }
                runCatching { recorder.stop() }
                recorder.release()
                recognizer?.close()
                model?.close()
            }
        }

    private fun parseResultText(result: String, key: String): String =
        runCatching { JSONObject(result).optString(key).trim() }.getOrDefault("")

    private companion object {
        const val DEFAULT_BUFFER_SIZE = 4_096
        const val RESULT_TEXT_KEY = "text"
        const val PARTIAL_TEXT_KEY = "partial"
    }
}

internal class VoiceQueryTranscriptAccumulator {
    private val finalSegments = mutableListOf<String>()

    fun addFinalSegment(segment: String) {
        segment.trim().takeIf(String::isNotEmpty)?.let(finalSegments::add)
    }

    fun value(): String = finalSegments.joinToString(separator = " ")
}
