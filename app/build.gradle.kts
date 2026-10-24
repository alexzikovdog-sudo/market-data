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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 👇 ЭТИ ДВА БЛОКА РЕШАЮТ ОШИБКУ С JVM TARGET 👇
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
    // 👆 ЭТИ ДВА БЛОКА РЕШАЮТ ОШИБКУ С JVM TARGET 👆
}

dependencies {
    // Базовые библиотеки Android (нужны для нормальной работы приложения)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
