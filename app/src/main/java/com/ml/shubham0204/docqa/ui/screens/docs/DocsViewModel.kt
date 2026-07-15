package com.ml.shubham0204.docqa.ui.screens.docs

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.docqa.data.Document
import com.ml.shubham0204.docqa.data.DocumentsDB
import com.ml.shubham0204.docqa.domain.ingestion.ContentIngestionUseCase
import com.ml.shubham0204.docqa.domain.ingestion.ContentSource
import com.ml.shubham0204.docqa.domain.readers.Readers
import com.ml.shubham0204.docqa.domain.readers.getMimeType
import hideProgressDialog
import kotlinx.coroutines.Dispatchers
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

    data class OnDocURLSubmitted(
        val context: Context,
        val url: String,
        val docType: Readers.DocumentType,
    ) : DocsScreenUIEvent

    data class OnRemoveDoc(
        val docId: Long,
    ) : DocsScreenUIEvent
}

enum class DocDownloadState {
    DOWNLOAD_NONE,
    DOWNLOAD_IN_PROGRESS,
    DOWNLOAD_SUCCESS,
    DOWNLOAD_FAILURE,
}

data class DocsScreenUIState(
    val documents: List<Document> = emptyList(),
    val docDownloadState: DocDownloadState = DocDownloadState.DOWNLOAD_NONE,
)

@KoinViewModel
class DocsViewModel(
    private val contentResolver: ContentResolver,
    private val documentsDB: DocumentsDB,
    private val contentIngestionUseCase: ContentIngestionUseCase,
) : ViewModel() {
    private val _docsScreenUIState = MutableStateFlow(DocsScreenUIState())
    val docsScreenUIState: StateFlow<DocsScreenUIState> = _docsScreenUIState

    init {
        viewModelScope.launch {
            documentsDB.getAllDocuments().collect {
                _docsScreenUIState.value = _docsScreenUIState.value.copy(documents = it)
            }
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

            is DocsScreenUIEvent.OnRemoveDoc -> {
                documentsDB.removeDocumentAndChunks(event.docId)
            }
        }
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
