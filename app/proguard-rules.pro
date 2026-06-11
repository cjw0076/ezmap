# EZmap ProGuard Rules for R8 Minification
# Comprehensive protection for on-device AI agent, navigation, and voice pipeline

# ============================================================================
# GENERAL SETTINGS
# ============================================================================

-verbose
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Prevent R8 from shrinking unmatched stack traces
-keep public class * extends java.lang.Exception

# ============================================================================
# AGENT ENGINE & GEMINI API
# ============================================================================

# Preserve agent models and engine classes
-keep class com.example.ez_capstone.agent.** { *; }
-keep class com.example.ez_capstone.models.** { *; }

# Preserve Gemini API response models (used in JSON deserialization)
-keepclassmembers class com.example.ez_capstone.agent.GeminiApiClient {
    public *** generateContent(...);
}

# ============================================================================
# ROOM DATABASE (Entity, DAO, Database)
# ============================================================================

# Preserve all Room entities
-keep class com.example.ez_capstone.db.** { *; }

# Prevent renaming of Room column names (used via reflection)
-keepclasseswithmembernames class * {
    @androidx.room.ColumnInfo <fields>;
}

-keepclasseswithmembernames class * {
    @androidx.room.PrimaryKey <fields>;
}

# Preserve Room generated code
-keep class *.** extends androidx.room.RoomDatabase { *; }

# ============================================================================
# GSON & JSON SERIALIZATION
# ============================================================================

# Preserve model classes used in Gson deserialization
-keep class com.example.ez_capstone.models.** { *; }
-keep class com.example.ez_capstone.agent.** { *; }

# Prevent obfuscation of field names used in JSON
-keepclassmembers class com.example.ez_capstone.models.** {
    !static !transient <fields>;
}

-keepclassmembers class com.example.ez_capstone.agent.** {
    !static !transient <fields>;
}

# ============================================================================
# RETROFIT API INTERFACES
# ============================================================================

# Preserve all Retrofit API interfaces
-keep interface com.example.ez_capstone.api.** { *; }
-keep class com.example.ez_capstone.api.** {
    public protected *;
}

# Preserve method signatures for Retrofit reflection
-keepclassmembers interface com.example.ez_capstone.api.** {
    ** *(...);
}

# Specific API clients
-keep class com.example.ez_capstone.api.KakaoLocalApi { *; }
-keep class com.example.ez_capstone.api.KakaoMobilityApi { *; }
-keep class com.example.ez_capstone.api.NaverDirectionsApi { *; }
-keep class com.example.ez_capstone.api.OpinetApi { *; }
-keep class com.example.ez_capstone.api.KmaWeatherApi { *; }
-keep class com.example.ez_capstone.api.OdsayApi { *; }

# ============================================================================
# OKHTTP & RETROFIT
# ============================================================================

-keep class com.squareup.okhttp3.** { *; }
-keep interface com.squareup.okhttp3.** { *; }

-keep class retrofit2.** { *; }
-keep interface retrofit2.** { *; }

-keepattributes Exceptions

-keepclasseswithmembers class * {
    @retrofit2.http.GET <methods>;
}
-keepclasseswithmembers class * {
    @retrofit2.http.POST <methods>;
}
-keepclasseswithmembers class * {
    @retrofit2.http.PUT <methods>;
}
-keepclasseswithmembers class * {
    @retrofit2.http.DELETE <methods>;
}

# Preserve OkHttp interceptors and handlers
-keep class okhttp3.logging.** { *; }
-keepclassmembers class okhttp3.logging.** {
    public <methods>;
}

# ============================================================================
# KAKAO SDK (Maps + Navigation)
# ============================================================================

# Preserve Kakao Map SDK classes
-keep class com.kakao.maps.** { *; }
-keep interface com.kakao.maps.** { *; }

-keep class com.kakao.sdk.** { *; }
-keep interface com.kakao.sdk.** { *; }

# Kakao Navi SDK
-keep class com.kakao.navi.** { *; }
-keep interface com.kakao.navi.** { *; }

# Prevent Kakao from being renamed
-keepnames class * extends com.kakao.maps.MapView
-keepnames class * extends com.kakao.sdk.navi.NaviClient

# ============================================================================
# VOSK (OFFLINE STT)
# ============================================================================

-keep class org.vosk.** { *; }
-keep interface org.vosk.** { *; }
-keep class com.alphacephei.vosk.** { *; }
-keep interface com.alphacephei.vosk.** { *; }

# Preserve native method interfaces
-keepclasseswithmembernames class * {
    native <methods>;
}

# ============================================================================
# PORCUPINE (WAKE WORD DETECTION)
# ============================================================================

-keep class ai.picovoice.porcupine.** { *; }
-keep interface ai.picovoice.porcupine.** { *; }
-keep class com.picovoice.porcupine.** { *; }

# Preserve native JNI methods
-keepclasseswithmembernames class ai.picovoice.porcupine.** {
    native <methods>;
}

# ============================================================================
# HILT DEPENDENCY INJECTION
# ============================================================================

-keep class dagger.hilt.** { *; }
-keep interface dagger.hilt.** { *; }

# Preserve Hilt entry points
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep @dagger.hilt.android.WithFragmentBindings class *

# Preserve all Hilt-generated code
-keep class **.Hilt_* { *; }
-keep class **_Factory { *; }
-keep class **_Factory$* { *; }
-keep class **_MembersInjector { *; }
-keep class **_MembersInjector$* { *; }
-keep class **_Provide* { *; }
-keep class **_BindModuleProvider { *; }

# Keep module classes
-keep class * extends dagger.Module { *; }
-keep @dagger.Module class * { *; }

# ============================================================================
# KOTLIN COROUTINES
# ============================================================================

-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-keep class kotlin.coroutines.** { *; }
-keep interface kotlin.coroutines.** { *; }

-keep class kotlinx.coroutines.** { *; }
-keep interface kotlinx.coroutines.** { *; }

# Preserve lambda expressions
-keepclassmembers class ** {
    *** lambda$*(...);
}

# ============================================================================
# JETPACK COMPOSE
# ============================================================================

-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }

# Preserve Compose compiler-generated code
-keep @androidx.compose.runtime.Composable class * { *; }
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# ============================================================================
# ANDROIDX LIBRARIES
# ============================================================================

# Room
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }

# Lifecycle
-keep class androidx.lifecycle.** { *; }
-keep interface androidx.lifecycle.** { *; }

# Fragment
-keep class androidx.fragment.** { *; }
-keep interface androidx.fragment.** { *; }

# ViewModel
-keep class androidx.lifecycle.ViewModel { *; }
-keep @androidx.hilt.lifecycle.ViewModelInject class * { *; }

# Security (EncryptedSharedPreferences)
-keep class androidx.security.crypto.** { *; }
-keep interface androidx.security.crypto.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep @androidx.work.Worker class * { *; }

# ============================================================================
# API KEY & CONFIGURATION
# ============================================================================

# Preserve ApiKeyProvider class (EncryptedSharedPreferences)
-keep class com.example.ez_capstone.config.ApiKeyProvider { *; }
-keep class com.example.ez_capstone.config.** { *; }

# ============================================================================
# VIEWMODEL & NAVIGATION
# ============================================================================

# Preserve all ViewModels
-keep class com.example.ez_capstone.viewmodel.** extends androidx.lifecycle.ViewModel { *; }
-keep class com.example.ez_capstone.viewmodel.** { *; }

# Preserve Navigation
-keep class com.example.ez_capstone.navigation.** { *; }
-keep class androidx.navigation.** { *; }

# ============================================================================
# UI SCREENS & COMPONENTS
# ============================================================================

# Preserve all UI screens and components
-keep class com.example.ez_capstone.ui.** { *; }
-keep interface com.example.ez_capstone.ui.** { *; }

# Composables
-keepclassmembers class com.example.ez_capstone.ui.** {
    public <methods>;
}

# ============================================================================
# HELPERS & WORKERS
# ============================================================================

# Preserve helpers
-keep class com.example.ez_capstone.helpers.** { *; }

# Preserve WorkManager workers
-keep @androidx.hilt.work.HiltWorker class com.example.ez_capstone.worker.** { *; }
-keep class com.example.ez_capstone.worker.** extends androidx.work.Worker { *; }

# ============================================================================
# VOICE PIPELINE
# ============================================================================

# Preserve voice engine classes
-keep class com.example.ez_capstone.voice.** { *; }
-keep class com.example.ez_capstone.navi.** { *; }

# ============================================================================
# SUPPRESS WARNINGS
# ============================================================================

-dontwarn android.**
-dontwarn androidx.**
-dontwarn com.google.**
-dontwarn com.kakao.**
-dontwarn org.vosk.**
-dontwarn ai.picovoice.**
-dontwarn com.squareup.**
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn io.sentry.**

# ── Sentry ──
-keep class io.sentry.** { *; }
-dontwarn dagger.**

# ── R8 missing classes: AutoValue/JavaPoet 컴파일 전용 의존성(런타임 부재) ──
# annotation processor가 끌어오는 javax.lang.model.* 참조 — 런타임엔 필요 없음.
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
-dontwarn com.google.auto.value.**

# ── SQLCipher (필수) ──
# 네이티브 JNI가 net.sqlcipher.database.SQLiteDatabase의 'mNativeHandle' 필드를
# 이름으로 조회한다. 난독화하면 NoSuchFieldError로 DB 생성 시 즉시 크래시.
-keep class net.sqlcipher.** { *; }
-keep interface net.sqlcipher.** { *; }
-keepclassmembers class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

# ── JNA (Vosk 음성엔진의 네이티브 브리지, 필수) ──
# JNA 네이티브가 com.sun.jna.Pointer.peer 등 필드를 JNI로 조회 → 난독화하면
# UnsatisfiedLinkError("Can't obtain peer field ID")로 Vosk 초기화 시 크래시.
-keep class com.sun.jna.** { *; }
-keepclassmembers class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
-dontwarn com.sun.jna.**
-dontwarn java.awt.**