package com.ml.shubham0204.docqa.domain.asr

object SpeechModelConfig {
    const val MODEL_ID = "vosk-model-small-cn-0.22"
    const val ARCHIVE_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
    const val ARCHIVE_SHA256 = "3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba"
    const val MAX_ARCHIVE_BYTES = 60L * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 160L * 1024 * 1024
    const val MIN_AVAILABLE_STORAGE_BYTES = 256L * 1024 * 1024
    const val INSTALL_MARKER_FILE = ".installed"
    const val REQUIRED_MODEL_FILE = "am/final.mdl"
}
