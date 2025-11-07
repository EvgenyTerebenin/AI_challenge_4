package com.heygude.aichallenge.presentation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "system_prompts")

@Serializable
data class SystemPrompt(
    val id: String,
    val name: String,
    val content: String,
    val isDefault: Boolean = false
)

class SystemPromptManager(private val context: Context) {
    private val dataStore = context.dataStore
    private val json = Json { ignoreUnknownKeys = true }
    
    private val promptsKey = stringPreferencesKey("prompts")
    private val currentPromptIdKey = stringPreferencesKey("current_prompt_id")
    
    val allPrompts: Flow<List<SystemPrompt>> = dataStore.data.map { preferences ->
        val promptsJson = preferences[promptsKey] ?: "[]"
        try {
            json.decodeFromString<List<SystemPrompt>>(promptsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    val currentPromptId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[currentPromptIdKey]
    }
    
    val currentPrompt: Flow<SystemPrompt?> = dataStore.data.map { preferences ->
        val promptsJson = preferences[promptsKey] ?: "[]"
        val currentId = preferences[currentPromptIdKey]
        try {
            val prompts = json.decodeFromString<List<SystemPrompt>>(promptsJson)
            currentId?.let { id ->
                prompts.find { it.id == id } ?: prompts.firstOrNull { it.isDefault } ?: prompts.firstOrNull()
            } ?: prompts.firstOrNull { it.isDefault } ?: prompts.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun addPrompt(prompt: SystemPrompt) {
        dataStore.edit { preferences ->
            val promptsJson = preferences[promptsKey] ?: "[]"
            val prompts = try {
                json.decodeFromString<MutableList<SystemPrompt>>(promptsJson)
            } catch (e: Exception) {
                mutableListOf()
            }
            
            // If this is the first prompt or it's marked as default, set it as current
            if (prompts.isEmpty() || prompt.isDefault) {
                preferences[currentPromptIdKey] = prompt.id
            }
            
            prompts.add(prompt)
            preferences[promptsKey] = json.encodeToString(prompts)
        }
    }
    
    suspend fun updatePrompt(prompt: SystemPrompt) {
        dataStore.edit { preferences ->
            val promptsJson = preferences[promptsKey] ?: "[]"
            val prompts = try {
                json.decodeFromString<MutableList<SystemPrompt>>(promptsJson)
            } catch (e: Exception) {
                mutableListOf()
            }
            
            val index = prompts.indexOfFirst { it.id == prompt.id }
            if (index >= 0) {
                prompts[index] = prompt
            }
            
            preferences[promptsKey] = json.encodeToString(prompts)
        }
    }
    
    suspend fun deletePrompt(promptId: String) {
        dataStore.edit { preferences ->
            val promptsJson = preferences[promptsKey] ?: "[]"
            val prompts = try {
                json.decodeFromString<MutableList<SystemPrompt>>(promptsJson)
            } catch (e: Exception) {
                mutableListOf()
            }
            
            prompts.removeAll { it.id == promptId }
            
            // If we deleted the current prompt, set another one as current
            val currentId = preferences[currentPromptIdKey]
            if (currentId == promptId) {
                preferences[currentPromptIdKey] = prompts.firstOrNull()?.id ?: ""
            }
            
            preferences[promptsKey] = json.encodeToString(prompts)
        }
    }
    
    suspend fun setCurrentPrompt(promptId: String) {
        dataStore.edit { preferences ->
            preferences[currentPromptIdKey] = promptId
        }
    }
    
    suspend fun initializeDefaultPrompt() {
        dataStore.edit { preferences ->
            val promptsJson = preferences[promptsKey]
            if (promptsJson == null) {
                // Initialize with default prompt
                val defaultPrompt = SystemPrompt(
                    id = "default",
                    name = "Шеф-Помощник",
                    content = """Ваша роль: Вы — "Шеф-Помощник", искусственный интеллект для подбора рецептов. Ваша главная задача — помочь пользователю приготовить блюдо из тех продуктов, которые есть у него под рукой. Вы проводите быстрый опрос, а затем самостоятельно завершаете диалог, предоставив персонализированные рецепты.

Критические правила работы:

Две фазы диалога: Каждая сессия состоит из двух обязательных фаз:

Фаза 1: Сбор информации. Вы быстро и эффективно выясняете у пользователя: 1) Список основных доступных продуктов, 2) Диетические предпочтения/ограничения, 3) Цель/настроение (быстрый перекус, праздничное блюдо, полезный ужин и т.д.).

Фаза 2: Финализация. Вы самостоятельно переходите в эту фазу, как только поймете, что получили достаточно информации для подбора рецептов. В этой фазе вы прекращаете задавать вопросы и выдаете финальный ответ с подобранными рецептами.

Условие для остановки и финализации: Вы должны принять решение о переходе к Фаза 2, когда у вас есть четкие ответы на три ключевых вопроса:

Что есть? (Список продуктов)

Для кого? (Диетические ограничения: аллергии, вегетарианство и т.д.)

Зачем? (Тип блюда: завтрак, обед, десерт и пр.)

Запрет на вечный диалог: Вам запрещено уточнять каждый возможный продукт или углубляться в несущественные детали без запроса. Ваша цель — за 3-5 сообщений собрать ключевую информацию и выдать результат.

Процесс и результат:

В начале диалога: Кратко представьтесь и объясните процесс. Например: "Привет! Я ваш Шеф-Помощник. Назовите основные продукты, которые у вас есть, и я подберу рецепты. Также укажите, пожалуйста, любые диетические предпочтения (например, без молочных продуктов, вегетарианское) и какого типа блюдо вы ищете (например, сытный ужин, легкий салат)."

Во время Фазы 1: Задавайте уточняющие вопросы, только если информации не хватает для подбора.

Пример: Пользователь: "Есть курица, рис и помидоры". Вы: "Отлично! Вы ищете основное блюдо на ужин? И есть ли аллергии или предпочтения?"

В момент перехода к Фаза 2: Четко объявите о завершении сбора информации. Например: "Прекрасно! Этой информации достаточно. Подбираю для вас рецепты на основе ваших продуктов..."

*Рецепты подобраны на основе указанных вами продуктов. Приятного аппетита! Для нового поиска просто перечислите новые продукты.*
Важно: После выдачи подборки рецептов диалог по этому запросу считается завершенным. Если пользователь захочет изменить список продуктов или найти что-то другое, это будет новая сессия, и вы начнете Фазу 1 заново.""",
                    isDefault = true
                )
                preferences[promptsKey] = json.encodeToString(listOf(defaultPrompt))
                preferences[currentPromptIdKey] = defaultPrompt.id
            }
        }
    }
}

