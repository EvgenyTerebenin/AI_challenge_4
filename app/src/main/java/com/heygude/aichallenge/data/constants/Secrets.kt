package com.heygude.aichallenge.data.constants

import com.heygude.aichallenge.BuildConfig

object Secrets {
    // Read from BuildConfig, which is populated from local.properties
    // Add to local.properties (not committed):
    // yandex.api.key=YOUR_REAL_API_KEY
    // yandex.folder.id=YOUR_REAL_FOLDER_ID
    val YANDEX_API_KEY: String = BuildConfig.YANDEX_API_KEY
    val YANDEX_FOLDER_ID: String = BuildConfig.YANDEX_FOLDER_ID
}

