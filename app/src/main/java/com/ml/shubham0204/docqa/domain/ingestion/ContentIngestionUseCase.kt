package com.ml.shubham0204.docqa.domain.ingestion

import android.content.ContentResolver
import com.ml.shubham0204.docqa.data.Chunk
import com.ml.shubham0204.docqa.data.Document
import com.ml.shubham0204.docqa.data.DocumentsDB
import com.ml.shubham0204.docqa.domain.SentenceEmbeddingProvider
import com.ml.shubham0204.docqa.domain.WhiteSpaceSplitter
import com.ml.shubham0204.docqa.domain.readers.Readers
import org.koin.core.annotation.Single
import java.security.MessageDigest

@Single
class ContentIngestionUseCase(
    private val contentResolver: ContentResolver,
    private val documentsDB: DocumentsDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
    private val imageOcrExtractor: ImageOcrExtractor,
) {
    suspend fun ingestDocument(
        source: ContentSource.Document,
        onProgress: (String) -> Unit = {},
    ): IngestionResult {
        val extractionResult = extractDocument(source)
        return indexExtraction(source, extractionResult, onProgress)
    }

    suspend fun ingestImage(
        source: ContentSource.Image,
        onProgress: (String) -> Unit = {},
    ): IngestionResult {
        onProgress("Recognizing image text...")
        return indexExtraction(source, imageOcrExtractor.extract(source), onProgress)
    }

    private fun indexExtraction(
        source: ContentSource,
        extractionResult: ExtractionResult,
        onProgress: (String) -> Unit,
    ): IngestionResult {
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
                onProgress("Creating embedding ${index + 1}/${textChunks.size}...")
                Chunk(
                    docFileName = source.displayName,
                    chunkData = textChunk,
                    chunkEmbedding = sentenceEncoder.encodeText(textChunk),
                )
            }
        onProgress("Saving document...")
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
                requireNotNull(
                    Readers
                        .getReaderForDocType(source.documentType)
                        .readFromInputStream(inputStream),
                ) { "Unable to extract text from ${source.displayName}" }
            }
        return ExtractionResult(
            text = requireNotNull(text.takeIf(String::isNotBlank)) {
                "No text was extracted from ${source.displayName}"
            },
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
