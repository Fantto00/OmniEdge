package com.ml.shubham0204.docqa.domain.asr

import java.io.File

/**
 * 校验路径、模型写入
 */
object SpeechModelArchive {
    fun outputFile(rootDirectory: File, entryName: String): File {
        require(entryName.isNotBlank()) { "The model archive contains an empty entry." }
        val rootPath = rootDirectory.canonicalFile.path + File.separator
        val outputFile = File(rootDirectory, entryName).canonicalFile
        require(outputFile.path.startsWith(rootPath)) {
            "The model archive contains an unsafe entry."
        }
        return outputFile
    }
}
