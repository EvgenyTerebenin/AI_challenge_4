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
            val request = YandexCompletionRequest(
                modelUri = "gpt://${Secrets.YANDEX_FOLDER_ID}/yandexgpt",
                completionOptions = CompletionOptions(
                    stream = false,
                    temperature = 0.6,
                    maxTokens = 2000
                ),
                messages = listOf(
                    Message(role = "system", text = "You are a helpful assistant."),
                    Message(role = "user", text = prompt)
                )
            )
            val response = api.completion(request)
            val text = response.result?.alternatives?.firstOrNull()?.message?.text ?: ""
            Result.success(text)
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


