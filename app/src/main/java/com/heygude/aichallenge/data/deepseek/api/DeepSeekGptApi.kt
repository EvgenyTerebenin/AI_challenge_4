package com.heygude.aichallenge.data.deepseek.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

internal interface DeepSeekGptApi {
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body body: DeepSeekChatRequest
    ): DeepSeekChatResponse
}

@Serializable
internal data class DeepSeekChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.6,
    val max_tokens: Int = 2000,
    val stream: Boolean = false
)

@Serializable
internal data class DeepSeekMessage(
    val role: String,
    val content: String
)

@Serializable
internal data class DeepSeekChatResponse(
    val id: String? = null,
    val object_type: String? = null,
    val created: Long? = null,
    val model: String? = null,
    val choices: List<DeepSeekChoice>? = null,
    val usage: DeepSeekUsage? = null
)

@Serializable
internal data class DeepSeekChoice(
    val index: Int? = null,
    val message: DeepSeekMessage? = null,
    val finish_reason: String? = null
)

@Serializable
internal data class DeepSeekUsage(
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val total_tokens: Int? = null
)

@Serializable
internal data class DeepSeekApiError(
    val error: DeepSeekErrorDetail? = null
)

@Serializable
internal data class DeepSeekErrorDetail(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

