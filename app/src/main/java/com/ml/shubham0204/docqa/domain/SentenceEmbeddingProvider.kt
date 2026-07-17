package com.ml.shubham0204.docqa.domain

import android.content.Context
import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.koin.core.annotation.Single
import java.io.File

/**
 * 把文本切块转化为向量的本地推理引擎，通过 all-MiniLM-L6-V2 ONNX 模型实现，是 RAG 链路的出口
 */
@Single
class SentenceEmbeddingProvider(
    private val context: Context,
) {
    // SentenceEmbedding：原作者封装好的本地 ONNX 推理库，用于从文本生成 384 维的向量
    private val sentenceEmbedding = SentenceEmbedding()

    init {
        val onnxLocalFile = copyToLocalStorage("all-MiniLM-L6-V2.onnx") // 加载模型到私有目录
        val tokenizerLocalFile = copyToLocalStorage("tokenizer.json") // 加载分词表
        val tokenizerBytes = tokenizerLocalFile.readBytes() // 阅读分词表
        runBlocking(Dispatchers.IO) { // 短暂阻塞io线程用于初始化 ONNX RAG 模型
            sentenceEmbedding.init(
                onnxLocalFile.absolutePath,
                tokenizerBytes,
                useTokenTypeIds = false,
                outputTensorName = "last_hidden_state",
                normalizeEmbeddings = false,
            )
        }
    }

    fun encodeText(text: String): FloatArray =
        runBlocking(Dispatchers.Default) {
            return@runBlocking sentenceEmbedding.encode(text)
        }

    // Copies the file from the assets folder to the app's internal
    // storage. Files stored in the assets folder are not accessible with
    // a `File` object that makes handling difficult.
    // 翻译：assets 里的文件不能用常规 File 方式操作，使用限制很大，
    // 所以需要先把它拷贝一份到 App 的私有本地文件夹，之后就能像普通本地文件一样随意处理了。
    private fun copyToLocalStorage(filename: String): File {
        val storageFile = File(context.filesDir, filename)
        if (!storageFile.exists()) {
            val tokenizerBytes = context.assets.open(filename).readBytes()
            if (!storageFile.exists()) {
                storageFile.writeBytes(tokenizerBytes)
            }
            return storageFile
        } else {
            return storageFile
        }
    }
}
