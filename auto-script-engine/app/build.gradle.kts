plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.script.engine"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.script.engine"
        minSdk = 26          // 图像/OCR 查找依赖 takeScreenshot(API 30+),运行时已做降级保护
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // 按 CPU 架构拆分 APK,减小体积(arm64-v8a 覆盖绝大多数现代手机)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // WorkManager(定时调度)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // JSON 解析
    implementation("com.google.code.gson:gson:2.10.1")

    // Google ML Kit —— 文字识别(中文 + 通用)
    implementation("com.google.mlkit:text-recognition:16.0.0")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")
}
