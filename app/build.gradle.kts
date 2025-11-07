import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.heygude.aichallenge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.heygude.aichallenge"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Inject secrets from local.properties into BuildConfig
        val localProps = Properties().apply {
            val propsFile = rootProject.file("local.properties")
            if (propsFile.exists()) {
                propsFile.inputStream().use { this.load(it) }
            }
        }
        val yandexApiKey = (localProps.getProperty("yandex.api.key") ?: "YOUR_YANDEX_API_KEY_HERE")
        val yandexFolderId = (localProps.getProperty("yandex.folder.id") ?: "YOUR_YANDEX_FOLDER_ID_HERE")
        val deepseekApiKey = (localProps.getProperty("deepseek.api.key") ?: "YOUR_DEEPSEEK_API_KEY_HERE")
        buildConfigField("String", "YANDEX_API_KEY", "\"$yandexApiKey\"")
        buildConfigField("String", "YANDEX_FOLDER_ID", "\"$yandexFolderId\"")
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"$deepseekApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.kotlinx.serialization.json)
    // Navigation
    implementation(libs.androidx.navigation.compose)
    // DataStore
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}