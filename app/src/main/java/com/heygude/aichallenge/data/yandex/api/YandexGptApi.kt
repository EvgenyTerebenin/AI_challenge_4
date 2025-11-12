package com.heygude.aichallenge.data.yandex.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

internal interface YandexGptApi {
    @POST("foundationModels/v1/completion")
    suspend fun completion(@Body body: YandexCompletionRequest): YandexCompletionResponse
    
    @POST("foundationModels/v1/tokenize")
    suspend fun tokenize(@Body body: YandexTokenizeRequest): YandexTokenizeResponse
}

@Serializable
internal data class YandexCompletionRequest(
    val modelUri: String,
    val completionOptions: CompletionOptions,
    val messages: List<Message>
)

@Serializable
internal data class CompletionOptions(
    val temperature: Double = 0.6,
    val maxTokens: Int = 2000,
    val stream: Boolean = false
)

@Serializable
internal data class Message(
    val role: String,
    val text: String
)

@Serializable
internal data class YandexCompletionResponse(
    val result: ResultPayload?
)

@Serializable
internal data class ResultPayload(
    val alternatives: List<Alternative>?,
    val usage: Usage? = null
)

@Serializable
internal data class Alternative(
    val message: Message?,
    val status: String?
)

@Serializable
internal data class ApiError(
    val message: String? = null,
    val error: ErrorPayload? = null
)

@Serializable
internal data class ErrorPayload(
    val code: String? = null,
    val message: String? = null
)

@Serializable
internal data class YandexTokenizeRequest(
    val modelUri: String,
    val text: String
)

@Serializable
internal data class YandexTokenizeResponse(
    val tokens: List<TokenInfo>? = null,
    val modelVersion: String? = null
)

@Serializable
internal data class TokenInfo(
    val id: String? = null,
    val text: String? = null,
    val special: Boolean? = null
)

@Serializable
internal data class Usage(
    val inputTextTokens: String? = null,
    val completionTokens: String? = null,
    val totalTokens: String? = null,
    // Alternative field names that might be used
    val input_tokens: String? = null,
    val completion_tokens: String? = null,
    val total_tokens: String? = null
)



