package com.heygude.aichallenge

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.heygude.aichallenge.data.AIAgentRepository
import com.heygude.aichallenge.data.DefaultAIAgentRepository
import com.heygude.aichallenge.data.yandex.DefaultYandexGptDataSource
import com.heygude.aichallenge.data.yandex.GptModel
import com.heygude.aichallenge.data.yandex.YandexResponse
import com.heygude.aichallenge.data.deepseek.DefaultDeepSeekGptDataSource
import com.heygude.aichallenge.presentation.SystemPromptManager
import com.heygude.aichallenge.presentation.SettingsManager
import com.heygude.aichallenge.R
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AIAgentViewModel(
    application: Application,
    private val repository: AIAgentRepository = DefaultAIAgentRepository(
        DefaultYandexGptDataSource(),
        DefaultDeepSeekGptDataSource()
    ),
    private val systemPromptManager: SystemPromptManager = SystemPromptManager(application),
    private val settingsManager: SettingsManager = SettingsManager(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState

    data class ChatMessage(
        val id: Long,
        val text: String,
        val isUser: Boolean,
        val timestampMs: Long,
        val model: GptModel? = null, // Model used for AI responses, null for user messages
        val requestTokens: Int? = null, // Request tokens (for user messages and AI responses)
        val responseTokens: Int? = null, // Response tokens (only for AI responses)
        val responseTimeMs: Long? = null, // Response execution time in milliseconds (only for AI responses)
        val costUsd: Double? = null // Request cost in USD (only for AI responses)
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Read selected model from SettingsManager
    val selectedModel: StateFlow<GptModel> = settingsManager.selectedModel
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = GptModel.YANDEX_LATEST
        )

    fun setSelectedModel(model: GptModel) {
        viewModelScope.launch {
            settingsManager.setSelectedModel(model)
        }
    }

    init {
        viewModelScope.launch {
            systemPromptManager.initializeDefaultPrompt()
        }
    }

    fun sendPrompt(prompt: String) {
        if (prompt.isBlank()) return
        val now = System.currentTimeMillis()
        val currentModel = selectedModel.value
        
        // Add user message immediately
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
            val temperature = settingsManager.temperature.firstOrNull() ?: 0.6
            val maxTokens = settingsManager.maxTokens.firstOrNull() ?: 2000
            
            // For Yandex, use the method with token tracking
            val result = if (currentModel.provider == com.heygude.aichallenge.data.yandex.ModelProvider.YANDEX) {
                repository.generateResponseWithTokens(prompt, systemPrompt, currentModel, temperature, maxTokens)
            } else {
                // For other providers, use regular method and wrap result
                repository.generateResponse(prompt, systemPrompt, currentModel, temperature, maxTokens).map { text ->
                    YandexResponse(text, null)
                }
            }
            
            _uiState.value = result.fold(
                onSuccess = { yandexResponse ->
                    val response = yandexResponse ?: YandexResponse("", null)
                    val tokenInfo = response.tokenInfo
                    
                    // Update user message with request tokens if available
                    val userRequestTokens = tokenInfo?.requestTokens
                    if (userRequestTokens != null) {
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == now && msg.isUser) {
                                msg.copy(requestTokens = userRequestTokens)
                            } else {
                                msg
                            }
                        }
                    }
                    
                    val reply = ChatMessage(
                        id = System.currentTimeMillis(),
                        text = response.text,
                        isUser = false,
                        timestampMs = System.currentTimeMillis(),
                        model = currentModel,
                        requestTokens = tokenInfo?.requestTokens,
                        responseTokens = tokenInfo?.responseTokens,
                        responseTimeMs = tokenInfo?.responseTimeMs,
                        costUsd = tokenInfo?.costUsd
                    )
                    
                    // Add reply message
                    _messages.value = _messages.value + reply
                    UiState.Success(response.text)
                },
                onFailure = { error ->
                    val context = getApplication<Application>()
                    val errorMessage = error.message ?: context.getString(R.string.unknown_error)
                    val errorText = context.getString(R.string.error_format, errorMessage)
                    
                    val reply = ChatMessage(
                        id = System.currentTimeMillis(),
                        text = errorText,
                        isUser = false,
                        timestampMs = System.currentTimeMillis(),
                        model = currentModel
                    )
                    _messages.value = _messages.value + reply
                    UiState.Error(errorMessage)
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


