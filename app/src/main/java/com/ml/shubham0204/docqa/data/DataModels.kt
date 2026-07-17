package com.ml.shubham0204.docqa.data

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index

/**
 * 定义分块、文档、检索上下文的数据模型
 */
@Entity
data class Chunk(
    @Id var chunkId: Long = 0,
    @Index var docId: Long = 0,
    var docFileName: String = "",
    var chunkData: String = "",
    @HnswIndex(dimensions = 384) var chunkEmbedding: FloatArray = floatArrayOf(),
)

@Entity
data class Document(
    @Id var docId: Long = 0,
    var docText: String = "",
    var docFileName: String = "",
    var docAddedTime: Long = 0,
    var sourceType: String = "DOCUMENT",
    var sourceMimeType: String = "",
    var sourceUri: String = "",
    var contentHash: String = "",
    var extractorVersion: String = "",
    var extractionMetadata: String = "",
    var embeddingModelVersion: String = "",
)

data class RetrievedContext(
    val fileName: String,
    val context: String,
)
