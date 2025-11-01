package com.heygude.aichallenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.heygude.aichallenge.data.AIAgentRepository
import com.heygude.aichallenge.data.DefaultAIAgentRepository
import com.heygude.aichallenge.data.yandex.DefaultYandexGptDataSource
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AIAgentViewModel(
    private val repository: AIAgentRepository = DefaultAIAgentRepository(DefaultYandexGptDataSource())
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    data class ChatMessage(
        val id: Long,
        val text: String,
        val isUser: Boolean,
        val timestampMs: Long
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

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
            val result = repository.generateResponse(prompt)
            _uiState.value = result.fold(
                onSuccess = { text ->
                    val reply = ChatMessage(
                        id = System.currentTimeMillis(),
                        text = text,
                        isUser = false,
                        timestampMs = System.currentTimeMillis()
                    )
                    _messages.value = _messages.value + reply
                    UiState.Success(text)
                },
                onFailure = { error ->
                    val reply = ChatMessage(
                        id = System.currentTimeMillis(),
                        text = "Error: ${error.message ?: "Unknown error"}",
                        isUser = false,
                        timestampMs = System.currentTimeMillis()
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


