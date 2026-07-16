package com.ml.shubham0204.docqa.ui.screens.docs

import androidx.annotation.StringRes
import com.ml.shubham0204.docqa.R

object DocumentSourcePresentation {
    @StringRes
    fun labelRes(sourceType: String): Int =
        when (sourceType) {
            "IMAGE" -> R.string.document_source_image
            "AUDIO" -> R.string.document_source_audio
            else -> R.string.document_source_document
        }
}
