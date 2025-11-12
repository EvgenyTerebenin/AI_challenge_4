package com.heygude.aichallenge.data.deepseek

import com.heygude.aichallenge.data.constants.Secrets
import com.heygude.aichallenge.data.deepseek.api.DeepSeekChatRequest
import com.heygude.aichallenge.data.deepseek.api.DeepSeekGptApi
import com.heygude.aichallenge.data.deepseek.api.DeepSeekMessage
import com.heygude.aichallenge.data.yandex.GptModel
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
 * Data source responsible for invoking DeepSeek GPT API.
 */
interface DeepSeekGptDataSource {
    suspend fun generateResponse(prompt: String, systemPrompt: String, model: GptModel, temperature: Double = 0.6, maxTokens: Int = 2000): Result<String>
}

class DefaultDeepSeekGptDataSource : DeepSeekGptDataSource {
    private val baseUrl = "https://api.deepseek.com/v1/"
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val client: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val api: DeepSeekGptApi by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .client(client)
            .build()
            .create(DeepSeekGptApi::class.java)
    }

    override suspend fun generateResponse(prompt: String, systemPrompt: String, model: GptModel, temperature: Double, maxTokens: Int): Result<String> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            Timber.w("DeepSeekGPT: Prompt is blank")
            return@withContext Result.failure(IllegalArgumentException("Prompt must not be blank"))
        }
        return@withContext try {
            val timestamp = Instant.now().toString()
            val modelDisplayName = model.displayName
            Timber.d("DeepSeekGPT: Starting request - Model: $modelDisplayName, Temperature: $temperature, Prompt length: ${prompt.length}")
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

            // Clamp temperature to model's valid range
            val clampedTemperature = temperature.coerceIn(model.provider.minTemperature, model.provider.maxTemperature)
            val request = DeepSeekChatRequest(
                model = model.modelPath, // e.g., "deepseek-chat" or "deepseek-reasoner"
                messages = listOf(
                    DeepSeekMessage(role = "system", content = formattedSystemPrompt),
                    DeepSeekMessage(role = "user", content = prompt)
                ),
                temperature = clampedTemperature,
                max_tokens = maxTokens.coerceIn(1, 32000),
                stream = false
            )
            Timber.d("DeepSeekGPT: Request - Model: ${request.model}, Temperature: ${request.temperature}, Messages count: ${request.messages.size}")
            val response = api.chatCompletion(
                authorization = "Bearer ${Secrets.DEEPSEEK_API_KEY}",
                body = request
            )
            val raw = response.choices?.firstOrNull()?.message?.content ?: ""
            Timber.d("DeepSeekGPT: Response received - Raw length: ${raw.length}, Has choices: ${response.choices != null}")
            val cleaned = stripCodeFences(raw)
            Timber.d("DeepSeekGPT: Response cleaned - Final length: ${cleaned.length}")
            Result.success(cleaned)
        } catch (t: Throwable) {
            if (t is HttpException) {
                val raw = t.response()?.errorBody()?.string()
                val parsed = try {
                    raw?.let { json.decodeFromString<com.heygude.aichallenge.data.deepseek.api.DeepSeekApiError>(it) }
                } catch (_: Throwable) { null }
                val message = parsed?.error?.message ?: raw ?: t.message() ?: "Unknown error"
                Timber.e(t, "DeepSeekGPT: HTTP Error - Code: ${t.code()}, Message: $message")
                Result.failure(IllegalStateException(message))
            } else {
                Timber.e(t, "DeepSeekGPT: Request failed - ${t.message}")
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

