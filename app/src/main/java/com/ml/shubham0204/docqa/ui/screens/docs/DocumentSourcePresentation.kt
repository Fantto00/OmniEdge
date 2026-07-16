package com.ml.shubham0204.docqa.ui.screens.docs

object DocumentSourcePresentation {
    fun label(sourceType: String): String =
        when (sourceType) {
            "IMAGE" -> "Image OCR"
            "AUDIO" -> "Audio transcript"
            else -> "Document"
        }
}
