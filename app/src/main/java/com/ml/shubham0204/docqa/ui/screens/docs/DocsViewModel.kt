package com.ml.shubham0204.docqa.ui.screens.docs

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.docqa.data.Document
import com.ml.shubham0204.docqa.data.DocumentsDB
import com.ml.shubham0204.docqa.domain.asr.SpeechModelManager
import com.ml.shubham0204.docqa.domain.asr.VoskTranscriber
import com.ml.shubham0204.docqa.domain.ingestion.ContentIngestionUseCase
import com.ml.shubham0204.docqa.domain.ingestion.ContentSource
import com.ml.shubham0204.docqa.domain.readers.Readers
import com.ml.shubham0204.docqa.domain.readers.getMimeType
import hideProgressDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import setProgressDialogText
import showProgressDialog
import java.io.File
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import kotlin.math.min

sealed interface DocsScreenUIEvent {
    data class OnDocSelected(
        val fileUri: Uri,
        val docType: Readers.DocumentType,
    ) : DocsScreenUIEvent

    data class OnImageSelected(
        val imageUri: Uri,
    ) : DocsScreenUIEvent

    data class OnAudioSelected(
        val audioUri: Uri,
    ) : DocsScreenUIEvent

    data class OnAudioImportSelected(
        val audioUri: Uri,
    ) : DocsScreenUIEvent

    data object OnSpeechModelSetup : DocsScreenUIEvent

    data object OnSpeechModelSetupCancelled : DocsScreenUIEvent

    data object OnAudioPocCancelled : DocsScreenUIEvent

    data object OnAudioImportCancelled : DocsScreenUIEvent

    data class OnDocURLSubmitted(
        val context: Context,
        val url: String,
        val docType: Readers.DocumentType,
    ) : DocsScreenUIEvent

    data class OnRemoveDoc(
        val docId: Long,
    ) : DocsScreenUIEvent

    data object OnImportMessageShown : DocsScreenUIEvent
}

enum class DocDownloadState {
    DOWNLOAD_NONE,
    DOWNLOAD_IN_PROGRESS,
    DOWNLOAD_SUCCESS,
    DOWNLOAD_FAILURE,
}

data class SpeechModelUIState(
    val status: String = "尚未安装中文语音识别模型",
    val isBusy: Boolean = false,
    val isReady: Boolean = false,
)

data class AudioPocUIState(
    val status: String = "请选择音频文件以运行离线转写测试",
    val isBusy: Boolean = false,
    val transcript: String? = null,
)

data class AudioImportUIState(
    val status: String = "请选择音频文件以将离线转写内容加入知识库",
    val isBusy: Boolean = false,
)

data class DocsScreenUIState(
    val documents: List<Document> = emptyList(),
    val docDownloadState: DocDownloadState = DocDownloadState.DOWNLOAD_NONE,
    val importMessage: String? = null,
    val speechModel: SpeechModelUIState = SpeechModelUIState(),
    val audioPoc: AudioPocUIState = AudioPocUIState(),
    val audioImport: AudioImportUIState = AudioImportUIState(),
)

@KoinViewModel
class DocsViewModel(
    private val contentResolver: ContentResolver,
    private val documentsDB: DocumentsDB,
    private val contentIngestionUseCase: ContentIngestionUseCase,
    private val speechModelManager: SpeechModelManager,
    private val voskTranscriber: VoskTranscriber,
) : ViewModel() {
    private val _docsScreenUIState = MutableStateFlow(DocsScreenUIState())
    val docsScreenUIState: StateFlow<DocsScreenUIState> = _docsScreenUIState
    private var speechModelSetupJob: Job? = null
    private var audioPocJob: Job? = null
    private var audioImportJob: Job? = null

    init {
        viewModelScope.launch {
            documentsDB.getAllDocuments().collect {
                _docsScreenUIState.value = _docsScreenUIState.value.copy(documents = it)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val isInstalled = speechModelManager.isModelInstalled()
            val hasPendingSetup = speechModelManager.hasPendingSetup()
            updateSpeechModelState(
                SpeechModelUIState(
                    status =
                        if (isInstalled) {
                            "中文语音识别模型已就绪"
                        } else if (hasPendingSetup) {
                            "可继续配置中文语音识别模型"
                        } else {
                            "尚未安装中文语音识别模型"
                        },
                    isReady = isInstalled,
                ),
            )
        }
    }

    fun onEvent(event: DocsScreenUIEvent) {
        when (event) {
            is DocsScreenUIEvent.OnDocSelected -> {
                showProgressDialog()
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        contentIngestionUseCase.ingestDocument(
                            ContentSource.Document(
                                uri = event.fileUri,
                                displayName = getDocumentDisplayName(event.fileUri),
                                mimeType = event.docType.getMimeType(),
                                documentType = event.docType,
                            ),
                            { setProgressDialogText(localizedProgress(it)) },
                        )
                    } finally {
                        withContext(Dispatchers.Main) {
                            hideProgressDialog()
                        }
                    }
                }
            }

            is DocsScreenUIEvent.OnDocURLSubmitted -> {
                showProgressDialog()
                viewModelScope.launch(Dispatchers.IO) {
                    var connection: HttpURLConnection? = null
                    var cachedDocument: File? = null
                    try {
                        connection = URL(event.url).openConnection() as HttpURLConnection
                        connection.connect()
                        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                            val docFileName = getFileNameFromURL(event.url).ifBlank { "下载资料" }
                            cachedDocument = File(event.context.cacheDir, docFileName)

                            connection.inputStream.use { inputStream ->
                                cachedDocument.outputStream().use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }

                            contentIngestionUseCase.ingestDocument(
                                ContentSource.Document(
                                    uri = Uri.fromFile(cachedDocument),
                                    sourceUri = event.url,
                                    displayName = docFileName,
                                    mimeType = event.docType.getMimeType(),
                                    documentType = event.docType,
                                ),
                                { setProgressDialogText(localizedProgress(it)) },
                            )
                            withContext(Dispatchers.Main) {
                                _docsScreenUIState.value =
                                    _docsScreenUIState.value.copy(
                                        docDownloadState = DocDownloadState.DOWNLOAD_SUCCESS,
                                    )
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                _docsScreenUIState.value =
                                    _docsScreenUIState.value.copy(
                                        docDownloadState = DocDownloadState.DOWNLOAD_FAILURE,
                                    )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            _docsScreenUIState.value =
                                _docsScreenUIState.value.copy(
                                    docDownloadState = DocDownloadState.DOWNLOAD_FAILURE,
                                )
                        }
                    } finally {
                        connection?.disconnect()
                        cachedDocument?.delete()
                        withContext(Dispatchers.Main) {
                            hideProgressDialog()
                        }
                    }
                }
            }

            is DocsScreenUIEvent.OnImageSelected -> {
                showProgressDialog()
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        contentIngestionUseCase.ingestImage(
                            ContentSource.Image(
                                uri = event.imageUri,
                                displayName = getDocumentDisplayName(event.imageUri),
                                mimeType = contentResolver.getType(event.imageUri),
                            ),
                            { setProgressDialogText(localizedProgress(it)) },
                        )
                        setImportMessage("图片文字已加入知识库")
                    } catch (exception: Exception) {
                        setImportMessage("无法识别所选图片中的文字")
                    } finally {
                        withContext(Dispatchers.Main) {
                            hideProgressDialog()
                        }
                    }
                }
            }

            is DocsScreenUIEvent.OnAudioSelected -> runAudioPoc(event.audioUri)

            is DocsScreenUIEvent.OnAudioImportSelected -> importAudio(event.audioUri)

            DocsScreenUIEvent.OnSpeechModelSetup -> setupSpeechModel()

            DocsScreenUIEvent.OnSpeechModelSetupCancelled -> {
                speechModelManager.cancelSetup()
                speechModelSetupJob?.cancel()
            }

            DocsScreenUIEvent.OnAudioPocCancelled -> audioPocJob?.cancel()

            DocsScreenUIEvent.OnAudioImportCancelled -> audioImportJob?.cancel()

            is DocsScreenUIEvent.OnRemoveDoc -> {
                documentsDB.removeDocumentAndChunks(event.docId)
            }

            DocsScreenUIEvent.OnImportMessageShown -> {
                _docsScreenUIState.value = _docsScreenUIState.value.copy(importMessage = null)
            }
        }
    }

    private suspend fun setImportMessage(message: String) {
        withContext(Dispatchers.Main) {
            _docsScreenUIState.value = _docsScreenUIState.value.copy(importMessage = message)
        }
    }

    private fun setupSpeechModel() {
        if (speechModelSetupJob?.isActive == true) return
        speechModelSetupJob =
            viewModelScope.launch(Dispatchers.IO) {
                updateSpeechModelState(
                    SpeechModelUIState(
                        status = "正在准备中文语音识别模型…",
                        isBusy = true,
                    ),
                )
                try {
                    speechModelManager.ensureModel { progress ->
                        updateSpeechModelState(
                            SpeechModelUIState(
                                status = localizedProgress(progress),
                                isBusy = true,
                            ),
                        )
                    }
                    updateSpeechModelState(
                        SpeechModelUIState(
                            status = "中文语音识别模型已就绪",
                            isReady = true,
                        ),
                    )
                    setImportMessage("中文语音识别模型已就绪，可运行离线转写测试")
                } catch (_: CancellationException) {
                    updateSpeechModelState(
                        SpeechModelUIState(status = "已取消中文语音识别模型配置"),
                    )
                } catch (exception: Exception) {
                    updateSpeechModelState(
                        SpeechModelUIState(
                            status = "无法配置中文语音识别模型，请稍后重试",
                        ),
                    )
                }
            }
    }

    private fun updateSpeechModelState(state: SpeechModelUIState) {
        _docsScreenUIState.value = _docsScreenUIState.value.copy(speechModel = state)
    }

    private fun runAudioPoc(audioUri: Uri) {
        if (audioPocJob?.isActive == true) return
        audioPocJob =
            viewModelScope.launch(Dispatchers.IO) {
                if (!speechModelManager.isModelInstalled()) {
                    updateAudioPocState(
                        AudioPocUIState(
                            status = "请先配置中文语音识别模型，再选择音频文件。",
                        ),
                    )
                    return@launch
                }
                updateAudioPocState(AudioPocUIState(status = "正在打开音频以进行离线转写…", isBusy = true))
                try {
                    val result =
                        voskTranscriber.transcribe(
                            source =
                                ContentSource.Audio(
                                    uri = audioUri,
                                    displayName = getDocumentDisplayName(audioUri),
                                    mimeType = contentResolver.getType(audioUri),
                                ),
                            onProgress = { progress ->
                                updateAudioPocState(AudioPocUIState(status = localizedProgress(progress), isBusy = true))
                            },
                        )
                    updateAudioPocState(
                        AudioPocUIState(
                            status =
                                "离线转写测试已完成：${result.mimeType}，音频时长 ${result.audioDurationMs / 1_000} 秒，" +
                                    "处理耗时 ${result.processingDurationMs / 1_000.0} 秒；结果未建立索引。",
                            transcript = result.text.ifBlank { "未识别到语音。" },
                        ),
                    )
                } catch (_: CancellationException) {
                    updateAudioPocState(AudioPocUIState(status = "已取消离线转写测试。"))
                } catch (exception: Exception) {
                    updateAudioPocState(
                        AudioPocUIState(
                            status = "无法转写所选音频，请稍后重试。",
                        ),
                    )
                }
            }
    }

    private fun updateAudioPocState(state: AudioPocUIState) {
        _docsScreenUIState.value = _docsScreenUIState.value.copy(audioPoc = state)
    }

    private fun importAudio(audioUri: Uri) {
        if (audioImportJob?.isActive == true) return
        audioImportJob =
            viewModelScope.launch(Dispatchers.IO) {
                if (!speechModelManager.isModelInstalled()) {
                    updateAudioImportState(
                        AudioImportUIState(
                            status = "请先配置中文语音识别模型，再导入音频。",
                        ),
                    )
                    return@launch
                }
                updateAudioImportState(
                    AudioImportUIState(
                        status = "正在准备音频以进行离线转写…",
                        isBusy = true,
                    ),
                )
                try {
                    val result =
                        contentIngestionUseCase.ingestAudio(
                            source =
                                ContentSource.Audio(
                                    uri = audioUri,
                                    displayName = getDocumentDisplayName(audioUri),
                                    mimeType = contentResolver.getType(audioUri),
                                ),
                            onProgress = { progress ->
                                updateAudioImportState(
                                    AudioImportUIState(status = localizedProgress(progress), isBusy = true),
                                )
                            },
                        )
                    updateAudioImportState(
                        AudioImportUIState(
                            status = "音频已加入知识库（${result.chunkCount} 个片段）。",
                        ),
                    )
                    setImportMessage("音频转写内容已加入知识库")
                } catch (_: CancellationException) {
                    updateAudioImportState(AudioImportUIState(status = "已取消音频导入。"))
                } catch (exception: Exception) {
                    updateAudioImportState(
                        AudioImportUIState(
                            status = "无法将所选音频加入知识库，请稍后重试。",
                        ),
                    )
                }
            }
    }

    private fun updateAudioImportState(state: AudioImportUIState) {
        _docsScreenUIState.value = _docsScreenUIState.value.copy(audioImport = state)
    }

    private fun localizedProgress(progress: String): String =
        when {
            progress == "Extracting document text..." -> "正在提取文档文字…"
            progress == "No embedded PDF text found. Starting OCR..." -> "未检测到 PDF 内嵌文字，正在开始 OCR…"
            progress == "Recognizing image text..." -> "正在识别图片文字…"
            progress == "Transcribing audio offline..." -> "正在离线转写音频…"
            progress == "Creating chunks..." -> "正在切分文本…"
            progress == "Creating embeddings..." -> "正在生成向量…"
            progress.startsWith("Creating embedding ") ->
                "正在生成向量 ${progress.removePrefix("Creating embedding ")}"
            progress == "Saving document..." -> "正在保存资料…"
            progress == "Chinese ASR model is ready" -> "中文语音识别模型已就绪"
            progress == "Downloading Chinese ASR model..." -> "正在下载中文语音识别模型…"
            progress.startsWith("Downloading Chinese ASR model: ") ->
                "正在下载中文语音识别模型：${progress.removePrefix("Downloading Chinese ASR model: ")}"
            progress == "Verifying and unpacking ASR model..." -> "正在校验并解压语音识别模型…"
            progress == "Loading Chinese ASR model..." -> "正在加载中文语音识别模型…"
            progress == "Transcribing Chinese audio offline..." -> "正在离线转写中文音频…"
            else -> "正在处理资料…"
        }

    private fun getDocumentDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return "未命名资料"
    }

    // Extracts the file name from the URL
    // Source: https://stackoverflow.com/a/11576046/13546426
    private fun getFileNameFromURL(url: String?): String {
        if (url == null) {
            return ""
        }
        try {
            val resource = URL(url)
            val host = resource.host
            if (host.isNotEmpty() && url.endsWith(host)) {
                return ""
            }
        } catch (e: MalformedURLException) {
            return ""
        }
        val startIndex = url.lastIndexOf('/') + 1
        val length = url.length
        var lastQMPos = url.lastIndexOf('?')
        if (lastQMPos == -1) {
            lastQMPos = length
        }
        var lastHashPos = url.lastIndexOf('#')
        if (lastHashPos == -1) {
            lastHashPos = length
        }
        val endIndex = min(lastQMPos.toDouble(), lastHashPos.toDouble()).toInt()
        return url.substring(startIndex, endIndex)
    }
}
