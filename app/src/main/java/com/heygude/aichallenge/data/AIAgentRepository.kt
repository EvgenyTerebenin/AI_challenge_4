package com.heygude.aichallenge.data

import com.heygude.aichallenge.data.yandex.GptModel
import com.heygude.aichallenge.data.yandex.ModelProvider
import com.heygude.aichallenge.data.yandex.YandexGptDataSource
import com.heygude.aichallenge.data.yandex.YandexResponse
import com.heygude.aichallenge.data.deepseek.DeepSeekGptDataSource

/**
 * Repository abstraction to support multiple GPT providers.
 */
interface AIAgentRepository {
    suspend fun generateResponse(prompt: String, systemPrompt: String, model: GptModel = GptModel.YANDEX_LATEST, temperature: Double = 0.6, maxTokens: Int = 2000): Result<String>
    suspend fun generateResponseWithTokens(prompt: String, systemPrompt: String, model: GptModel = GptModel.YANDEX_LATEST, temperature: Double = 0.6, maxTokens: Int = 2000): Result<YandexResponse?>
}

/**
 * Default implementation that delegates to the appropriate data source
 * based on the model's provider (Yandex, DeepSeek, etc.).
 */
class DefaultAIAgentRepository(
    private val yandexDataSource: YandexGptDataSource,
    private val deepseekDataSource: DeepSeekGptDataSource
) : AIAgentRepository {
    override suspend fun generateResponse(prompt: String, systemPrompt: String, model: GptModel, temperature: Double, maxTokens: Int): Result<String> {
        return when (model.provider) {
            ModelProvider.YANDEX -> yandexDataSource.generateResponse(prompt, systemPrompt, model, temperature, maxTokens)
            ModelProvider.DEEPSEEK -> deepseekDataSource.generateResponse(prompt, systemPrompt, model, temperature, maxTokens)
        }
    }
    
    override suspend fun generateResponseWithTokens(prompt: String, systemPrompt: String, model: GptModel, temperature: Double, maxTokens: Int): Result<YandexResponse?> {
        return when (model.provider) {
            ModelProvider.YANDEX -> yandexDataSource.generateResponseWithTokens(prompt, systemPrompt, model, temperature, maxTokens).map { it }
            ModelProvider.DEEPSEEK -> {
                // For DeepSeek, return null token info for now
                deepseekDataSource.generateResponse(prompt, systemPrompt, model, temperature, maxTokens).map { text ->
                    YandexResponse(text, null)
                }
            }
        }
    }
}


