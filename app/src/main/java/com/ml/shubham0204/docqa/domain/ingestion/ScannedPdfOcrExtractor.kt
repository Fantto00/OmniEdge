package com.ml.shubham0204.docqa.domain.ingestion

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout
import org.koin.core.annotation.Single
import java.io.File

/**
 * 字数少于80，被视为扫描版本的 PDF 的抓取类
 */
@Single
class ScannedPdfOcrExtractor(
    private val contentResolver: ContentResolver,
    private val imageOcrExtractor: ImageOcrExtractor,
) {
    // GPT写的 可读性差
    suspend fun extract(
        source: ContentSource.Document,
        onProgress: (String) -> Unit = {},
    ): ExtractionResult =
        withTimeout(ScannedPdfOcrLimits.MAX_OCR_DURATION_MS) { // 超时限制
            val pageTexts = mutableListOf<String>()
            openFileDescriptor(source.uri).use { fileDescriptor ->
                PdfRenderer(fileDescriptor).use { pdfRenderer ->
                    ScannedPdfOcrLimits.requireSupportedPageCount(pdfRenderer.pageCount)
                    var totalRenderedPixels = 0L
                    repeat(pdfRenderer.pageCount) { pageIndex ->
                        currentCoroutineContext().ensureActive()
                        onProgress("Recognizing scanned PDF page ${pageIndex + 1}/${pdfRenderer.pageCount}...")
                        pdfRenderer.openPage(pageIndex).use { page ->
                            val (width, height) =
                                ScannedPdfOcrLimits.renderDimensions(page.width, page.height)
                            totalRenderedPixels += width.toLong() * height
                            ScannedPdfOcrLimits.requireSupportedTotalPixels(totalRenderedPixels)
                            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            try {
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                pageTexts += imageOcrExtractor.recognize(InputImage.fromBitmap(bitmap, 0)) // 在这里用图片引擎识别
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            ExtractionResult(
                text = pageTexts.joinToString(separator = "\n\n"),
                sourceType = source.sourceType,
                mimeType = source.mimeType,
                extractorId = EXTRACTOR_ID,
                extractorVersion = EXTRACTOR_VERSION,
                extractionMetadata = "ocrPages=1-${pageTexts.size}",
            )
        }

    private fun openFileDescriptor(uri: Uri): ParcelFileDescriptor =
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            ParcelFileDescriptor.open(File(requireNotNull(uri.path)), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            requireNotNull(contentResolver.openFileDescriptor(uri, "r")) {
                "Unable to open $uri"
            }
        }

    private companion object {
        const val EXTRACTOR_ID = "ml-kit/chinese-text-recognition/pdf-renderer"
        const val EXTRACTOR_VERSION = "16.0.1"
    }
}
