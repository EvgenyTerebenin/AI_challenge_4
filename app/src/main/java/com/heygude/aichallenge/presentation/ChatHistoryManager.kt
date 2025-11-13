package com.heygude.aichallenge.presentation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.chatHistoryDataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_history")

@Serializable
data class ChatMessageData(
    val id: Long,
    val text: String,
    val isUser: Boolean,
    val timestampMs: Long,
    val model: String? = null,
    val requestTokens: Int? = null,
    val responseTokens: Int? = null,
    val responseTimeMs: Long? = null,
    val costUsd: Double? = null,
    val isSummary: Boolean = false
)

class ChatHistoryManager(private val context: Context) {
    private val dataStore = context.chatHistoryDataStore
    private val chatHistoryKey = stringPreferencesKey("chat_history")
    
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    
    val chatHistory: Flow<List<ChatMessageData>> = dataStore.data.map { preferences ->
        val historyJson = preferences[chatHistoryKey] ?: "[]"
        try {
            json.decodeFromString<List<ChatMessageData>>(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun saveChatHistory(messages: List<ChatMessageData>) {
        dataStore.edit { preferences ->
            val historyJson = json.encodeToString(messages)
            preferences[chatHistoryKey] = historyJson
        }
    }
    
    suspend fun clearChatHistory() {
        dataStore.edit { preferences ->
            preferences.remove(chatHistoryKey)
        }
    }
}

