package com.ml.shubham0204.docqa.domain.asr

import android.content.Context
import android.os.StatFs
import com.ketch.Ketch
import com.ketch.Status
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.Single
import org.vosk.Model
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.net.ssl.HttpsURLConnection

/**
 * 语音模型的完整生命周期管理器，负责下载、验证、解压、安装和加载模型
 */
@Single
class SpeechModelManager(
    private val context: Context,
) {
    private val ketch = Ketch.builder().build(context)
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    @Volatile private var downloadId: Int? = null

    suspend fun ensureModel(onProgress: (String) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            modelDirectory().takeIf(::isInstalled)?.let { installedModel ->
                onProgress("Chinese ASR model is ready")
                return@withContext installedModel
            }

            requireAvailableStorage() // 检查容量
            val archive = File(context.cacheDir, "${SpeechModelConfig.MODEL_ID}.zip")
            val temporaryRoot =
                File(
                    modelParentDirectory(),
                    ".${SpeechModelConfig.MODEL_ID}-${UUID.randomUUID()}",
                )
            try {
                onProgress("Downloading Chinese ASR model...")
                downloadArchive(archive, onProgress)
                verifyArchive(archive)

                onProgress("Verifying and unpacking ASR model...")
                unpackArchive(archive, temporaryRoot)
                val extractedModel = File(temporaryRoot, SpeechModelConfig.MODEL_ID)
                require(hasRequiredFiles(extractedModel)) { "The downloaded ASR model is incomplete." }

                onProgress("Loading Chinese ASR model...")
                verifyModelCanLoad(extractedModel)
                installExtractedModel(extractedModel)
                modelDirectory()
            } finally {
                if (pendingDownloadId() == null) {
                    archive.delete()
                }
                if (temporaryRoot.exists()) {
                    temporaryRoot.deleteRecursively()
                }
            }
        }

    fun isModelInstalled(): Boolean = isInstalled(modelDirectory())

    fun installedModelDirectory(): File? = modelDirectory().takeIf(::isInstalled)

    fun hasPendingSetup(): Boolean = pendingDownloadId() != null

    fun cancelSetup() {
        (downloadId ?: pendingDownloadId())?.let(ketch::cancel)
        clearPendingDownloadId()
    }

    /**
     * 基于 HttpURLConnection 的下载器，使用Ketch库管理下载任务，并提供进度回调
     */
    private suspend fun downloadArchive(
        archive: File,
        onProgress: (String) -> Unit,
    ) {
        archive.parentFile?.mkdirs()
        require(URL(SpeechModelConfig.ARCHIVE_URL).protocol == "https") {
            "ASR model downloads must use HTTPS."
        }
        val connection = URL(SpeechModelConfig.ARCHIVE_URL).openConnection() as HttpsURLConnection
        try {
            connection.run {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            requestMethod = "HEAD"
            connect()
            require(responseCode == HttpsURLConnection.HTTP_OK) {
                "Unable to download the ASR model (HTTP $responseCode)."
            }
            val expectedLength = contentLengthLong
            require(expectedLength in 1..SpeechModelConfig.MAX_ARCHIVE_BYTES) {
                "The ASR model download has an unexpected size."
            }
            }
        } finally {
            connection.disconnect()
        }
        val activeDownloadId =
            pendingDownloadId()
                ?: run {
                    archive.delete()
                    ketch
                        .download(
                            SpeechModelConfig.ARCHIVE_URL,
                            archive.parentFile!!.absolutePath,
                            archive.name,
                        ).also(::savePendingDownloadId)
                }
        downloadId = activeDownloadId
        try {
            val completedDownload =
                ketch
                    .observeDownloadById(activeDownloadId)
                    .filterNotNull()
                    .first { download ->
                        currentCoroutineContext().ensureActive()
                        when (download.status) {
                            Status.PROGRESS -> onProgress("Downloading Chinese ASR model: ${download.progress}%")
                            Status.FAILED -> return@first true
                            else -> Unit
                        }
                        download.status == Status.SUCCESS
                    }
            if (completedDownload.status == Status.FAILED) {
                clearPendingDownloadId()
                error("Unable to download the ASR model: ${completedDownload.failureReason}")
            }
            clearPendingDownloadId()
            require(archive.isFile) { "The ASR model download did not produce an archive." }
        } finally {
            downloadId = null
        }
    }

    private suspend fun verifyArchive(archive: File) {
        require(archive.length() in 1..SpeechModelConfig.MAX_ARCHIVE_BYTES) {
            "The ASR model archive has an unexpected size."
        }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(archive).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                currentCoroutineContext().ensureActive()
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                digest.update(buffer, 0, bytesRead)
            }
        }
        val actualHash = digest.digest().joinToString(separator = "") { "%02x".format(it) }
        require(actualHash == SpeechModelConfig.ARCHIVE_SHA256) {
            "The ASR model archive failed its integrity check."
        }
    }

    private suspend fun unpackArchive(
        archive: File,
        temporaryRoot: File,
    ) {
        require(temporaryRoot.mkdirs()) { "Unable to create the ASR model directory." }
        var expandedBytes = 0L
        ZipInputStream(BufferedInputStream(FileInputStream(archive))).use { zipInput ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = zipInput.nextEntry ?: break
                val outputFile = SpeechModelArchive.outputFile(temporaryRoot, entry.name)
                if (entry.isDirectory) {
                    require(outputFile.mkdirs() || outputFile.isDirectory) {
                        "Unable to create the ASR model directory."
                    }
                } else {
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val bytesRead = zipInput.read(buffer)
                            if (bytesRead == -1) break
                            expandedBytes += bytesRead
                            require(expandedBytes <= SpeechModelConfig.MAX_EXPANDED_BYTES) {
                                "The ASR model exceeds its unpacked size limit."
                            }
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                zipInput.closeEntry()
            }
        }
    }

    private fun installExtractedModel(extractedModel: File) {
        val destination = modelDirectory()
        if (destination.exists()) {
            require(destination.deleteRecursively()) { "Unable to replace the incomplete ASR model." }
        }
        require(extractedModel.renameTo(destination)) { "Unable to install the ASR model." }
        File(destination, SpeechModelConfig.INSTALL_MARKER_FILE).writeText(SpeechModelConfig.ARCHIVE_SHA256)
    }

    private fun verifyModelCanLoad(modelDirectory: File) {
        val model = Model(modelDirectory.absolutePath)
        model.close()
    }

    /**
     * 容量检查
     */
    private fun requireAvailableStorage() {
        val availableBytes = StatFs(context.noBackupFilesDir.path).availableBytes
        require(availableBytes >= SpeechModelConfig.MIN_AVAILABLE_STORAGE_BYTES) {
            "At least 256 MB of free storage is required for the Chinese ASR model."
        }
    }

    private fun modelParentDirectory(): File = File(context.noBackupFilesDir, "speech-models")

    private fun modelDirectory(): File = File(modelParentDirectory(), SpeechModelConfig.MODEL_ID)

    private fun isInstalled(directory: File): Boolean =
        hasRequiredFiles(directory) &&
            File(directory, SpeechModelConfig.INSTALL_MARKER_FILE)
                .takeIf(File::isFile)
                ?.readText() == SpeechModelConfig.ARCHIVE_SHA256

    private fun hasRequiredFiles(directory: File): Boolean =
        File(directory, SpeechModelConfig.REQUIRED_MODEL_FILE).isFile

    private fun pendingDownloadId(): Int? =
        preferences
            .getInt(PREFERENCE_PENDING_DOWNLOAD_ID, NO_PENDING_DOWNLOAD_ID)
            .takeIf { it != NO_PENDING_DOWNLOAD_ID }

    private fun savePendingDownloadId(id: Int) {
        preferences.edit().putInt(PREFERENCE_PENDING_DOWNLOAD_ID, id).apply()
    }

    private fun clearPendingDownloadId() {
        preferences.edit().remove(PREFERENCE_PENDING_DOWNLOAD_ID).apply()
    }

    private companion object {
        const val BUFFER_SIZE = 32 * 1024
        const val CONNECTION_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val PREFERENCES_NAME = "speech_model_manager"
        const val PREFERENCE_PENDING_DOWNLOAD_ID = "pending_download_id"
        const val NO_PENDING_DOWNLOAD_ID = -1
    }
}
