package com.ml.shubham0204.docqa.domain.asr

import android.os.SystemClock
import com.ml.shubham0204.docqa.domain.ingestion.ContentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.koin.core.annotation.Single
import org.vosk.Model
import org.vosk.Recognizer

data class AudioTranscriptionPocResult(
    val text: String,
    val mimeType: String,
    val audioDurationMs: Long,
    val processingDurationMs: Long,
)

@Single
class VoskTranscriber(
    private val speechModelManager: SpeechModelManager,
    private val platformAudioDecoder: PlatformAudioDecoder,
) {
    suspend fun transcribe(
        source: ContentSource.Audio,
        onProgress: (String) -> Unit = {},
    ): AudioTranscriptionPocResult =
        withContext(Dispatchers.Default) {
            val modelDirectory =
                requireNotNull(speechModelManager.installedModelDirectory()) {
                    "Set up the Chinese ASR model before starting an audio transcription POC."
                }
            val startedAt = SystemClock.elapsedRealtime()
            val model = Model(modelDirectory.absolutePath)
            val recognizer = Recognizer(model, AudioPocLimits.TARGET_SAMPLE_RATE_HZ.toFloat())
            try {
                var hasAnnouncedTranscription = false
                val decodedAudio =
                    platformAudioDecoder.decode(
                        source = source,
                        onPcm = { pcm ->
                            currentCoroutineContext().ensureActive()
                            recognizer.acceptWaveForm(pcm, pcm.size)
                            if (!hasAnnouncedTranscription) {
                                onProgress("Transcribing Chinese audio offline...")
                                hasAnnouncedTranscription = true
                            }
                        },
                        onProgress = onProgress,
                    )
                val text = JSONObject(recognizer.finalResult).optString("text").trim()
                AudioTranscriptionPocResult(
                    text = text,
                    mimeType = decodedAudio.mimeType,
                    audioDurationMs = decodedAudio.normalizedDurationMs,
                    processingDurationMs = SystemClock.elapsedRealtime() - startedAt,
                )
            } finally {
                recognizer.close()
                model.close()
            }
        }
}
