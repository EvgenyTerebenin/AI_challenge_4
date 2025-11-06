package com.heygude.aichallenge.data.yandex

import com.heygude.aichallenge.data.constants.Secrets
import com.heygude.aichallenge.data.yandex.api.CompletionOptions
import com.heygude.aichallenge.data.yandex.api.Message
import com.heygude.aichallenge.data.yandex.api.YandexCompletionRequest
import com.heygude.aichallenge.data.yandex.api.YandexGptApi
import com.heygude.aichallenge.data.yandex.api.ApiError
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

/**
 * Data source responsible for invoking Yandex GPT API.
 * Later this will be implemented with Retrofit.
 */
interface YandexGptDataSource {
    suspend fun generateResponse(prompt: String): Result<String>
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
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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

    override suspend fun generateResponse(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (prompt.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Prompt must not be blank"))
        }
        return@withContext try {
            val timestamp = Instant.now().toString()
            val autoMobileAIAgentPrompt = "Ты — помощник по подбору автомобилей. Твоя задача — помочь пользователю выбрать подходящий автомобиль, задав уточняющие вопросы, и на основе ответов дать обоснованную рекомендацию.\n" +
                    "\n" +
                    "Правила взаимодействия:\n" +
                    "1. Если пользователь говорит: **\"Помоги мне выбрать автомобиль\"** — ты начинаешь сбор информации.\n" +
                    "2. Ты **задаёшь по одному вопросу**, дожидаешься ответа, затем задаёшь следующий.\n" +
                    "3. Задавай не более трех уточняющих вопросов\n" +
                    "4. После получения всех трёх ответов ты **самостоятельно завершаешь диалог**, выдавая итоговую рекомендацию.\n" +
                    "5. Рекомендация должна быть:\n" +
                    "   - Краткой (3–4 предложения)\n" +
                    "   - Обоснованной (связанной с ответами пользователя)\n" +
                    "   - Конкретной (название модели или типа автомобиля)\n" +
                    "6. После выдачи рекомендации ты **больше не задаёшь вопросы** и не продолжаешь сбор данных.\n" +
                    "\n" +
                    "Ты не отвечаешь на другие темы, пока не завершён сбор информации и выдача результата.\n" +
                    "\n" +
                    "Жди команду пользователя.\n"
            val systemPrompt = """$autoMobileAIAgentPrompt
                ВАЖНО: Всегда отвечай строго в следующем формате в виде строки, но чтоб его можно было распарсить как JSON.
Не используй Markdown совершенно. Не оборачивай ответ в тройные обратные кавычки ``` ни в начале, ни в конце. Отвечай чистой строкой без какого-либо форматирования.

{
  \"status\": \"success\",
  \"data\": { 
    \"text\": \"Основной текст ответа от модели\",
    \"metadata\": {
      \"model\": \"yandexgpt\",
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

            val request = YandexCompletionRequest(
                modelUri = "gpt://${Secrets.YANDEX_FOLDER_ID}/yandexgpt",
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = 0.6,
                    maxTokens = 2000
                ),
                messages = listOf(
                    Message(role = "system", text = systemPrompt),
                    Message(role = "user", text = prompt)
                )
            )
            val response = api.completion(request)
            val raw = response.result?.alternatives?.firstOrNull()?.message?.text ?: ""
            val cleaned = stripCodeFences(raw)
            Result.success(cleaned)
        } catch (t: Throwable) {
            if (t is HttpException) {
                val raw = t.response()?.errorBody()?.string()
                val parsed = try {
                    raw?.let { json.decodeFromString(ApiError.serializer(), it) }
                } catch (_: Throwable) { null }
                val message = parsed?.error?.message ?: parsed?.message ?: raw ?: t.message()
                Result.failure(IllegalStateException(message))
            } else {
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


