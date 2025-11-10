package com.heygude.aichallenge.data

import com.heygude.aichallenge.data.yandex.GptModel
import com.heygude.aichallenge.data.yandex.ModelProvider
import com.heygude.aichallenge.data.yandex.YandexGptDataSource
import com.heygude.aichallenge.data.deepseek.DeepSeekGptDataSource

/**
 * Repository abstraction to support multiple GPT providers.
 */
interface AIAgentRepository {
    suspend fun generateResponse(prompt: String, systemPrompt: String, model: GptModel = GptModel.YANDEX_LATEST, temperature: Double = 0.6): Result<String>
}

/**
 * Default implementation that delegates to the appropriate data source
 * based on the model's provider (Yandex, DeepSeek, etc.).
 */
class DefaultAIAgentRepository(
    private val yandexDataSource: YandexGptDataSource,
    private val deepseekDataSource: DeepSeekGptDataSource
) : AIAgentRepository {
    override suspend fun generateResponse(prompt: String, systemPrompt: String, model: GptModel, temperature: Double): Result<String> {
        return when (model.provider) {
            ModelProvider.YANDEX -> yandexDataSource.generateResponse(prompt, systemPrompt, model, temperature)
            ModelProvider.DEEPSEEK -> deepseekDataSource.generateResponse(prompt, systemPrompt, model, temperature)
        }
    }
}


