package com.heygude.aichallenge.presentation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.heygude.aichallenge.data.yandex.GptModel

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsManager(private val context: Context) {
    private val dataStore = context.settingsDataStore
    
    private val temperatureKey = doublePreferencesKey("temperature")
    private val selectedModelKey = stringPreferencesKey("selected_model")
    
    val temperature: Flow<Double> = dataStore.data.map { preferences ->
        preferences[temperatureKey] ?: 0.6 // Default temperature
    }
    
    val selectedModel: Flow<GptModel> = dataStore.data.map { preferences ->
        val modelName = preferences[selectedModelKey] ?: GptModel.YANDEX_LATEST.name
        try {
            GptModel.valueOf(modelName)
        } catch (e: Exception) {
            GptModel.YANDEX_LATEST
        }
    }
    
    suspend fun setTemperature(temperature: Double) {
        dataStore.edit { preferences ->
            preferences[temperatureKey] = temperature.coerceIn(0.0, 2.0)
        }
    }
    
    suspend fun setSelectedModel(model: GptModel) {
        dataStore.edit { preferences ->
            preferences[selectedModelKey] = model.name
        }
    }
}

