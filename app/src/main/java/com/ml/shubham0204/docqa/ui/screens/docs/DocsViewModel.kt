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

    data object OnSpeechModelSetup : DocsScreenUIEvent

    data object OnSpeechModelSetupCancelled : DocsScreenUIEvent

    data object OnAudioPocCancelled : DocsScreenUIEvent

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
    val status: String = "Chinese ASR model is not installed",
    val isBusy: Boolean = false,
    val isReady: Boolean = false,
)

data class AudioPocUIState(
    val status: String = "Select an audio file to run the offline ASR POC",
    val isBusy: Boolean = false,
    val transcript: String? = null,
)

data class DocsScreenUIState(
    val documents: List<Document> = emptyList(),
    val docDownloadState: DocDownloadState = DocDownloadState.DOWNLOAD_NONE,
    val importMessage: String? = null,
    val speechModel: SpeechModelUIState = SpeechModelUIState(),
    val audioPoc: AudioPocUIState = AudioPocUIState(),
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
                            "Chinese ASR model is ready"
                        } else if (hasPendingSetup) {
                            "Chinese ASR model setup can be resumed"
                        } else {
                            "Chinese ASR model is not installed"
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
                            ::setProgressDialogText,
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
                            val docFileName = getFileNameFromURL(event.url).ifBlank { "downloaded-document" }
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
                                ::setProgressDialogText,
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
                            ::setProgressDialogText,
                        )
                        setImportMessage("Image text added to the knowledge base")
                    } catch (exception: Exception) {
                        setImportMessage(exception.message ?: "Unable to recognize text in the selected image")
                    } finally {
                        withContext(Dispatchers.Main) {
                            hideProgressDialog()
                        }
                    }
                }
            }

            is DocsScreenUIEvent.OnAudioSelected -> runAudioPoc(event.audioUri)

            DocsScreenUIEvent.OnSpeechModelSetup -> setupSpeechModel()

            DocsScreenUIEvent.OnSpeechModelSetupCancelled -> {
                speechModelManager.cancelSetup()
                speechModelSetupJob?.cancel()
            }

            DocsScreenUIEvent.OnAudioPocCancelled -> audioPocJob?.cancel()

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
                        status = "Preparing Chinese ASR model...",
                        isBusy = true,
                    ),
                )
                try {
                    speechModelManager.ensureModel { progress ->
                        updateSpeechModelState(
                            SpeechModelUIState(
                                status = progress,
                                isBusy = true,
                            ),
                        )
                    }
                    updateSpeechModelState(
                        SpeechModelUIState(
                            status = "Chinese ASR model is ready",
                            isReady = true,
                        ),
                    )
                    setImportMessage("Chinese ASR model is ready for the audio POC")
                } catch (_: CancellationException) {
                    updateSpeechModelState(
                        SpeechModelUIState(status = "Chinese ASR model setup was cancelled"),
                    )
                } catch (exception: Exception) {
                    updateSpeechModelState(
                        SpeechModelUIState(
                            status = exception.message ?: "Unable to set up the Chinese ASR model",
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
                            status = "Set up the Chinese ASR model before selecting audio.",
                        ),
                    )
                    return@launch
                }
                updateAudioPocState(AudioPocUIState(status = "Opening audio for offline transcription...", isBusy = true))
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
                                updateAudioPocState(AudioPocUIState(status = progress, isBusy = true))
                            },
                        )
                    updateAudioPocState(
                        AudioPocUIState(
                            status =
                                "POC completed: ${result.mimeType}, ${result.audioDurationMs / 1_000}s audio, " +
                                    "${result.processingDurationMs / 1_000.0}s processing. Not indexed.",
                            transcript = result.text.ifBlank { "No speech detected." },
                        ),
                    )
                } catch (_: CancellationException) {
                    updateAudioPocState(AudioPocUIState(status = "Audio transcription POC was cancelled."))
                } catch (exception: Exception) {
                    updateAudioPocState(
                        AudioPocUIState(
                            status = exception.message ?: "Unable to transcribe the selected audio.",
                        ),
                    )
                }
            }
    }

    private fun updateAudioPocState(state: AudioPocUIState) {
        _docsScreenUIState.value = _docsScreenUIState.value.copy(audioPoc = state)
    }

    private fun getDocumentDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return "document"
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
