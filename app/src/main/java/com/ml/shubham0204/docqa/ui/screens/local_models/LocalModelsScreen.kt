package com.ml.shubham0204.docqa.ui.screens.local_models

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.docqa.R
import com.ml.shubham0204.docqa.data.LocalModel
import com.ml.shubham0204.docqa.ui.components.AppAlertDialog
import com.ml.shubham0204.docqa.ui.theme.DocQATheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalModelsScreen(
    uiState: LocalModelsUIState,
    onEvent: (LocalModelsUIEvent) -> Unit,
    onBackClick: () -> Unit,
) {
    DocQATheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.screen_models_title),
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxWidth(),
            ) {
                LaunchedEffect(0) {
                    onEvent(LocalModelsUIEvent.RefreshModelsList)
                }
                LocalModelsList(
                    uiState.models,
                    onDownloadModelClick = { localModel ->
                        onEvent(LocalModelsUIEvent.OnModelDownloadClick(localModel))
                    },
                    onLoadModelClick = { localModel ->
                        onEvent(LocalModelsUIEvent.OnUseModelClick(localModel))
                    },
                )
                DownloadDialogModel(uiState.downloadModelDialogState, onDismiss = {})
                AppAlertDialog()
            }
        }
    }
}

@Composable
private fun LocalModelsList(
    modelsList: List<LocalModel>,
    onDownloadModelClick: (LocalModel) -> Unit,
    onLoadModelClick: (LocalModel) -> Unit,
) {
    val context = LocalContext.current
    LazyColumn {
        items(modelsList) { localModel ->
            LocalModelListItem(
                modelName = localModel.name,
                modelDescription = localizedModelDescription(localModel),
                isDownloaded =
                    if (context.filesDir != null) {
                        // `context.filesDir` can be null when rendering
                        // the Compose preview
                        localModel.isDownloaded(context.filesDir.absolutePath)
                    } else {
                        true
                    },
                isLoaded = localModel.isLoaded,
                onDownloadClick = { onDownloadModelClick(localModel) },
                onLoadModelClick = { onLoadModelClick(localModel) },
            )
        }
    }
}

@Composable
private fun LocalModelListItem(
    modelName: String,
    modelDescription: String,
    isDownloaded: Boolean,
    isLoaded: Boolean,
    onDownloadClick: () -> Unit,
    onLoadModelClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = modelDescription,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isDownloaded) {
            if (isLoaded) {
                Box(
                    modifier =
                        Modifier
                            .background(Color.White)
                            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                            .padding(4.dp),
                ) {
                    Text(stringResource(R.string.status_model_loaded), style = MaterialTheme.typography.labelSmall)
                }
            } else {
                IconButton(onClick = onLoadModelClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.action_load_model),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            IconButton(onClick = onDownloadClick) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = stringResource(R.string.action_download_model),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    )
}

@Composable
private fun localizedModelDescription(model: LocalModel): String =
    when {
        model.name.startsWith("Qwen") -> stringResource(R.string.model_description_qwen)
        model.name.startsWith("Phi") -> stringResource(R.string.model_description_phi)
        model.name.startsWith("DeepSeek") -> stringResource(R.string.model_description_deepseek)
        model.name.startsWith("Gemma") -> stringResource(R.string.model_description_gemma)
        model.name.startsWith("Llama") -> stringResource(R.string.model_description_llama)
        else -> stringResource(R.string.model_description_default)
    }

@Composable
@Preview
private fun LocalModelsScreenPreview() {
    LocalModelsScreen(
        uiState =
            LocalModelsUIState(
                models =
                    listOf<LocalModel>(
                        LocalModel(
                            name = "Qwen3 8B",
                            description = "A Qwen family model series",
                            isLoaded = false,
                            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_seq128_q8_ekv4096.task",
                        ),
                        LocalModel(
                            name = "Qwen2.5 1.5B",
                            description = "A Qwen family model series",
                            isLoaded = true,
                            downloadUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_seq128_q8_ekv4096.task",
                        ),
                    ),
            ),
        onEvent = { },
        onBackClick = {},
    )
}
