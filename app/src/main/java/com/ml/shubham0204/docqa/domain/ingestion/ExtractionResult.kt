package com.ml.shubham0204.docqa.domain.ingestion

/**
 * 抽取出来的文本内容，包含文本内容、来源类型、MIME类型、抽取器ID、抽取器版本、抽取元数据等信息
 */
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
