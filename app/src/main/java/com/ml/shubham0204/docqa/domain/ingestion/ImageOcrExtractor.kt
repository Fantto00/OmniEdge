package com.ml.shubham0204.docqa.domain.ingestion

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.core.annotation.Single
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Single
class ImageOcrExtractor(
    private val context: Context,
    private val contentResolver: ContentResolver,
) {
    suspend fun extract(source: ContentSource.Image): ExtractionResult {
        validateImageDimensions(source)
        val text = recognize(InputImage.fromFilePath(context, source.uri))
        return ExtractionResult(
            text = text,
            sourceType = source.sourceType,
            mimeType = source.mimeType,
            extractorId = EXTRACTOR_ID,
            extractorVersion = EXTRACTOR_VERSION,
        )
    }

    suspend fun recognize(inputImage: InputImage): String {
        val recognizer =
            TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build(),
            )
        return try {
            recognizer.process(inputImage).awaitText()
        } finally {
            recognizer.close()
        }
    }

    private fun validateImageDimensions(source: ContentSource.Image) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        requireNotNull(contentResolver.openInputStream(source.uri)) {
            "Unable to open ${source.displayName}"
        }.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, bounds)
        }
        OcrImageLimits.requireSupportedDimensions(bounds.outWidth, bounds.outHeight)
    }

    private suspend fun Task<Text>.awaitText(): String =
        suspendCancellableCoroutine { continuation ->
            this@awaitText
                .addOnSuccessListener { result ->
                    if (continuation.isActive) {
                        continuation.resume(result.text)
                    }
                }.addOnFailureListener { error ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
        }

    private companion object {
        const val EXTRACTOR_ID = "ml-kit/chinese-text-recognition"
        const val EXTRACTOR_VERSION = "16.0.1"
    }
}
