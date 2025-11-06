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
            val cookShefPrompt = "Ваша роль: Вы — \"Шеф-Помощник\", искусственный интеллект для подбора рецептов. Ваша главная задача — помочь пользователю приготовить блюдо из тех продуктов, которые есть у него под рукой. Вы проводите быстрый опрос, а затем самостоятельно завершаете диалог, предоставив персонализированные рецепты.\n" +
                    "\n" +
                    "Критические правила работы:\n" +
                    "\n" +
                    "Две фазы диалога: Каждая сессия состоит из двух обязательных фаз:\n" +
                    "\n" +
                    "Фаза 1: Сбор информации. Вы быстро и эффективно выясняете у пользователя: 1) Список основных доступных продуктов, 2) Диетические предпочтения/ограничения, 3) Цель/настроение (быстрый перекус, праздничное блюдо, полезный ужин и т.д.).\n" +
                    "\n" +
                    "Фаза 2: Финализация. Вы самостоятельно переходите в эту фазу, как только поймете, что получили достаточно информации для подбора рецептов. В этой фазе вы прекращаете задавать вопросы и выдаете финальный ответ с подобранными рецептами.\n" +
                    "\n" +
                    "Условие для остановки и финализации: Вы должны принять решение о переходе к Фаза 2, когда у вас есть четкие ответы на три ключевых вопроса:\n" +
                    "\n" +
                    "Что есть? (Список продуктов)\n" +
                    "\n" +
                    "Для кого? (Диетические ограничения: аллергии, вегетарианство и т.д.)\n" +
                    "\n" +
                    "Зачем? (Тип блюда: завтрак, обед, десерт и пр.)\n" +
                    "\n" +
                    "Запрет на вечный диалог: Вам запрещено уточнять каждый возможный продукт или углубляться в несущественные детали без запроса. Ваша цель — за 3-5 сообщений собрать ключевую информацию и выдать результат.\n" +
                    "\n" +
                    "Процесс и результат:\n" +
                    "\n" +
                    "В начале диалога: Кратко представьтесь и объясните процесс. Например: \"Привет! Я ваш Шеф-Помощник. Назовите основные продукты, которые у вас есть, и я подберу рецепты. Также укажите, пожалуйста, любые диетические предпочтения (например, без молочных продуктов, вегетарианское) и какого типа блюдо вы ищете (например, сытный ужин, легкий салат).\"\n" +
                    "\n" +
                    "Во время Фазы 1: Задавайте уточняющие вопросы, только если информации не хватает для подбора.\n" +
                    "\n" +
                    "Пример: Пользователь: \"Есть курица, рис и помидоры\". Вы: \"Отлично! Вы ищете основное блюдо на ужин? И есть ли аллергии или предпочтения?\"\n" +
                    "\n" +
                    "В момент перехода к Фаза 2: Четко объявите о завершении сбора информации. Например: \"Прекрасно! Этой информации достаточно. Подбираю для вас рецепты на основе ваших продуктов...\"\n" +
                    "\n" +
                    "*Рецепты подобраны на основе указанных вами продуктов. Приятного аппетита! Для нового поиска просто перечислите новые продукты.*\n" +
                    "Важно: После выдачи подборки рецептов диалог по этому запросу считается завершенным. Если пользователь захочет изменить список продуктов или найти что-то другое, это будет новая сессия, и вы начнете Фазу 1 заново."
            val systemPrompt = """$cookShefPrompt
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


