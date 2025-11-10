package com.heygude.aichallenge

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.heygude.aichallenge.presentation.SystemPrompt
import com.heygude.aichallenge.presentation.SystemPromptManager
import com.heygude.aichallenge.presentation.SettingsManager
import com.heygude.aichallenge.data.yandex.GptModel
import com.heygude.aichallenge.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(
    application: Application,
    private val systemPromptManager: SystemPromptManager = SystemPromptManager(application),
    private val settingsManager: SettingsManager = SettingsManager(application)
) : AndroidViewModel(application) {

    val allPrompts: StateFlow<List<SystemPrompt>> = systemPromptManager.allPrompts
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    val currentPromptId: StateFlow<String?> = systemPromptManager.currentPromptId
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    val temperature: StateFlow<Double> = settingsManager.temperature
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = 0.6
        )
    
    val selectedModel: StateFlow<GptModel> = settingsManager.selectedModel
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = GptModel.YANDEX_LATEST
        )

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun addPrompt(name: String, content: String) {
        if (name.isBlank() || content.isBlank()) {
            _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.name_and_content_cannot_be_empty))
            return
        }
        viewModelScope.launch {
            try {
                val prompt = SystemPrompt(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    content = content,
                    isDefault = false
                )
                systemPromptManager.addPrompt(prompt)
                _uiState.value = UiState.Success(getApplication<Application>().getString(R.string.prompt_added_successfully))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.failed_to_add_prompt, e.message ?: ""))
            }
        }
    }

    fun updatePrompt(prompt: SystemPrompt) {
        viewModelScope.launch {
            try {
                systemPromptManager.updatePrompt(prompt)
                _uiState.value = UiState.Success(getApplication<Application>().getString(R.string.prompt_updated_successfully))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.failed_to_update_prompt, e.message ?: ""))
            }
        }
    }

    fun deletePrompt(promptId: String) {
        viewModelScope.launch {
            try {
                systemPromptManager.deletePrompt(promptId)
                _uiState.value = UiState.Success(getApplication<Application>().getString(R.string.prompt_deleted_successfully))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.failed_to_delete_prompt, e.message ?: ""))
            }
        }
    }

    fun setCurrentPrompt(promptId: String) {
        viewModelScope.launch {
            try {
                systemPromptManager.setCurrentPrompt(promptId)
                _uiState.value = UiState.Success(getApplication<Application>().getString(R.string.current_prompt_changed_successfully))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.failed_to_change_prompt, e.message ?: ""))
            }
        }
    }

    fun setTemperature(temperature: Double) {
        viewModelScope.launch {
            try {
                settingsManager.setTemperature(temperature)
                _uiState.value = UiState.Success(getApplication<Application>().getString(R.string.temperature_updated_successfully))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.failed_to_update_temperature, e.message ?: ""))
            }
        }
    }
    
    fun setSelectedModel(model: GptModel) {
        viewModelScope.launch {
            try {
                settingsManager.setSelectedModel(model)
                _uiState.value = UiState.Success(getApplication<Application>().getString(R.string.model_updated_successfully))
            } catch (e: Exception) {
                _uiState.value = UiState.Error(getApplication<Application>().getString(R.string.failed_to_update_model, e.message ?: ""))
            }
        }
    }

    fun clearError() {
        _uiState.value = UiState.Idle
    }

    sealed interface UiState {
        object Idle : UiState
        data class Success(val message: String) : UiState
        data class Error(val message: String) : UiState
    }
}

