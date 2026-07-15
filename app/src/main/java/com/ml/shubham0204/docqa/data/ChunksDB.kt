package com.ml.shubham0204.docqa.data

import org.koin.core.annotation.Single

@Single
class ChunksDB {
    private val chunksBox = ObjectBoxStore.store.boxFor(Chunk::class.java)

    fun addChunk(chunk: Chunk) {
        chunksBox.put(chunk)
    }

    fun getSimilarChunks(
        queryEmbedding: FloatArray,
        n: Int = 5,
    ): List<Pair<Float, Chunk>> {
        val totalChunks = chunksBox.count()
        if (totalChunks == 0L) return emptyList()

        val efSearch = minOf(maxOf(n * 5, 25), totalChunks.toInt())
        return chunksBox
            .query(Chunk_.chunkEmbedding.nearestNeighbors(queryEmbedding, efSearch))
            .build()
            .findWithScores()
            .map { Pair(it.score.toFloat(), it.get()) }
            .take(n)
    }

    fun removeChunks(docId: Long) {
        chunksBox.removeByIds(
            chunksBox
                .query(Chunk_.docId.equal(docId))
                .build()
                .findIds()
                .toList(),
        )
    }
}
