package com.heygude.aichallenge.data.yandex

/**
 * Enum representing available Yandex GPT models
 */
enum class GptModel(val displayName: String, val modelPath: String) {
    LATEST("YandexGPT 5.1 Pro", "yandexgpt-5.1/latest"),
    LITE("YandexGPT 5 Lite", "yandexgpt-5-lite/latest");

    fun getModelUri(folderId: String): String {
        return "gpt://$folderId/$modelPath"
    }
}

