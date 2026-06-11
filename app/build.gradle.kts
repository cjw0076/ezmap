import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Keep app build output outside the Korean path, but on Windows keep it on the project drive for KSP.
val isWindows = System.getProperty("os.name").contains("Windows", ignoreCase = true)
val projectDriveRoot = rootProject.layout.projectDirectory.asFile.toPath().root?.toString()
val defaultAppBuildDir = if (isWindows && projectDriveRoot != null) {
    File(projectDriveRoot, "gradle-builds/ezmap-app").absolutePath
} else {
    File(System.getProperty("user.home"), ".gradle-builds/ezmap-app").absolutePath
}
val appBuildDir = providers.gradleProperty("ezmapAppBuildDir").orNull
    ?: System.getenv("EZMAP_APP_BUILD_DIR")
    ?: defaultAppBuildDir
layout.buildDirectory.set(file(appBuildDir))

android {
    namespace = "com.example.ez_capstone"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ez_capstone"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // API keys: keys.properties(팀 공유, 커밋됨) → local.properties(개인, git-ignored)가 덮어씀.
        // 팀원은 pull 후 sdk.dir만 있으면 빌드 가능(Android Studio가 자동 생성).
        val props = Properties()
        rootProject.file("keys.properties").takeIf { it.exists() }
            ?.inputStream()?.use { props.load(it) }
        rootProject.file("local.properties").takeIf { it.exists() }
            ?.inputStream()?.use { props.load(it) }

        fun prop(key: String): String = props.getProperty(key, "")
        // 필수 키 (체험 모드 + 개발용)
        buildConfigField("String", "TRIAL_GEMINI_KEY", "\"${prop("GEMINI_API_KEY")}\"")
        buildConfigField("String", "TRIAL_KAKAO_KEY", "\"${prop("KAKAO_REST_KEY")}\"")
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"${prop("KAKAO_NATIVE_APP_KEY")}\"")
        // 선택 키
        buildConfigField("String", "KMA_API_KEY", "\"${prop("KMA_API_KEY")}\"")
        buildConfigField("String", "OPINET_API_KEY", "\"${prop("OPINET_API_KEY")}\"")
        buildConfigField("String", "NAVER_CLIENT_ID", "\"${prop("NAVER_CLIENT_ID")}\"")
        buildConfigField("String", "NAVER_CLIENT_SECRET", "\"${prop("NAVER_CLIENT_SECRET")}\"")
        buildConfigField("String", "NAVER_MAP_CLIENT_ID", "\"${prop("NAVER_MAP_CLIENT_ID")}\"")
        buildConfigField("String", "NAVER_MAP_CLIENT_SECRET", "\"${prop("NAVER_MAP_CLIENT_SECRET")}\"")
        buildConfigField("String", "ODSAY_API_KEY", "\"${prop("ODSAY_API_KEY")}\"")
        // Spotify (음악 제어, OAuth PKCE — client ID만 필요)
        buildConfigField("String", "SPOTIFY_CLIENT_ID", "\"${prop("SPOTIFY_CLIENT_ID")}\"")
        // Porcupine 웨이크워드
        buildConfigField("String", "PORCUPINE_ACCESS_KEY", "\"${prop("PORCUPINE_ACCESS_KEY")}\"")
        // Sentry DSN (옵트인 크래시 리포팅, 비어있으면 no-op)
        buildConfigField("String", "SENTRY_DSN", "\"${prop("SENTRY_DSN")}\"")
        // Kakao Map 초기화 키 (MapSDK) — local.properties에 보관, git 제외
        buildConfigField("String", "KAKAO_MAP_KEY", "\"${prop("KAKAO_MAP_KEY")}\"")

        // Kakao SDK Native AppKey → Manifest placeholder
        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = prop("KAKAO_NATIVE_APP_KEY")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    // 릴리즈 서명 (key.properties는 git-ignored, 없으면 서명 미적용)
    val keyPropertiesFile = rootProject.file("key.properties")
    val keyProperties = Properties().apply {
        if (keyPropertiesFile.exists()) keyPropertiesFile.inputStream().use { load(it) }
    }
    signingConfigs {
        create("release") {
            (keyProperties["storeFile"] as? String)?.let { storeFile = file(it) }
            storePassword = keyProperties["storePassword"] as? String
            keyAlias = keyProperties["keyAlias"] as? String
            keyPassword = keyProperties["keyPassword"] as? String
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (keyPropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        viewBinding = true
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.all {
            it.jvmArgs("-Dfile.encoding=UTF-8", "-Xmx512m", "-Djdk.attach.allowAttachSelf=true")
        }
        unitTests.isReturnDefaultValues = true  // android.util.Log 등 stub 반환
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Testing
    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("com.google.dagger:hilt-android-testing:2.51.1")
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("io.mockk:mockk-android:1.13.10")
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // MediaPipe — 온디바이스 Text Embedding (Phase 10)
    implementation("com.google.mediapipe:tasks-text:0.10.14")

    // Lottie — voice/agent interaction motion assets
    implementation("com.airbnb.android:lottie-compose:6.4.0")

    // Retrofit + OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Kakao Map SDK
    implementation("com.kakao.maps.open:android:2.12.18")

    // Kakao Login SDK
    implementation("com.kakao.sdk:v2-user:2.20.6")

    // Kakao Navi SDK
    implementation("com.kakao.sdk:v2-navi:2.20.6")

    // Vosk (offline STT for command fallback)
    implementation("com.alphacephei:vosk-android:0.3.47")

    // Porcupine (wake word detection)
    implementation("ai.picovoice:porcupine-android:4.0.0")

    // Google Location Services
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // ViewPager2
    implementation("androidx.viewpager2:viewpager2:1.1.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Fragment KTX
    implementation("androidx.fragment:fragment-ktx:1.8.3")

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room (온디바이스 SQLite)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // SQLCipher — Room DB 암호화 (AES-256)
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // Play Integrity API — 앱 무결성 검증 (5.3.4)
    implementation("com.google.android.play:integrity:1.3.0")

    // WorkManager (ProactiveWorker)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.hilt:hilt-work:1.2.0")
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // EncryptedSharedPreferences (API Key 암호화)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Android Auto
    implementation("androidx.car.app:app:1.4.0")

    // MediaSession — Bluetooth headset button support
    implementation("androidx.media:media:1.7.0")

    // Sentry — 옵트인 크래시 리포팅 (SentryInitializer가 동의+DSN 게이팅)
    implementation(platform(libs.sentry.bom))
    implementation(libs.sentry.android)
}
