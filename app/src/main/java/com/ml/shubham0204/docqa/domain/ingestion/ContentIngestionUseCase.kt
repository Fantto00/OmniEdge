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

/**
 * 负责整个业务流程，从用户导入文档到入库
 */
@Single
class ContentIngestionUseCase(
    private val contentResolver: ContentResolver,
    private val documentsDB: DocumentsDB,
    private val sentenceEncoder: SentenceEmbeddingProvider,
    private val imageOcrExtractor: ImageOcrExtractor,
    private val scannedPdfOcrExtractor: ScannedPdfOcrExtractor,
    private val voskTranscriber: VoskTranscriber,
) {
    /**
     * 文档内容处理并入库
     */
    suspend fun ingestDocument(
        source: ContentSource.Document,
        onProgress: (String) -> Unit = {},
    ): IngestionResult {
        onProgress("Extracting document text...")
        val primaryExtraction = extractDocument(source) // 先提取文档文本
        val extractionResult =
            if (
                //是pdf且小于80字：走OCR
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

    /**
     * 图片内容处理并入库
     */
    suspend fun ingestImage(
        source: ContentSource.Image,
        onProgress: (String) -> Unit = {},
    ): IngestionResult {
        onProgress("Recognizing image text...")
        return indexExtraction(source, imageOcrExtractor.extract(source), onProgress)
    }

    /**
     * 音频内容处理并入库
     */
    suspend fun ingestAudio(
        source: ContentSource.Audio,
        onProgress: (String) -> Unit = {},
    ): IngestionResult {
        onProgress("Transcribing audio offline...")
        val transcription = voskTranscriber.transcribe(source, onProgress) // 调用 Vosk 模型能力，把音频转成原始文本
        val indexableText = AudioTranscriptNormalizer.normalizeForIndexing(transcription.text) // Vosk 出来的文本有空格，需要去掉
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


    /**
     * 把抽取好的文本变成持久化的向量数据：入库过程
     * 怎么实现正确入库，在取消的时候不留脏数据？
     * 取消支持：每步重型操作前都插入了取消点，也就是ensureActive()
     * 不留脏数据：通过 ObjectBox的 runInTx 实现事务（ runInTx 自带事务属性）
     */
    private suspend fun indexExtraction(
        source: ContentSource,
        extractionResult: ExtractionResult,
        onProgress: (String) -> Unit,
    ): IngestionResult {
        currentCoroutineContext().ensureActive() // ensureActive：在协程中检查是否被取消
        //先保证非空文本
        val indexableText = requireNotNull(extractionResult.text.takeIf(String::isNotBlank)) {
            "No text was extracted from ${source.displayName}"
        }
        onProgress("Creating chunks...")
        // 调用 WhiteSpaceSplitter.createChunks() 方法分块
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
        // 逐块嵌入为chunk
        val chunks =
            textChunks.mapIndexed { index, textChunk ->
                currentCoroutineContext().ensureActive()
                onProgress("Creating embedding ${index + 1}/${textChunks.size}...")
                Chunk(
                    docFileName = source.displayName,
                    chunkData = textChunk,
                    chunkEmbedding = sentenceEncoder.encodeText(textChunk), // 每块文本调用 SentenceEmbeddingProvider 编码为向量
                )
            }
        onProgress("Saving document...")
        currentCoroutineContext().ensureActive()
        //原子入库：文档和块一起入库
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

    /**
     * 处理文档类来源（PDF、DOCX、Markdown、TXT）的文本抽取
     */
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
