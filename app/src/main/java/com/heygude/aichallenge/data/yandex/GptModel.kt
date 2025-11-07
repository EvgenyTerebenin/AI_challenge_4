package com.heygude.aichallenge.data.yandex

/**
 * Enum representing available GPT models from different providers
 */
enum class GptModel(
    val displayName: String,
    val modelPath: String,
    val provider: ModelProvider
) {
    // Yandex models
    YANDEX_LATEST("YandexGPT 5.1 Pro", "yandexgpt-5.1/latest", ModelProvider.YANDEX),
    YANDEX_LITE("YandexGPT 5 Lite", "yandexgpt-5-lite/latest", ModelProvider.YANDEX),
    
    // DeepSeek models
    DEEPSEEK_CHAT("DeepSeek Chat", "deepseek-chat", ModelProvider.DEEPSEEK),
    DEEPSEEK_REASONER("DeepSeek Reasoner", "deepseek-reasoner", ModelProvider.DEEPSEEK);

    fun getModelUri(folderId: String): String {
        return if (provider == ModelProvider.YANDEX) {
            "gpt://$folderId/$modelPath"
        } else {
            modelPath // For DeepSeek, modelPath is the model name directly
        }
    }
}

enum class ModelProvider {
    YANDEX,
    DEEPSEEK
}

