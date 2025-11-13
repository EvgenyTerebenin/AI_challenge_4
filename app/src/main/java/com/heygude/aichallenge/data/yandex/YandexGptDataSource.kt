package com.heygude.aichallenge.data.yandex

import com.heygude.aichallenge.data.constants.Secrets
import com.heygude.aichallenge.data.yandex.api.CompletionOptions
import com.heygude.aichallenge.data.yandex.api.Message
import com.heygude.aichallenge.data.yandex.api.YandexCompletionRequest
import com.heygude.aichallenge.data.yandex.api.YandexGptApi
import com.heygude.aichallenge.data.yandex.api.ApiError
import com.heygude.aichallenge.data.yandex.api.YandexTokenizeRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.Retrofit
import retrofit2.HttpException
import java.util.concurrent.TimeUnit
import java.time.Instant
import timber.log.Timber

/**
 * Token information for a request/response
 */
data class TokenInfo(
    val requestTokens: Int,
    val responseTokens: Int,
    val responseTimeMs: Long,
    val costUsd: Double
)

/**
 * Response with text and token information
 */
data class YandexResponse(
    val text: String,
    val tokenInfo: TokenInfo?
)

/**
 * Data source responsible for invoking Yandex GPT API.
 * Later this will be implemented with Retrofit.
 */
interface YandexGptDataSource {
    suspend fun generateResponse(prompt: String, systemPrompt: String, model: GptModel = GptModel.YANDEX_LATEST, temperature: Double = 0.6, maxTokens: Int = 2000, conversationHistory: List<com.heygude.aichallenge.AIAgentViewModel.ChatMessage> = emptyList()): Result<String>
    suspend fun generateResponseWithTokens(prompt: String, systemPrompt: String, model: GptModel = GptModel.YANDEX_LATEST, temperature: Double = 0.6, maxTokens: Int = 2000, conversationHistory: List<com.heygude.aichallenge.AIAgentViewModel.ChatMessage> = emptyList()): Result<YandexResponse>
}

class DefaultYandexGptDataSource : YandexGptDataSource {
    private val baseUrl = "https://llm.api.cloud.yandex.net/"
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
            .newBuilder()
            .addHeader("Authorization", "Api-Key ${Secrets.YANDEX_API_KEY}")
            .addHeader("x-folder-id", Secrets.YANDEX_FOLDER_ID)
            .build()
        chain.proceed(request)
    }

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Increased for large responses
            .writeTimeout(60, TimeUnit.SECONDS) // Increased for large requests
            .build()
    }

    private val api: YandexGptApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(client)
            .build()
            .create(YandexGptApi::class.java)
    }

    override suspend fun generateResponse(prompt: String, systemPrompt: String, model: GptModel, temperature: Double, maxTokens: Int, conversationHistory: List<com.heygude.aichallenge.AIAgentViewModel.ChatMessage>): Result<String> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            Timber.w("YandexGPT: Prompt is blank")
            return@withContext Result.failure(IllegalArgumentException("Prompt must not be blank"))
        }
        return@withContext try {
            val timestamp = Instant.now().toString()
            val modelDisplayName = model.displayName
            Timber.d("YandexGPT: Starting request - Model: $modelDisplayName, Temperature: $temperature, Prompt length: ${prompt.length}")
            val formattedSystemPrompt = """$systemPrompt
                ВАЖНО: Всегда отвечай строго в следующем формате в виде строки, но чтоб его можно было распарсить как JSON.
Не используй Markdown совершенно. Не оборачивай ответ в тройные обратные кавычки ``` ни в начале, ни в конце. Отвечай чистой строкой без какого-либо форматирования.

{
  \"status\": \"success\",
  \"data\": { 
    \"text\": \"Основной текст ответа от модели\",
    \"metadata\": {
      \"model\": \"$modelDisplayName\",
      \"timestamp\": \"$timestamp\",
      \"tokens_used\": количество использованных токенов
    }
  },
  \"error\": null
}

Или в случае ошибки:

{
  \"status\": \"error\",
  \"data\": null,
  \"error\": {
    \"code\": \"код ошибки\",
    \"message\": \"Описание ошибки\",
    \"details\": {
      \"retry_after\": 60
    }
  }
}

ОБЯЗАТЕЛЬНО используй timestamp: \"$timestamp\" в поле metadata.timestamp
ОБЯЗАТЕЛЬНО не используй тройные обратные кавычки ``` в начале и конце ответа, не используй блоки кода и не добавляй любые символы форматирования."""

            // Build message list with full conversation history
            val messages = mutableListOf<Message>()
            messages.add(Message(role = "system", text = formattedSystemPrompt))
            
            // Add all conversation history (sorted by timestamp to maintain order)
            val sortedHistory = conversationHistory.sortedBy { it.timestampMs }
            Timber.d("YandexGPT: Adding ${sortedHistory.size} messages from conversation history")
            sortedHistory.forEach { msg ->
                messages.add(Message(
                    role = if (msg.isUser) "user" else "assistant",
                    text = msg.text
                ))
            }
            
            // Add current user prompt as the last message
            messages.add(Message(role = "user", text = prompt))
            Timber.d("YandexGPT: Total messages in request: ${messages.size} (1 system + ${sortedHistory.size} history + 1 current)")
            
            // Clamp temperature to model's valid range
            val clampedTemperature = temperature.coerceIn(model.provider.minTemperature, model.provider.maxTemperature)
            val request = YandexCompletionRequest(
                modelUri = model.getModelUri(Secrets.YANDEX_FOLDER_ID),
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = clampedTemperature,
                    maxTokens = maxTokens.coerceIn(1, 32000)
                ),
                messages = messages
            )
            Timber.d("YandexGPT: Request - ModelUri: ${request.modelUri}, Temperature: ${request.completionOptions.temperature}, Messages count: ${request.messages.size}")
            val response = api.completion(request)
            val raw = response.result?.alternatives?.firstOrNull()?.message?.text ?: ""
            Timber.d("YandexGPT: Response received - Raw length: ${raw.length}, Has alternatives: ${response.result?.alternatives != null}")
            val cleaned = stripCodeFences(raw)
            Timber.d("YandexGPT: Response cleaned - Final length: ${cleaned.length}")
            Result.success(cleaned)
        } catch (t: Throwable) {
            if (t is HttpException) {
                val raw = t.response()?.errorBody()?.string()
                val parsed = try {
                    raw?.let { json.decodeFromString(ApiError.serializer(), it) }
                } catch (_: Throwable) { null }
                val message = parsed?.error?.message ?: parsed?.message ?: raw ?: t.message()
                Timber.e(t, "YandexGPT: HTTP Error - Code: ${t.code()}, Message: $message")
                Result.failure(IllegalStateException(message))
            } else {
                Timber.e(t, "YandexGPT: Request failed - ${t.message}")
                Result.failure(t)
            }
        }
    }

    override suspend fun generateResponseWithTokens(prompt: String, systemPrompt: String, model: GptModel, temperature: Double, maxTokens: Int, conversationHistory: List<com.heygude.aichallenge.AIAgentViewModel.ChatMessage>): Result<YandexResponse> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            Timber.w("YandexGPT: Prompt is blank")
            return@withContext Result.failure(IllegalArgumentException("Prompt must not be blank"))
        }
        return@withContext try {
            val timestamp = Instant.now().toString()
            val modelDisplayName = model.displayName
            val modelUri = model.getModelUri(Secrets.YANDEX_FOLDER_ID)
            Timber.d("YandexGPT: Starting request - Model: $modelDisplayName, Temperature: $temperature, Prompt length: ${prompt.length}")
            
            val formattedSystemPrompt = """$systemPrompt
                ВАЖНО: Всегда отвечай строго в следующем формате в виде строки, но чтоб его можно было распарсить как JSON.
Не используй Markdown совершенно. Не оборачивай ответ в тройные обратные кавычки ``` ни в начале, ни в конце. Отвечай чистой строкой без какого-либо форматирования.

{
  \"status\": \"success\",
  \"data\": { 
    \"text\": \"Основной текст ответа от модели\",
    \"metadata\": {
      \"model\": \"$modelDisplayName\",
      \"timestamp\": \"$timestamp\",
      \"tokens_used\": количество использованных токенов
    }
  },
  \"error\": null
}

Или в случае ошибки:

{
  \"status\": \"error\",
  \"data\": null,
  \"error\": {
    \"code\": \"код ошибки\",
    \"message\": \"Описание ошибки\",
    \"details\": {
      \"retry_after\": 60
    }
  }
}

ОБЯЗАТЕЛЬНО используй timestamp: \"$timestamp\" в поле metadata.timestamp
ОБЯЗАТЕЛЬНО не используй тройные обратные кавычки ``` в начале и конце ответа, не используй блоки кода и не добавляй любые символы форматирования."""

            // Build message list with full conversation history
            val messages = mutableListOf<Message>()
            messages.add(Message(role = "system", text = formattedSystemPrompt))
            
            // Add all conversation history (sorted by timestamp to maintain order)
            val sortedHistory = conversationHistory.sortedBy { it.timestampMs }
            Timber.d("YandexGPT: Adding ${sortedHistory.size} messages from conversation history")
            sortedHistory.forEach { msg ->
                messages.add(Message(
                    role = if (msg.isUser) "user" else "assistant",
                    text = msg.text
                ))
            }
            
            // Add current user prompt as the last message
            messages.add(Message(role = "user", text = prompt))
            Timber.d("YandexGPT: Total messages in request: ${messages.size} (1 system + ${sortedHistory.size} history + 1 current)")
            
            // Count request tokens using tokenizer API
            val requestText = messages.joinToString("\n\n") { "${it.role}: ${it.text}" }
            val tokenizeRequest = YandexTokenizeRequest(
                modelUri = modelUri,
                text = requestText
            )
            val tokenizeResponse = api.tokenize(tokenizeRequest)
            val requestTokens = tokenizeResponse.tokens?.size ?: 0
            Timber.d("YandexGPT: Request tokens: $requestTokens")

            // Clamp temperature to model's valid range
            val clampedTemperature = temperature.coerceIn(model.provider.minTemperature, model.provider.maxTemperature)
            val request = YandexCompletionRequest(
                modelUri = modelUri,
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = clampedTemperature,
                    maxTokens = maxTokens.coerceIn(1, 32000)
                ),
                messages = messages
            )
            Timber.d("YandexGPT: Request - ModelUri: ${request.modelUri}, Temperature: ${request.completionOptions.temperature}, Messages count: ${request.messages.size}")
            
            // Track response time
            val startTime = System.currentTimeMillis()
            val response = api.completion(request)
            val endTime = System.currentTimeMillis()
            val responseTimeMs = endTime - startTime
            
            val raw = response.result?.alternatives?.firstOrNull()?.message?.text ?: ""
            Timber.d("YandexGPT: Response received - Raw length: ${raw.length}, Has alternatives: ${response.result?.alternatives != null}, Response time: ${responseTimeMs}ms")
            
            // Extract usage statistics from response
            val usage = response.result?.usage
            val inputTextTokens = usage?.inputTextTokens?.toIntOrNull() 
                ?: usage?.input_tokens?.toIntOrNull() 
                ?: requestTokens
            val completionTokens = usage?.completionTokens?.toIntOrNull() 
                ?: usage?.completion_tokens?.toIntOrNull() 
                ?: 0
            
            // If completion tokens are not in usage, try to count them from response text
            val responseTokens = if (completionTokens == 0 && raw.isNotEmpty()) {
                try {
                    val responseTokenizeRequest = YandexTokenizeRequest(
                        modelUri = modelUri,
                        text = raw
                    )
                    val responseTokenizeResponse = api.tokenize(responseTokenizeRequest)
                    responseTokenizeResponse.tokens?.size ?: 0
                } catch (e: Exception) {
                    Timber.w(e, "YandexGPT: Failed to tokenize response text")
                    0
                }
            } else {
                completionTokens
            }
            
            // Calculate cost: $0.006668 per 1,000 tokens
            val totalTokens = inputTextTokens + responseTokens
            val costUsd = (totalTokens / 1000.0) * 0.006668
            
            Timber.d("YandexGPT: Tokens - Input: $inputTextTokens, Output: $responseTokens, Total: $totalTokens, Cost: $$costUsd")
            
            val cleaned = stripCodeFences(raw)
            Timber.d("YandexGPT: Response cleaned - Final length: ${cleaned.length}")
            
            val tokenInfo = TokenInfo(
                requestTokens = inputTextTokens,
                responseTokens = responseTokens,
                responseTimeMs = responseTimeMs,
                costUsd = costUsd
            )
            
            Result.success(YandexResponse(cleaned, tokenInfo))
        } catch (t: Throwable) {
            if (t is HttpException) {
                val raw = t.response()?.errorBody()?.string()
                val parsed = try {
                    raw?.let { json.decodeFromString(ApiError.serializer(), it) }
                } catch (_: Throwable) { null }
                val message = parsed?.error?.message ?: parsed?.message ?: raw ?: t.message()
                Timber.e(t, "YandexGPT: HTTP Error - Code: ${t.code()}, Message: $message")
                Result.failure(IllegalStateException(message))
            } else {
                Timber.e(t, "YandexGPT: Request failed - ${t.message}")
                Result.failure(t)
            }
        }
    }
}

private fun stripCodeFences(input: String): String {
    val trimmed = input.trim()
    // Handle fenced blocks with optional language label. DOTALL via (?s)
    val fencedRegex = Regex("""(?s)^\s*```[a-zA-Z0-9_\-]*\s*\n(.*?)\s*\n?```\s*$""")
    val match = fencedRegex.find(trimmed)
    if (match != null && match.groupValues.size > 1) {
        return match.groupValues[1].trim()
    }
    // Simple case: entire string starts and ends with ``` on one line
    if (trimmed.startsWith("```") && trimmed.endsWith("```") && trimmed.length >= 6) {
        return trimmed.removePrefix("```").removeSuffix("```").trim()
    }
    return trimmed
}


