package com.ml.shubham0204.docqa.ui.screens.chat

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ml.shubham0204.docqa.R

@Composable
fun ChatScreenMoreOptionsPopup(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onItemClick: (ChatScreenUIEvent) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        OptionsPopupItem(
            title = stringResource(R.string.screen_credentials_title),
            icon = Icons.Default.Key,
            onItemClick = {
                onItemClick(ChatScreenUIEvent.OnEditCredentialsClick)
                onDismissRequest()
            },
        )
        OptionsPopupItem(
            title = stringResource(R.string.screen_models_title),
            icon = Icons.Default.Download,
            onItemClick = {
                onItemClick(ChatScreenUIEvent.OnLocalModelsClick)
                onDismissRequest()
            },
        )
        OptionsPopupItem(
            title = stringResource(R.string.screen_docs_title),
            icon = Icons.Default.FolderOpen,
            onItemClick = {
                onItemClick(ChatScreenUIEvent.OnOpenDocsClick)
                onDismissRequest()
            },
        )
    }
}

@Composable
private fun OptionsPopupItem(
    title: String,
    icon: ImageVector,
    onItemClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(title, style = MaterialTheme.typography.labelMedium)
        },
        leadingIcon = {
            Icon(icon, contentDescription = title)
        },
        onClick = onItemClick,
    )
}
