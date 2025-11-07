package com.heygude.aichallenge

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.app.Application
import com.heygude.aichallenge.presentation.SystemPrompt
import com.heygude.aichallenge.presentation.SystemPromptManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(
    application: Application,
    private val systemPromptManager: SystemPromptManager = SystemPromptManager(application)
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

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun addPrompt(name: String, content: String) {
        if (name.isBlank() || content.isBlank()) {
            _uiState.value = UiState.Error("Name and content cannot be empty")
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
                _uiState.value = UiState.Success("Prompt added successfully")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to add prompt: ${e.message}")
            }
        }
    }

    fun updatePrompt(prompt: SystemPrompt) {
        viewModelScope.launch {
            try {
                systemPromptManager.updatePrompt(prompt)
                _uiState.value = UiState.Success("Prompt updated successfully")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to update prompt: ${e.message}")
            }
        }
    }

    fun deletePrompt(promptId: String) {
        viewModelScope.launch {
            try {
                systemPromptManager.deletePrompt(promptId)
                _uiState.value = UiState.Success("Prompt deleted successfully")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to delete prompt: ${e.message}")
            }
        }
    }

    fun setCurrentPrompt(promptId: String) {
        viewModelScope.launch {
            try {
                systemPromptManager.setCurrentPrompt(promptId)
                _uiState.value = UiState.Success("Current prompt changed successfully")
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to change prompt: ${e.message}")
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

