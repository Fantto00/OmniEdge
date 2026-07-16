package com.ml.shubham0204.docqa.domain.ingestion

import android.content.ContentResolver
import com.ml.shubham0204.docqa.data.Chunk
import com.ml.shubham0204.docqa.data.Document
import com.ml.shubham0204.docqa.data.DocumentsDB
import com.ml.shubham0204.docqa.domain.SentenceEmbeddingProvider
import com.ml.shubham0204.docqa.domain.WhiteSpaceSplitter
import com.ml.shubham0204.docqa.domain.asr.VoskTranscriber
import com.ml.shubham0204.docqa.domain.readers.Readers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.koin.core.annotation.Single
import java.security.MessageDigest

@Single
class ContentIngestionUseCase(
    private val contentResolver: ContentResolver,
    private val documentsDB: DocumentsDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
    private val imageOcrExtractor: ImageOcrExtractor,
    private val scannedPdfOcrExtractor: ScannedPdfOcrExtractor,
    private val voskTranscriber: VoskTranscriber,
) {
    suspend fun ingestDocument(
        source: ContentSource.Document,
        onProgress: (String) -> Unit = {},
    ): IngestionResult {
        onProgress("Extracting document text...")
        val primaryExtraction = extractDocument(source)
        val extractionResult =
            if (
                source.documentType == Readers.DocumentType.PDF &&
                ScannedPdfOcrLimits.shouldUseOcr(primaryExtraction.text)
            ) {
                onProgress("No embedded PDF text found. Starting OCR...")
                scannedPdfOcrExtractor.extract(source, onProgress)
            } else {
                primaryExtraction
            }
        return indexExtraction(source, extractionResult, onProgress)
    }

    suspend fun ingestImage(
        source: ContentSource.Image,
        onProgress: (String) -> Unit = {},
    ): IngestionResult {
        onProgress("Recognizing image text...")
        return indexExtraction(source, imageOcrExtractor.extract(source), onProgress)
    }

    suspend fun ingestAudio(
        source: ContentSource.Audio,
        onProgress: (String) -> Unit = {},
    ): IngestionResult {
        onProgress("Transcribing audio offline...")
        val transcription = voskTranscriber.transcribe(source, onProgress)
        val indexableText = AudioTranscriptNormalizer.normalizeForIndexing(transcription.text)
        return indexExtraction(
            source = source,
            extractionResult =
                ExtractionResult(
                    text = indexableText,
                    sourceType = source.sourceType,
                    mimeType = transcription.mimeType,
                    extractorId = "vosk/chinese-small",
                    extractorVersion = "0.3.75+vosk-model-small-cn-0.22",
                    extractionMetadata =
                        "audioDurationMs=${transcription.audioDurationMs};" +
                            "processingDurationMs=${transcription.processingDurationMs}",
                ),
            onProgress = onProgress,
        )
    }

    private suspend fun indexExtraction(
        source: ContentSource,
        extractionResult: ExtractionResult,
        onProgress: (String) -> Unit,
    ): IngestionResult {
        currentCoroutineContext().ensureActive()
        val indexableText = requireNotNull(extractionResult.text.takeIf(String::isNotBlank)) {
            "No text was extracted from ${source.displayName}"
        }
        onProgress("Creating chunks...")
        val textChunks =
            WhiteSpaceSplitter.createChunks(
                indexableText,
                chunkSize = 500,
                chunkOverlap = 50,
            ).filter(String::isNotBlank)
        require(textChunks.isNotEmpty()) {
            "No indexable chunks were created for ${source.displayName}"
        }
        onProgress("Creating embeddings...")
        val chunks =
            textChunks.mapIndexed { index, textChunk ->
                currentCoroutineContext().ensureActive()
                onProgress("Creating embedding ${index + 1}/${textChunks.size}...")
                Chunk(
                    docFileName = source.displayName,
                    chunkData = textChunk,
                    chunkEmbedding = sentenceEncoder.encodeText(textChunk),
                )
            }
        onProgress("Saving document...")
        currentCoroutineContext().ensureActive()
        val documentId =
            documentsDB.addDocumentAndChunks(
                document =
                    Document(
                        docText = indexableText,
                        docFileName = source.displayName,
                        docAddedTime = System.currentTimeMillis(),
                        sourceType = extractionResult.sourceType.name,
                        sourceMimeType = extractionResult.mimeType.orEmpty(),
                        sourceUri = source.sourceUri,
                        contentHash = indexableText.sha256(),
                        extractorVersion = extractionResult.extractorVersion,
                        extractionMetadata = extractionResult.extractionMetadata,
                        embeddingModelVersion = EMBEDDING_MODEL_VERSION,
                    ),
                chunks = chunks,
            )
        return IngestionResult(documentId = documentId, chunkCount = chunks.size)
    }

    private fun extractDocument(source: ContentSource.Document): ExtractionResult {
        val text =
            requireNotNull(contentResolver.openInputStream(source.uri)) {
                "Unable to open ${source.displayName}"
            }.use { inputStream ->
                Readers
                    .getReaderForDocType(source.documentType)
                    .readFromInputStream(inputStream)
                    .orEmpty()
            }
        return ExtractionResult(
            text = text,
            sourceType = source.sourceType,
            mimeType = source.mimeType,
            extractorId = "reader/${source.documentType.name.lowercase()}",
            extractorVersion = READER_EXTRACTOR_VERSION,
        )
    }

    private fun String.sha256(): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(toByteArray())
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private companion object {
        const val READER_EXTRACTOR_VERSION = "1"
        const val EMBEDDING_MODEL_VERSION = "all-MiniLM-L6-v2"
    }
}
