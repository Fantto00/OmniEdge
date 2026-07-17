package com.ml.shubham0204.docqa.data

import io.objectbox.kotlin.flow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import org.koin.core.annotation.Single

/**
 * 和文档相关的数据库操作类
 */
@Single
class DocumentsDB {
    private val docsBox = ObjectBoxStore.store.boxFor(Document::class.java)
    private val chunksBox = ObjectBoxStore.store.boxFor(Chunk::class.java)

    /**
     * 把文档和分块一起添加到数据库中，并返回文档的ID
     */
    fun addDocumentAndChunks(
        document: Document,
        chunks: List<Chunk>,
    ): Long {
        var docId = 0L
        ObjectBoxStore.store.runInTx {
            docId = docsBox.put(document)
            chunks.forEach { chunk ->
                chunk.docId = docId
                chunksBox.put(chunk)
            }
        }
        return docId
    }

    fun removeDocumentAndChunks(docId: Long) {
        ObjectBoxStore.store.runInTx {
            val chunkIds =
                chunksBox
                    .query(Chunk_.docId.equal(docId))
                    .build()
                    .findIds()
                    .toList()
            chunksBox.removeByIds(chunkIds)
            docsBox.remove(docId)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getAllDocuments(): Flow<MutableList<Document>> =
        docsBox
            .query(Document_.docId.notNull())
            .build()
            .flow()
            .flowOn(Dispatchers.IO)

    fun getDocsCount(): Long = docsBox.count()
}
