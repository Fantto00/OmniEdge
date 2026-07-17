package com.ml.shubham0204.docqa.domain.ingestion

import android.net.Uri
import com.ml.shubham0204.docqa.domain.readers.Readers

/**
 * 定义用户选中的文件类型：文档/图片/音频
 */
sealed interface ContentSource {
    val uri: Uri
    val sourceUri: String
    val displayName: String
    val mimeType: String?
    val sourceType: SourceType

    data class Document(
        override val uri: Uri,
        override val displayName: String,
        override val mimeType: String?,
        val documentType: Readers.DocumentType,
        override val sourceUri: String = uri.toString(),
    ) : ContentSource {
        override val sourceType: SourceType = SourceType.DOCUMENT
    }

    data class Image(
        override val uri: Uri,
        override val displayName: String,
        override val mimeType: String?,
        override val sourceUri: String = uri.toString(),
    ) : ContentSource {
        override val sourceType: SourceType = SourceType.IMAGE
    }

    data class Audio(
        override val uri: Uri,
        override val displayName: String,
        override val mimeType: String?,
        override val sourceUri: String = uri.toString(),
    ) : ContentSource {
        override val sourceType: SourceType = SourceType.AUDIO
    }
}

enum class SourceType {
    DOCUMENT,
    IMAGE,
    AUDIO,
}
