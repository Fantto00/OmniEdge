package com.ml.shubham0204.docqa.domain.asr

object AudioPocLimits {
    const val TARGET_SAMPLE_RATE_HZ = 16_000
    const val TARGET_CHANNEL_COUNT = 1
    const val MAX_DURATION_MS = 5 * 60 * 1_000L
    const val MAX_NORMALIZED_PCM_BYTES =
        (MAX_DURATION_MS / 1_000L) * TARGET_SAMPLE_RATE_HZ * Short.SIZE_BYTES

    fun requireSupportedDuration(durationUs: Long) {
        if (durationUs > 0) {
            require(durationUs <= MAX_DURATION_MS * 1_000L) {
                "Audio transcription POC supports up to 5 minutes."
            }
        }
    }

    fun requireSupportedPcmSize(pcmBytes: Long) {
        require(pcmBytes <= MAX_NORMALIZED_PCM_BYTES) {
            "Audio transcription POC supports up to 5 minutes."
        }
    }
}
