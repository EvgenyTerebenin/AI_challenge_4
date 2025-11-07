package com.heygude.aichallenge

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.heygude.aichallenge.data.AIAgentRepository
import com.heygude.aichallenge.data.DefaultAIAgentRepository
import com.heygude.aichallenge.data.yandex.DefaultYandexGptDataSource
import com.heygude.aichallenge.data.yandex.GptModel
import com.heygude.aichallenge.data.deepseek.DefaultDeepSeekGptDataSource
import com.heygude.aichallenge.presentation.SystemPromptManager
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AIAgentViewModel(
    application: Application,
    private val repository: AIAgentRepository = DefaultAIAgentRepository(
        DefaultYandexGptDataSource(),
        DefaultDeepSeekGptDataSource()
    ),
    private val systemPromptManager: SystemPromptManager = SystemPromptManager(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    data class ChatMessage(
        val id: Long,
        val text: String,
        val isUser: Boolean,
        val timestampMs: Long,
        val model: GptModel? = null // Model used for AI responses, null for user messages
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _selectedModel = MutableStateFlow<GptModel>(GptModel.YANDEX_LATEST)
    val selectedModel: StateFlow<GptModel> = _selectedModel.asStateFlow()

    fun setSelectedModel(model: GptModel) {
        _selectedModel.value = model
    }

    init {
        viewModelScope.launch {
            systemPromptManager.initializeDefaultPrompt()
        }
    }

    fun sendPrompt(prompt: String) {
        if (prompt.isBlank()) return
        val now = System.currentTimeMillis()
        val userMessage = ChatMessage(
            id = now,
            text = prompt,
            isUser = true,
            timestampMs = now
        )
        _messages.value = _messages.value + userMessage
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val currentPrompt = systemPromptManager.currentPrompt.firstOrNull()
            val systemPrompt = currentPrompt?.content ?: ""
            val selectedModel = _selectedModel.value
            val result = repository.generateResponse(prompt, systemPrompt, selectedModel)
            _uiState.value = result.fold(
                onSuccess = { text ->
                    val reply = ChatMessage(
                        id = System.currentTimeMillis(),
                        text = text,
                        isUser = false,
                        timestampMs = System.currentTimeMillis(),
                        model = selectedModel
                    )
                    _messages.value = _messages.value + reply
                    UiState.Success(text)
                },
                onFailure = { error ->
                    val reply = ChatMessage(
                        id = System.currentTimeMillis(),
                        text = "Error: ${error.message ?: "Unknown error"}",
                        isUser = false,
                        timestampMs = System.currentTimeMillis(),
                        model = selectedModel
                    )
                    _messages.value = _messages.value + reply
                    UiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }

    sealed interface UiState {
        object Idle : UiState
        object Loading : UiState
        data class Success(val text: String) : UiState
        data class Error(val message: String) : UiState
    }
}


