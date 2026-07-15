package com.ml.shubham0204.docqa.domain.ingestion

data class ExtractionResult(
    val text: String,
    val sourceType: SourceType,
    val mimeType: String?,
    val extractorId: String,
    val extractorVersion: String,
    val extractionMetadata: String = "",
)

data class IngestionResult(
    val documentId: Long,
    val chunkCount: Int,
)
