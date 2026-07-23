package com.ml.shubham0204.docqa.ui.screens.chat

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ml.shubham0204.docqa.data.ChunksDB
import com.ml.shubham0204.docqa.data.DocumentsDB
import com.ml.shubham0204.docqa.data.GeminiAPIKey
import com.ml.shubham0204.docqa.data.RetrievedContext
import com.ml.shubham0204.docqa.domain.SentenceEmbeddingProvider
import com.ml.shubham0204.docqa.domain.asr.RealtimeVoiceQueryTranscriber
import com.ml.shubham0204.docqa.domain.llm.GeminiRemoteAPI
import com.ml.shubham0204.docqa.domain.llm.LLMInferenceAPI
import com.ml.shubham0204.docqa.domain.llm.LiteRTAPI
import com.ml.shubham0204.docqa.R
import com.ml.shubham0204.docqa.ui.components.createAlertDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.koin.android.annotation.KoinViewModel

sealed interface ChatScreenUIEvent {
    data object OnEditCredentialsClick : ChatScreenUIEvent

    data object OnOpenDocsClick : ChatScreenUIEvent

    data object OnLocalModelsClick : ChatScreenUIEvent

    data class OnQuestionDraftChanged(
        val draftQuestion: String,
    ) : ChatScreenUIEvent

    data object OnVoiceQueryStart : ChatScreenUIEvent

    data object OnVoiceQueryStop : ChatScreenUIEvent

    data object OnVoiceQueryPermissionDenied : ChatScreenUIEvent

    sealed class ResponseGeneration {
        data class Start(
            val query: String,
            val prompt: String,
        ) : ChatScreenUIEvent

        data class StopWithSuccess(
            val response: String,
            val retrievedContextList: List<RetrievedContext>,
        ) : ChatScreenUIEvent

        data class StopWithError(
            val errorMessage: String,
        ) : ChatScreenUIEvent
    }
}

sealed interface VoiceQueryUIState {
    data object Idle : VoiceQueryUIState

    data class Listening(
        val partialText: String = "",
    ) : VoiceQueryUIState

    data object Stopping : VoiceQueryUIState

    data class Error(
        val message: String,
    ) : VoiceQueryUIState
}

sealed interface ChatNavEvent {
    data object None : ChatNavEvent

    data object ToEditAPIKeyScreen : ChatNavEvent

    data object ToDocsScreen : ChatNavEvent

    data object ToLocalModelsScreen : ChatNavEvent
}

data class ChatScreenUIState(
    val question: String = "",
    val draftQuestion: String = "",
    val response: String = "",
    val isGeneratingResponse: Boolean = false,
    val retrievedContextList: List<RetrievedContext> = emptyList(),
    val voiceQuery: VoiceQueryUIState = VoiceQueryUIState.Idle,
)

@KoinViewModel
class ChatViewModel(
    private val context: Context,
    private val documentsDB: DocumentsDB,
    private val chunksDB: ChunksDB,
    private val geminiAPIKey: GeminiAPIKey,
    private val sentenceEncoder: SentenceEmbeddingProvider,
    private val liteRTAPI: LiteRTAPI,
    private val realtimeVoiceQueryTranscriber: RealtimeVoiceQueryTranscriber,
) : ViewModel() {
    private val _chatScreenUIState = MutableStateFlow(ChatScreenUIState())
    val chatScreenUIState: StateFlow<ChatScreenUIState> = _chatScreenUIState

    private val _navEventChannel = Channel<ChatNavEvent>()
    val navEventChannel = _navEventChannel.receiveAsFlow()
    private var voiceQueryJob: Job? = null

    fun onChatScreenEvent(event: ChatScreenUIEvent) {
        when (event) {
            is ChatScreenUIEvent.OnQuestionDraftChanged -> {
                _chatScreenUIState.value =
                    _chatScreenUIState.value.copy(draftQuestion = event.draftQuestion)
            }

            ChatScreenUIEvent.OnVoiceQueryStart -> startVoiceQuery()

            ChatScreenUIEvent.OnVoiceQueryStop -> stopVoiceQuery()

            ChatScreenUIEvent.OnVoiceQueryPermissionDenied -> {
                updateVoiceQueryState(
                    VoiceQueryUIState.Error(context.getString(R.string.error_voice_query_permission_denied)),
                )
            }

            is ChatScreenUIEvent.ResponseGeneration.Start -> {
                if (!checkNumDocuments()) {
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.screen_chat_add_documents),
                            Toast.LENGTH_LONG,
                        ).show()
                    return
                }
                if (event.query.trim().isEmpty()) {
                    Toast
                        .makeText(context, context.getString(R.string.screen_chat_enter_query), Toast.LENGTH_LONG)
                        .show()
                    return
                }

                // If a local model is loaded, skip the Gemini API key check
                if (liteRTAPI.isLoaded) {
                    _chatScreenUIState.value =
                        _chatScreenUIState.value.copy(isGeneratingResponse = true)
                    _chatScreenUIState.value =
                        _chatScreenUIState.value.copy(question = event.query)
                    val llm = liteRTAPI
                    Toast.makeText(context, context.getString(R.string.screen_chat_using_local_model), Toast.LENGTH_LONG).show()
                    getAnswer(llm, event.query, event.prompt)
                    return
                }

                if (!checkValidAPIKey()) {
                    createAlertDialog(
                        dialogTitle = context.getString(R.string.dialog_invalid_api_key_title),
                        dialogText = context.getString(R.string.dialog_invalid_api_key_message),
                        dialogPositiveButtonText = context.getString(R.string.action_add_api_key),
                        onPositiveButtonClick = {
                            onChatScreenEvent(ChatScreenUIEvent.OnEditCredentialsClick)
                        },
                        dialogNegativeButtonText = context.getString(R.string.action_open_gemini_console),
                        onNegativeButtonClick = {
                            Intent(Intent.ACTION_VIEW).apply {
                                data = "https://aistudio.google.com/apikey".toUri()
                                context.startActivity(this)
                            }
                        },
                    )
                    return
                }
                _chatScreenUIState.value =
                    _chatScreenUIState.value.copy(isGeneratingResponse = true)
                _chatScreenUIState.value =
                    _chatScreenUIState.value.copy(question = event.query)

                val apiKey = geminiAPIKey.getAPIKey()
                    ?: throw Exception("Gemini API key is null")
                val llm = GeminiRemoteAPI(apiKey)
                Toast.makeText(context, context.getString(R.string.screen_chat_using_gemini), Toast.LENGTH_LONG).show()
                getAnswer(llm, event.query, event.prompt)
            }

            is ChatScreenUIEvent.ResponseGeneration.StopWithSuccess -> {
                _chatScreenUIState.value =
                    _chatScreenUIState.value.copy(isGeneratingResponse = false)
                _chatScreenUIState.value = _chatScreenUIState.value.copy(response = event.response)
                _chatScreenUIState.value =
                    _chatScreenUIState.value.copy(retrievedContextList = event.retrievedContextList)
            }

            is ChatScreenUIEvent.ResponseGeneration.StopWithError -> {
                _chatScreenUIState.value =
                    _chatScreenUIState.value.copy(isGeneratingResponse = false)
                _chatScreenUIState.value = _chatScreenUIState.value.copy(question = "")
            }

            is ChatScreenUIEvent.OnOpenDocsClick -> {
                viewModelScope.launch {
                    _navEventChannel.send(ChatNavEvent.ToDocsScreen)
                }
            }

            is ChatScreenUIEvent.OnEditCredentialsClick -> {
                viewModelScope.launch {
                    _navEventChannel.send(ChatNavEvent.ToEditAPIKeyScreen)
                }
            }

            is ChatScreenUIEvent.OnLocalModelsClick -> {
                viewModelScope.launch {
                    _navEventChannel.send(ChatNavEvent.ToLocalModelsScreen)
                }
            }
        }
    }

    private fun getAnswer(
        llm: LLMInferenceAPI,
        query: String,
        prompt: String,
    ) {
        try {
            var jointContext = ""
            val retrievedContextList = ArrayList<RetrievedContext>()
            val queryEmbedding = sentenceEncoder.encodeText(query)
            chunksDB.getSimilarChunks(queryEmbedding, n = 5).forEach {
                jointContext += " " + it.second.chunkData
                retrievedContextList.add(
                    RetrievedContext(
                        it.second.docFileName,
                        it.second.chunkData,
                    ),
                )
            }
            val inputPrompt = prompt.replace("\$CONTEXT", jointContext).replace("\$QUERY", query)
            CoroutineScope(Dispatchers.IO).launch {
                llm.getResponse(inputPrompt)?.let { llmResponse ->
                    onChatScreenEvent(
                        ChatScreenUIEvent.ResponseGeneration.StopWithSuccess(
                            llmResponse,
                            retrievedContextList,
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            onChatScreenEvent(ChatScreenUIEvent.ResponseGeneration.StopWithError(e.message ?: ""))
            throw e
        }
    }

    fun checkNumDocuments(): Boolean = documentsDB.getDocsCount() > 0

    fun checkValidAPIKey(): Boolean = geminiAPIKey.getAPIKey() != null

    override fun onCleared() {
        realtimeVoiceQueryTranscriber.stop()
        voiceQueryJob?.cancel()
        super.onCleared()
    }

    private fun startVoiceQuery() {
        if (voiceQueryJob?.isActive == true || _chatScreenUIState.value.isGeneratingResponse) return

        voiceQueryJob =
            viewModelScope.launch {
                try {
                    if (!realtimeVoiceQueryTranscriber.isModelInstalled()) {
                        updateVoiceQueryState(
                            VoiceQueryUIState.Error(context.getString(R.string.error_voice_query_model_not_ready)),
                        )
                        return@launch
                    }
                    realtimeVoiceQueryTranscriber.prepareForRecording()
                    updateVoiceQueryState(VoiceQueryUIState.Listening())
                    val finalText =
                        realtimeVoiceQueryTranscriber.transcribe { partialText ->
                            if (_chatScreenUIState.value.voiceQuery is VoiceQueryUIState.Listening) {
                                updateVoiceQueryState(VoiceQueryUIState.Listening(partialText))
                            }
                        }
                    if (finalText.isBlank()) {
                        updateVoiceQueryState(
                            VoiceQueryUIState.Error(context.getString(R.string.error_voice_query_empty)),
                        )
                    } else {
                        _chatScreenUIState.value =
                            _chatScreenUIState.value.copy(
                                draftQuestion = mergeDraftQuestion(finalText),
                                voiceQuery = VoiceQueryUIState.Idle,
                            )
                    }
                } catch (_: CancellationException) {
                    updateVoiceQueryState(VoiceQueryUIState.Idle)
                } catch (_: Exception) {
                    updateVoiceQueryState(
                        VoiceQueryUIState.Error(context.getString(R.string.error_voice_query_failed)),
                    )
                }
            }
    }

    private fun stopVoiceQuery() {
        if (voiceQueryJob?.isActive != true) return
        updateVoiceQueryState(VoiceQueryUIState.Stopping)
        realtimeVoiceQueryTranscriber.stop()
    }

    private fun updateVoiceQueryState(state: VoiceQueryUIState) {
        _chatScreenUIState.value = _chatScreenUIState.value.copy(voiceQuery = state)
    }

    private fun mergeDraftQuestion(finalText: String): String =
        listOf(_chatScreenUIState.value.draftQuestion.trim(), finalText.trim())
            .filter(String::isNotEmpty)
            .joinToString(separator = " ")
}
