package com.ml.shubham0204.docqa.ui.screens.edit_credentials

import android.widget.Toast
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ml.shubham0204.docqa.R
import com.ml.shubham0204.docqa.ui.theme.DocQATheme
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCredentialsScreen(onBackClick: () -> Unit) {
    DocQATheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.screen_credentials_title),
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
            val viewModel: EditCredentialsViewModel = koinViewModel()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .scrollable(rememberScrollState(), orientation = Orientation.Vertical)
                        .fillMaxWidth(),
            ) {
                GeminiAPIKey(viewModel)
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                HFAccessToken(viewModel)
            }
        }
    }
}

@Composable
private fun GeminiAPIKey(viewModel: EditCredentialsViewModel) {
    val context = LocalContext.current
    var geminiApiKey by remember { mutableStateOf(viewModel.getGeminiAPIKey() ?: "") }
    TextField(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        value = geminiApiKey,
        onValueChange = { geminiApiKey = it },
        shape = RoundedCornerShape(16.dp),
        colors =
            TextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                disabledTextColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        placeholder = { Text(text = stringResource(R.string.input_gemini_api_key)) },
    )
    Button(
        enabled = geminiApiKey.isNotBlank(),
        onClick = {
            viewModel.saveGeminiAPIKey(geminiApiKey)
            Toast.makeText(context, context.getString(R.string.status_api_key_saved), Toast.LENGTH_LONG).show()
        },
    ) {
        Icon(imageVector = Icons.Default.Save, contentDescription = stringResource(R.string.action_save_api_key))
        Text(text = stringResource(R.string.action_save_api_key))
    }
}

@Composable
private fun HFAccessToken(viewModel: EditCredentialsViewModel) {
    val context = LocalContext.current
    var hfAccessToken by remember { mutableStateOf(viewModel.getHFAccessToken() ?: "") }
    TextField(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        value = hfAccessToken,
        onValueChange = { hfAccessToken = it },
        shape = RoundedCornerShape(16.dp),
        colors =
            TextFieldDefaults.colors(
                focusedTextColor = Color.Black,
                disabledTextColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        placeholder = { Text(text = stringResource(R.string.input_hf_access_token)) },
    )
    Button(
        enabled = hfAccessToken.isNotBlank(),
        onClick = {
            viewModel.saveHFAccessToken(hfAccessToken)
            Toast.makeText(context, context.getString(R.string.status_hf_token_saved), Toast.LENGTH_LONG).show()
        },
    ) {
        Icon(imageVector = Icons.Default.Save, contentDescription = stringResource(R.string.action_save_hf_token))
        Text(text = stringResource(R.string.action_save_hf_token))
    }
}

@Preview
@Composable
private fun EditAPIKeyScreenPreview() {
    EditCredentialsScreen(onBackClick = {})
}
