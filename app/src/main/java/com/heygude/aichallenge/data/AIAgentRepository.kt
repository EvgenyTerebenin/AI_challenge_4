package com.heygude.aichallenge.data

import com.heygude.aichallenge.data.yandex.YandexGptDataSource

/**
 * Repository abstraction to support multiple GPT providers.
 * It currently mirrors the Yandex contract for simplicity.
 */
interface AIAgentRepository {
    suspend fun generateResponse(prompt: String, systemPrompt: String): Result<String>
}

/**
 * Default implementation that delegates to a concrete data source
 * (e.g., Yandex, OpenAI, etc.).
 */
class DefaultAIAgentRepository(
    private val dataSource: YandexGptDataSource
) : AIAgentRepository {
    override suspend fun generateResponse(prompt: String, systemPrompt: String): Result<String> =
        dataSource.generateResponse(prompt, systemPrompt)
}


