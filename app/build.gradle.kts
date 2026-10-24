plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ru.example.marketdatadownloader"
    compileSdk = 35
    defaultConfig {
        applicationId = "ru.example.marketdatadownloader"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
}
