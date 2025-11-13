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
import com.heygude.aichallenge.presentation.ChatHistoryManager
import com.heygude.aichallenge.presentation.ChatMessageData
import com.heygude.aichallenge.R
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

class AIAgentViewModel(
    application: Application,
    private val repository: AIAgentRepository = DefaultAIAgentRepository(
        DefaultYandexGptDataSource(),
        DefaultDeepSeekGptDataSource()
    ),
    private val systemPromptManager: SystemPromptManager = SystemPromptManager(application),
    private val settingsManager: SettingsManager = SettingsManager(application),
    private val chatHistoryManager: ChatHistoryManager = ChatHistoryManager(application)
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
        val costUsd: Double? = null, // Request cost in USD (only for AI responses)
        val isSummary: Boolean = false // True if this message is a summary of previous messages
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
            // Load chat history
            chatHistoryManager.chatHistory.firstOrNull()?.let { history ->
                _messages.value = history.map { data ->
                    ChatMessage(
                        id = data.id,
                        text = data.text,
                        isUser = data.isUser,
                        timestampMs = data.timestampMs,
                        model = data.model?.let { modelName ->
                            try {
                                GptModel.valueOf(modelName)
                            } catch (e: Exception) {
                                null
                            }
                        },
                        requestTokens = data.requestTokens,
                        responseTokens = data.responseTokens,
                        responseTimeMs = data.responseTimeMs,
                        costUsd = data.costUsd,
                        isSummary = data.isSummary
                    )
                }
            }
        }
    }
    
    private suspend fun saveHistory() {
        val historyData = _messages.value.map { msg ->
            ChatMessageData(
                id = msg.id,
                text = msg.text,
                isUser = msg.isUser,
                timestampMs = msg.timestampMs,
                model = msg.model?.name,
                requestTokens = msg.requestTokens,
                responseTokens = msg.responseTokens,
                responseTimeMs = msg.responseTimeMs,
                costUsd = msg.costUsd,
                isSummary = msg.isSummary
            )
        }
        chatHistoryManager.saveChatHistory(historyData)
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
        viewModelScope.launch {
            saveHistory()
        }
        
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            val currentPrompt = systemPromptManager.currentPrompt.firstOrNull()
            val systemPrompt = currentPrompt?.content ?: ""
            val temperature = settingsManager.temperature.firstOrNull() ?: 0.6
            val maxTokens = settingsManager.maxTokens.firstOrNull() ?: 2000
            
            // Get all previous messages for conversation history (exclude current message to avoid duplication,
            // as it will be added separately in the data source)
            val conversationHistory = _messages.value.filter { it.id != now }
            Timber.d("AIAgentViewModel: Sending conversation history with ${conversationHistory.size} previous messages")
            
            // For Yandex, use the method with token tracking
            val result = if (currentModel.provider == com.heygude.aichallenge.data.yandex.ModelProvider.YANDEX) {
                repository.generateResponseWithTokens(prompt, systemPrompt, currentModel, temperature, maxTokens, conversationHistory)
            } else {
                // For other providers, use regular method and wrap result
                repository.generateResponse(prompt, systemPrompt, currentModel, temperature, maxTokens, conversationHistory).map { text ->
                    YandexResponse(text, null)
                }
            }
            
            _uiState.value = result.fold(
                onSuccess = { yandexResponse ->
                    val response = yandexResponse ?: YandexResponse("", null)
                    val tokenInfo = response.tokenInfo
                    
                    // Update user message with request tokens and add reply in one operation
                    val userRequestTokens = tokenInfo?.requestTokens
                    val updatedMessages = if (userRequestTokens != null) {
                        _messages.value.map { msg ->
                            if (msg.id == now && msg.isUser) {
                                msg.copy(requestTokens = userRequestTokens)
                            } else {
                                msg
                            }
                        }
                    } else {
                        _messages.value
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
                    _messages.value = updatedMessages + reply
                    saveHistory()
                    
                    // Check if we need to compress history (every 10 non-summary messages)
                    compressHistoryIfNeeded()
                    
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
                    saveHistory()
                    UiState.Error(errorMessage)
                }
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            _messages.value = emptyList()
            chatHistoryManager.clearChatHistory()
        }
    }
    
    private suspend fun compressHistoryIfNeeded() {
        val messages = _messages.value
        // Count only non-summary messages
        val nonSummaryMessages = messages.filter { !it.isSummary }
        
        // If we have 10 or more non-summary messages, compress the oldest 10
        if (nonSummaryMessages.size >= 10) {
            Timber.d("AIAgentViewModel: Compressing history - ${nonSummaryMessages.size} non-summary messages found")
            
            // Get the oldest 10 non-summary messages (sorted by timestamp)
            val sortedNonSummary = nonSummaryMessages.sortedBy { it.timestampMs }
            val messagesToCompress = sortedNonSummary.take(10)
            
            // Generate summary
            val summary = generateSummary(messagesToCompress)
            
            if (summary != null) {
                // Create summary message
                val summaryMessage = ChatMessage(
                    id = System.currentTimeMillis(),
                    text = summary,
                    isUser = false,
                    timestampMs = messagesToCompress.first().timestampMs, // Use timestamp of first compressed message
                    model = selectedModel.value,
                    isSummary = true
                )
                
                // Replace compressed messages with summary
                val messagesToKeep = messages.filter { msg -> 
                    !messagesToCompress.any { it.id == msg.id }
                }
                
                // Insert summary at the position of the first compressed message
                val sortedMessages = (messagesToKeep + summaryMessage).sortedBy { it.timestampMs }
                _messages.value = sortedMessages
                
                saveHistory()
                Timber.d("AIAgentViewModel: History compressed - replaced ${messagesToCompress.size} messages with 1 summary")
            }
        }
    }
    
    private suspend fun generateSummary(messages: List<ChatMessage>): String? {
        if (messages.isEmpty()) return null
        
        val currentModel = selectedModel.value
        val temperature = settingsManager.temperature.firstOrNull() ?: 0.6
        val maxTokens = settingsManager.maxTokens.firstOrNull() ?: 2000
        
        // Create summary prompt
        val conversationText = messages.joinToString("\n") { msg ->
            val role = if (msg.isUser) "Пользователь" else "Ассистент"
            "$role: ${msg.text}"
        }
        
        val summaryPrompt = """Создай краткое резюме следующего диалога, сохраняя ключевые моменты и контекст для продолжения беседы. 
Резюме должно быть на том же языке, что и диалог. 
Резюме должно быть понятным и достаточным для продолжения разговора.

Диалог:
$conversationText

Резюме:"""
        
        val summarySystemPrompt = "Ты помощник, который создает краткие и информативные резюме диалогов, сохраняя важный контекст для продолжения беседы."
        
        return try {
            val result = if (currentModel.provider == com.heygude.aichallenge.data.yandex.ModelProvider.YANDEX) {
                repository.generateResponse(summaryPrompt, summarySystemPrompt, currentModel, temperature, maxTokens, emptyList())
            } else {
                repository.generateResponse(summaryPrompt, summarySystemPrompt, currentModel, temperature, maxTokens, emptyList())
            }
            
            result.fold(
                onSuccess = { summary -> 
                    Timber.d("AIAgentViewModel: Summary generated successfully, length: ${summary.length}")
                    summary.trim()
                },
                onFailure = { error ->
                    Timber.e(error, "AIAgentViewModel: Failed to generate summary")
                    null
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "AIAgentViewModel: Exception while generating summary")
            null
        }
    }

    sealed interface UiState {
        object Idle : UiState
        object Loading : UiState
        data class Success(val text: String) : UiState
        data class Error(val message: String) : UiState
    }
}


