package com.ml.shubham0204.docqa.ui.screens.local_models

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ml.shubham0204.docqa.R

@Composable
fun DownloadDialogModel(
    dialogState: DownloadModelDialogUIState,
    onDismiss: () -> Unit,
) {
    if (dialogState.isDialogVisible) {
        Dialog(
            onDismissRequest = onDismiss,
        ) {
            Surface(modifier = Modifier.heightIn(max = 300.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.dialog_download_models_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.dialog_download_models_message),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (dialogState.showProgress) {
                        Text(stringResource(R.string.status_model_downloading))
                        Text(stringResource(R.string.status_model_download_progress, dialogState.progress))
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { dialogState.progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                }
            }
        }
    }
}

@Composable
@Preview
private fun DownloadDialogModelPreview() {
    DownloadDialogModel(
        dialogState =
            DownloadModelDialogUIState(
                isDialogVisible = true,
                showProgress = false,
                progress = 80,
            ),
        onDismiss = {},
    )
}
