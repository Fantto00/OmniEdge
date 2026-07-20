package com.ml.shubham0204.docqa.ui.screens.docs

import AppProgressDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ml.shubham0204.docqa.data.Document
import com.ml.shubham0204.docqa.domain.readers.Readers
import com.ml.shubham0204.docqa.domain.readers.getMimeType
import com.ml.shubham0204.docqa.R
import com.ml.shubham0204.docqa.ui.components.AppAlertDialog
import com.ml.shubham0204.docqa.ui.components.createAlertDialog
import com.ml.shubham0204.docqa.ui.theme.DocQATheme

private val showDocDetailDialog = mutableStateOf(false)
private val dialogDoc = mutableStateOf<Document?>(null)

@Preview
@Composable
private fun DocsScreenPreview() {
    val uiState =
        DocsScreenUIState(
            documents =
                listOf(
                    Document(
                        docId = 1,
                        docFileName = "Document 1",
                        docText = "Text 1",
                        docAddedTime = 0
                    ),
                    Document(
                        docId = 2,
                        docFileName = "Document 2",
                        docText = "Text 2",
                        docAddedTime = 0
                    ),
                ),
            docDownloadState = DocDownloadState.DOWNLOAD_NONE,
        )
    DocsScreen(uiState = uiState, onBackClick = {}, onEvent = {})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocsScreen(
    uiState: DocsScreenUIState,
    onBackClick: (() -> Unit),
    onEvent: (DocsScreenUIEvent) -> Unit,
) {
    DocQATheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.screen_docs_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.a11y_navigate_back),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()) {
                Spacer(modifier = Modifier.height(12.dp))
                DocsList(uiState.documents, onEvent)
                DocOperations(uiState, onEvent)
                AppProgressDialog()
                AppAlertDialog()
                DocDetailDialog()
            }
        }
    }
}

@Composable
private fun ColumnScope.DocsList(
    docs: List<Document>,
    onEvent: (DocsScreenUIEvent) -> Unit,
) {
    LazyColumn(modifier = Modifier
        .fillMaxSize()
        .weight(1f)) {
        items(docs) { doc ->
            DocsListItem(
                doc.copy(
                    docText =
                        if (doc.docText.length > 200) {
                            doc.docText.substring(0, 200) + " ..."
                        } else {
                            doc.docText
                        },
                ),
                onRemoveDocClick = { docId -> onEvent(DocsScreenUIEvent.OnRemoveDoc(docId)) },
            )
        }
    }
}

@Composable
private fun DocsListItem(
    document: Document,
    onRemoveDocClick: ((Long) -> Unit),
) {
    val removeDialogTitle = stringResource(R.string.dialog_remove_document_title)
    val removeDialogMessage = stringResource(R.string.dialog_remove_document_message)
    val removeAction = stringResource(R.string.action_remove)
    val cancelAction = stringResource(R.string.action_cancel)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable {
                    dialogDoc.value = document
                    showDocDetailDialog.value = true
                }
                .background(Color.White)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .weight(1f)) {
            Text(
                text = document.docFileName,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.DarkGray,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = document.docText.trim().replace("\n", ""),
                style = MaterialTheme.typography.bodySmall,
                color = Color.DarkGray,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = DateUtils.getRelativeTimeSpanString(document.docAddedTime).toString(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray,
            )
            Text(
                text = stringResource(
                    R.string.document_indexed,
                    stringResource(DocumentSourcePresentation.labelRes(document.sourceType)),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Color.DarkGray,
            )
        }
        Icon(
            modifier =
                Modifier.clickable {
                    createAlertDialog(
                        dialogTitle = removeDialogTitle,
                        dialogText = removeDialogMessage,
                        dialogPositiveButtonText = removeAction,
                        onPositiveButtonClick = { onRemoveDocClick(document.docId) },
                        dialogNegativeButtonText = cancelAction,
                        onNegativeButtonClick = {},
                    )
                },
            imageVector = Icons.Default.Clear,
            tint = Color.DarkGray,
            contentDescription = stringResource(R.string.a11y_remove_document),
        )
        Spacer(modifier = Modifier.width(2.dp))
    }
}

@Composable
private fun ChooseDocTypeDialog(
    onDismiss: () -> Unit,
    onDocTypeSelected: (Readers.DocumentType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_choose_document_type)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.document_type_pdf),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDocTypeSelected(Readers.DocumentType.PDF) }
                        .padding(vertical = 16.dp)
                )
                Text(
                    stringResource(R.string.document_type_word),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDocTypeSelected(Readers.DocumentType.MS_DOCX) }
                        .padding(vertical = 16.dp)
                )
                Text(
                    stringResource(R.string.document_type_plain_text),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDocTypeSelected(Readers.DocumentType.PLAIN_TEXT) }
                        .padding(vertical = 16.dp)
                )
                Text(
                    stringResource(R.string.document_type_markdown),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDocTypeSelected(Readers.DocumentType.MARKDOWN) }
                        .padding(vertical = 16.dp)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun DocOperations(
    uiState: DocsScreenUIState,
    onEvent: (DocsScreenUIEvent) -> Unit,
) {
    val context = LocalContext.current
    var docType by remember { mutableStateOf(Readers.DocumentType.PDF) }
    var pdfUrl by remember { mutableStateOf("") }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showChooseDocTypeDialog by remember { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) {
            it.data?.data?.let { uri ->
                onEvent(DocsScreenUIEvent.OnDocSelected(uri, docType))
            }
        }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
        ) { uri ->
            uri?.let { onEvent(DocsScreenUIEvent.OnImageSelected(it)) }
        }

    val audioPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { onEvent(DocsScreenUIEvent.OnAudioSelected(it)) }
        }

    val audioImportLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            uri?.let { audioUri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        audioUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }.onSuccess {
                    onEvent(DocsScreenUIEvent.OnAudioImportSelected(audioUri))
                }.onFailure {
                    Toast.makeText(context, context.getString(R.string.error_audio_permission), Toast.LENGTH_LONG).show()
                }
            }
        }

    LaunchedEffect(uiState.importMessage) {
        uiState.importMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onEvent(DocsScreenUIEvent.OnImportMessageShown)
        }
    }

    if (showChooseDocTypeDialog) {
        ChooseDocTypeDialog(
            onDismiss = { showChooseDocTypeDialog = false },
            onDocTypeSelected = { selectedDocType ->
                showChooseDocTypeDialog = false
                docType = selectedDocType
                launcher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = docType.getMimeType()
                })
            }
        )
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .fillMaxWidth(),
    ) {
        // Upload from device
        Button(
            modifier = Modifier
                .weight(1f)
                .padding(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6650a4)),
            onClick = { showChooseDocTypeDialog = true },
        ) {
            Text(text = stringResource(R.string.action_add_from_device), color = Color.White)
        }

        Button(
            modifier = Modifier
                .weight(1f)
                .padding(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
            onClick = {
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                    ),
                )
            },
        ) {
            Text(text = stringResource(R.string.action_add_image), color = Color.White)
        }

        // Add from URL
        Button(
            modifier = Modifier
                .weight(1f)
                .padding(2.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF643A71)),
            onClick = { showUrlDialog = true },
        ) {
            Text(text = stringResource(R.string.action_add_from_url), color = Color.White)
        }
    }

    Column(
        modifier = Modifier.padding(horizontal = 10.dp),
    ) {
        Text(
            text = uiState.speechModel.status,
            style = MaterialTheme.typography.labelSmall,
            color = Color.DarkGray,
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Button(
                enabled = !uiState.speechModel.isBusy,
                onClick = { onEvent(DocsScreenUIEvent.OnSpeechModelSetup) },
            ) {
                Text(
                    text =
                        if (uiState.speechModel.isReady) {
                            stringResource(R.string.status_asr_model_ready)
                        } else {
                            stringResource(R.string.action_setup_chinese_asr)
                        },
                )
            }
            if (uiState.speechModel.isBusy) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                    onClick = { onEvent(DocsScreenUIEvent.OnSpeechModelSetupCancelled) },
                ) {
                    Text(text = stringResource(R.string.action_cancel_setup))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = uiState.audioPoc.status,
            style = MaterialTheme.typography.labelSmall,
            color = Color.DarkGray,
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Button(
                enabled =
                    uiState.speechModel.isReady &&
                        !uiState.audioPoc.isBusy &&
                        !uiState.audioImport.isBusy,
                onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
            ) {
                Text(text = stringResource(R.string.action_run_audio_poc))
            }
            if (uiState.audioPoc.isBusy) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                    onClick = { onEvent(DocsScreenUIEvent.OnAudioPocCancelled) },
                ) {
                    Text(text = stringResource(R.string.action_cancel_poc))
                }
            }
        }
        uiState.audioPoc.transcript?.let { transcript ->
            Text(
                text = stringResource(R.string.audio_poc_transcript, transcript),
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.DarkGray,
                maxLines = 4,
            )
            Button(
                modifier = Modifier.padding(top = 4.dp),
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(
                        ClipData.newPlainText(
                            context.getString(R.string.clipboard_label_audio_transcript),
                            transcript,
                        ),
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.status_audio_transcript_copied),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Text(text = stringResource(R.string.action_copy_audio_transcript))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text =
                uiState.audioImport.indexedChunkCount?.let { chunkCount ->
                    stringResource(R.string.status_audio_added_to_knowledge_base, chunkCount)
                } ?: uiState.audioImport.status,
            style = MaterialTheme.typography.labelSmall,
            color = Color.DarkGray,
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Button(
                enabled =
                    uiState.speechModel.isReady &&
                        !uiState.audioPoc.isBusy &&
                        !uiState.audioImport.isBusy,
                onClick = { audioImportLauncher.launch(arrayOf("audio/*")) },
            ) {
                Text(text = stringResource(R.string.action_add_audio))
            }
            if (uiState.audioImport.isBusy) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                    onClick = { onEvent(DocsScreenUIEvent.OnAudioImportCancelled) },
                ) {
                    Text(text = stringResource(R.string.action_cancel_import))
                }
            }
        }
    }

    when (uiState.docDownloadState) {
        DocDownloadState.DOWNLOAD_NONE -> {}
        DocDownloadState.DOWNLOAD_IN_PROGRESS -> {
            showUrlDialog = false
        }

        DocDownloadState.DOWNLOAD_SUCCESS -> {
            Toast.makeText(context, context.getString(R.string.status_document_added_from_url), Toast.LENGTH_SHORT).show()
        }

        DocDownloadState.DOWNLOAD_FAILURE -> {
            Toast.makeText(context, context.getString(R.string.error_document_download), Toast.LENGTH_SHORT).show()
        }
    }

    // URL Dialog
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = {
                showUrlDialog = false
                pdfUrl = ""
            },
            title = {
                Column {
                    Text(stringResource(R.string.dialog_add_document_from_url), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.dialog_add_document_from_url_hint),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            text = {
                Column {
                    TextField(
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        value = pdfUrl,
                        onValueChange = { pdfUrl = it },
                        label = { Text(stringResource(R.string.input_url)) },
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (pdfUrl.isNotBlank()) {
                        onEvent(DocsScreenUIEvent.OnDocURLSubmitted(context, pdfUrl, docType))
                    }
                }) {
                    Text(stringResource(R.string.action_add))
                }
            },
            dismissButton = {
                Button(onClick = { showUrlDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun DocDetailDialog() {
    var isVisible by remember { showDocDetailDialog }
    val context = LocalContext.current
    val doc by remember { dialogDoc }
    if (isVisible && doc != null) {
        Dialog(onDismissRequest = { /* Progress dialogs are non-cancellable */ }) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White, shape = RoundedCornerShape(8.dp))
                        .padding(24.dp),
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = doc?.docFileName ?: "",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = doc?.docText ?: "",
                        modifier = Modifier
                            .height(200.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                            onClick = {
                                val sendIntent: Intent =
                                    Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, doc?.docText)
                                        type = "text/plain"
                                    }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            },
                        ) {
                            Text(text = stringResource(R.string.action_share_text))
                        }
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue),
                            onClick = { isVisible = false },
                        ) {
                            Text(text = stringResource(R.string.action_close))
                        }
                    }
                }
            }
        }
    }
}
