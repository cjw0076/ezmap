package com.example.ez_capstone.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.ez_capstone.BuildConfig
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Calendar

/**
 * API 키 암호화 저장소.
 * EncryptedSharedPreferences 기반 — 사용자의 API Key를 기기에 안전하게 저장.
 * 체험 모드: 공용 키로 일 30회 제한 사용 가능.
 */
class ApiKeyProvider(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "ezmap_api_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("ApiKeyProvider", "EncryptedSharedPreferences 초기화 실패, 평문 폴백 사용", e)
        context.getSharedPreferences("ezmap_api_keys_fallback", Context.MODE_PRIVATE)
    }

    // ── 필수 키 ──

    var geminiKey: String
        get() = prefs.getString("gemini_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_key", value).apply()

    var kakaoRestKey: String
        get() = prefs.getString("kakao_rest_key", "") ?: ""
        set(value) = prefs.edit().putString("kakao_rest_key", value).apply()

    // ── 선택 키 (사용자 입력 우선, 없으면 BuildConfig fallback) ──

    var weatherKey: String
        get() = prefs.getString("weather_key", "").takeUnless { it.isNullOrBlank() }
            ?: BuildConfig.KMA_API_KEY
        set(value) = prefs.edit().putString("weather_key", value).apply()

    var dataGoKrKey: String
        get() = prefs.getString("data_go_kr_key", "").takeUnless { it.isNullOrBlank() }
            ?: BuildConfig.KMA_API_KEY  // 공공데이터포털 키 = 기상청 키 동일
        set(value) = prefs.edit().putString("data_go_kr_key", value).apply()

    var opinetKey: String
        get() = prefs.getString("opinet_key", "").takeUnless { it.isNullOrBlank() }
            ?: BuildConfig.OPINET_API_KEY
        set(value) = prefs.edit().putString("opinet_key", value).apply()

    var naverClientId: String
        get() = prefs.getString("naver_client_id", "").takeUnless { it.isNullOrBlank() }
            ?: BuildConfig.NAVER_CLIENT_ID
        set(value) = prefs.edit().putString("naver_client_id", value).apply()

    var naverClientSecret: String
        get() = prefs.getString("naver_client_secret", "").takeUnless { it.isNullOrBlank() }
            ?: BuildConfig.NAVER_CLIENT_SECRET
        set(value) = prefs.edit().putString("naver_client_secret", value).apply()

    /** 네이버 맵 전용 키 (Directions API용과 별도) */
    var naverMapClientId: String
        get() = prefs.getString("naver_map_client_id", "").takeUnless { it.isNullOrBlank() }
            ?: BuildConfig.NAVER_MAP_CLIENT_ID
        set(value) = prefs.edit().putString("naver_map_client_id", value).apply()

    var naverMapClientSecret: String
        get() = prefs.getString("naver_map_client_secret", "").takeUnless { it.isNullOrBlank() }
            ?: BuildConfig.NAVER_MAP_CLIENT_SECRET
        set(value) = prefs.edit().putString("naver_map_client_secret", value).apply()

    var odsayKey: String
        get() = prefs.getString("odsay_key", "").takeUnless { it.isNullOrBlank() }
            ?: BuildConfig.ODSAY_API_KEY
        set(value) = prefs.edit().putString("odsay_key", value).apply()

    /** Spotify Client ID (OAuth PKCE — secret 불필요). 사용자 입력 우선, 없으면 BuildConfig. */
    var spotifyClientId: String
        get() = prefs.getString("spotify_client_id", "").takeUnless { it.isNullOrBlank() }
            ?: BuildConfig.SPOTIFY_CLIENT_ID
        set(value) = prefs.edit().putString("spotify_client_id", value).apply()

    // ── 설정 상태 ──

    /** gemini + kakao 둘 다 있으면 true */
    val isConfigured: Boolean
        get() = geminiKey.isNotBlank() && kakaoRestKey.isNotBlank()

    /** 공공데이터 팩 설정 여부 (data.go.kr 키) — 실효 키(BuildConfig 폴백 포함) 기준.
     *  raw pref만 보면 keys.properties/BuildConfig에 키가 있어도 "미설정"으로 판정돼
     *  get_weather_kma 등 공공데이터 도구가 통째로 제외되는 버그가 있었음. */
    val isPublicDataConfigured: Boolean
        get() = dataGoKrKey.isNotBlank()

    /** 프리미엄 팩 설정 여부 (네이버 + ODsay) — 실효 키(BuildConfig 폴백 포함) 기준. */
    val isPremiumConfigured: Boolean
        get() = naverClientId.isNotBlank() || odsayKey.isNotBlank()

    /** 팩별 활성화된 기능 목록 */
    fun getEnabledFeatures(): List<String> {
        val features = mutableListOf<String>()
        if (activeGeminiKey.isNotBlank()) features.add("AI 대화")
        if (activeKakaoKey.isNotBlank()) features.addAll(listOf("장소 검색", "경로 탐색"))
        if (isPublicDataConfigured) features.addAll(listOf("날씨", "미세먼지", "충전소", "주차장", "약국", "병원"))
        if (opinetKey.isNotBlank()) features.add("주유소 가격")
        if (naverClientId.isNotBlank()) features.add("네이버 경로")
        if (odsayKey.isNotBlank()) features.add("대중교통")
        return features
    }

    // ── 체험 모드 ──

    var isTrialMode: Boolean
        get() = prefs.getBoolean("trial_mode", false)
        set(value) = prefs.edit().putBoolean("trial_mode", value).apply()

    /** 오늘 사용 횟수 (자정 리셋) */
    val trialUsageCount: Int
        get() = synchronized(prefs) {
            val lastDate = prefs.getString("trial_date", "") ?: ""
            val today = todayString()
            if (lastDate != today) {
                // 날짜가 바뀌면 카운트 리셋
                prefs.edit()
                    .putInt("trial_usage", 0)
                    .putString("trial_date", today)
                    .commit()
                return@synchronized 0
            }
            prefs.getInt("trial_usage", 0)
        }

    fun incrementTrialUsage(): Int = synchronized(prefs) {
        val today = todayString()
        val storedDay = prefs.getString("trial_date", "") ?: ""
        val current = if (storedDay == today) prefs.getInt("trial_usage", 0) else 0
        val next = current + 1
        prefs.edit()
            .putString("trial_date", today)
            .putInt("trial_usage", next)
            .commit() // apply() 대신 commit() — synchronized 블록 내 동기 write
        next
    }

    fun isTrialLimitReached(): Boolean = trialUsageCount >= TRIAL_DAILY_LIMIT

    // ── 실제 사용할 키 ──

    /** 체험 모드이면 공용 키, 아니면 사용자 키 */
    val activeGeminiKey: String
        get() = if (isTrialMode && geminiKey.isBlank()) TRIAL_GEMINI_KEY else geminiKey

    val activeKakaoKey: String
        get() = if (isTrialMode && kakaoRestKey.isBlank()) TRIAL_KAKAO_KEY else kakaoRestKey

    /** 키가 어떤 식으로든 사용 가능한 상태인지 */
    val isReady: Boolean
        get() = isConfigured || (isTrialMode && !isTrialLimitReached())

    // ── 온보딩 완료 여부 ──

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean("onboarding_completed", false)
        set(value) = prefs.edit().putBoolean("onboarding_completed", value).apply()

    // ── 데모 모드 (쇼케이스/발표용) ──
    // true 시 GeminiAgentEngine이 실제 API 대신 DemoResponseRepository 응답을 반환.
    var isDemoMode: Boolean
        get() = prefs.getBoolean("demo_mode", false)
        set(value) = prefs.edit().putBoolean("demo_mode", value).apply()

    private fun todayString(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    companion object {
        const val TRIAL_DAILY_LIMIT = 30

        // 체험용 공용 키 — local.properties → BuildConfig로 주입
        val TRIAL_GEMINI_KEY: String = BuildConfig.TRIAL_GEMINI_KEY
        val TRIAL_KAKAO_KEY: String = BuildConfig.TRIAL_KAKAO_KEY
    }
}
