package com.example.ez_capstone.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.db.dao.PreferenceDao
import com.example.ez_capstone.db.entity.PreferenceEntity
import com.example.ez_capstone.governance.AgentCapability
import com.example.ez_capstone.governance.PermissionLevel
import com.example.ez_capstone.governance.PermissionManager
import com.example.ez_capstone.memory.MemoryGovernor
import com.example.ez_capstone.monitoring.SentryInitializer
import com.example.ez_capstone.profile.MultiProfileManager
import com.example.ez_capstone.profile.ProfileType
import com.example.ez_capstone.server.AuthApi
import com.example.ez_capstone.server.HealthApi
import com.example.ez_capstone.server.TokenManager
import com.example.ez_capstone.voice.VoiceStateCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthApi: HealthApi,
    private val authApi: AuthApi,
    private val coordinator: VoiceStateCoordinator,
    private val apiKeyProvider: ApiKeyProvider,
    private val spotifyAuth: com.example.ez_capstone.config.SpotifyAuthManager,
    private val permissionManager: PermissionManager,
    private val memoryGovernor: MemoryGovernor,
    val multiProfileManager: MultiProfileManager,
    private val preferenceDao: PreferenceDao
) : ViewModel() {

    companion object {
        private const val PREFS_NAME = "ezmap_settings"
        private const val KEY_TTS_SPEED = "tts_speed"
        private const val KEY_WAKE_SENSITIVITY = "wake_sensitivity"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_LIVE_API_MODE = "live_api_mode"
        const val KEY_HIGH_CONTRAST = "high_contrast"
        const val KEY_VOICE_ONLY = "voice_only_mode"
    }

    data class SettingsUiState(
        val ttsSpeed: Float = 1.0f,
        val wakeSensitivity: Float = 0.5f,
        val ttsEnabled: Boolean = true,
        val profileName: String = "",
        val appVersion: String = "1.0",
        val serverStatus: ServerStatus = ServerStatus.CHECKING,
        val isTrialMode: Boolean = false,
        val geminiKeySet: Boolean = false,
        val kakaoKeySet: Boolean = false,
        val dataGoKrKeySet: Boolean = false,
        val opinetKeySet: Boolean = false,
        val naverKeySet: Boolean = false,
        val odsayKeySet: Boolean = false,
        val spotifyClientIdSet: Boolean = false,
        val spotifyConnected: Boolean = false,
        val activeProfileType: ProfileType = ProfileType.OWNER,
        val highContrastMode: Boolean = false,
        val voiceOnlyMode: Boolean = false,
        val liveApiMode: Boolean = false,
        val naviOptions: Map<String, Boolean> = mapOf(
            "avoid_narrow" to false,
            "prefer_wide" to false,
            "minimize_left_turn" to false
        ),
        val isDemoMode: Boolean = false,
        val crashReportingEnabled: Boolean = false
    )

    enum class ServerStatus { ONLINE, OFFLINE, CHECKING }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            ttsSpeed = prefs.getFloat(KEY_TTS_SPEED, 1.0f),
            wakeSensitivity = prefs.getFloat(KEY_WAKE_SENSITIVITY, 0.5f),
            ttsEnabled = prefs.getBoolean(KEY_TTS_ENABLED, true),
            isTrialMode = apiKeyProvider.isTrialMode,
            geminiKeySet = apiKeyProvider.geminiKey.isNotBlank(),
            kakaoKeySet = apiKeyProvider.kakaoRestKey.isNotBlank(),
            dataGoKrKeySet = apiKeyProvider.dataGoKrKey.isNotBlank(),
            opinetKeySet = apiKeyProvider.opinetKey.isNotBlank(),
            naverKeySet = apiKeyProvider.naverClientId.isNotBlank() && apiKeyProvider.naverClientSecret.isNotBlank(),
            odsayKeySet = apiKeyProvider.odsayKey.isNotBlank(),
            spotifyClientIdSet = apiKeyProvider.spotifyClientId.isNotBlank(),
            spotifyConnected = spotifyAuth.isConnected,
            activeProfileType = multiProfileManager.getActiveProfile().type,
            highContrastMode = prefs.getBoolean(KEY_HIGH_CONTRAST, false),
            voiceOnlyMode = prefs.getBoolean(KEY_VOICE_ONLY, false),
            liveApiMode = prefs.getBoolean(KEY_LIVE_API_MODE, false),
            isDemoMode = apiKeyProvider.isDemoMode,
            crashReportingEnabled = SentryInitializer.isConsentGranted(context)
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _logoutEvent = MutableStateFlow(false)
    val logoutEvent: StateFlow<Boolean> = _logoutEvent.asStateFlow()

    private val _resetApiKeysEvent = MutableStateFlow(false)
    val resetApiKeysEvent: StateFlow<Boolean> = _resetApiKeysEvent.asStateFlow()

    init {
        // coordinator에 저장된 설정 동기화
        coordinator.ttsSpeed = prefs.getFloat(KEY_TTS_SPEED, 1.0f)
        coordinator.ttsEnabled = prefs.getBoolean(KEY_TTS_ENABLED, true)
        coordinator.useLiveApi = prefs.getBoolean(KEY_LIVE_API_MODE, false)
        // 앱 버전 동적 로드
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            _uiState.update { it.copy(appVersion = packageInfo.versionName ?: "1.0") }
        } catch (_: Exception) {}
        loadProfile()
        checkServerHealth()
        viewModelScope.launch { loadNaviOptions() }
    }

    private suspend fun loadNaviOptions() {
        val prefs = preferenceDao.getByCategory("navi_option")
        val map = mutableMapOf("avoid_narrow" to false, "prefer_wide" to false, "minimize_left_turn" to false)
        prefs.forEach { map[it.key] = it.value > 0.5f }
        _uiState.update { it.copy(naviOptions = map) }
    }

    fun toggleNaviOption(key: String) {
        val newVal = !(_uiState.value.naviOptions[key] ?: false)
        _uiState.update { it.copy(naviOptions = it.naviOptions + (key to newVal)) }
        viewModelScope.launch {
            preferenceDao.upsert(PreferenceEntity("navi_option", key, if (newVal) 1f else 0f))
        }
    }

    fun toggleDemoMode() {
        val next = !apiKeyProvider.isDemoMode
        apiKeyProvider.isDemoMode = next
        _uiState.update { it.copy(isDemoMode = next) }
    }

    fun setTtsSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_TTS_SPEED, speed).apply()
        coordinator.ttsSpeed = speed
        _uiState.update { it.copy(ttsSpeed = speed) }
    }

    fun setWakeSensitivity(sensitivity: Float) {
        prefs.edit().putFloat(KEY_WAKE_SENSITIVITY, sensitivity).apply()
        _uiState.update { it.copy(wakeSensitivity = sensitivity) }
    }

    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TTS_ENABLED, enabled).apply()
        coordinator.ttsEnabled = enabled
        _uiState.update { it.copy(ttsEnabled = enabled) }
    }

    fun setHighContrastMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_CONTRAST, enabled).apply()
        _uiState.update { it.copy(highContrastMode = enabled) }
    }

    fun setVoiceOnlyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_ONLY, enabled).apply()
        _uiState.update { it.copy(voiceOnlyMode = enabled) }
    }

    fun setLiveApiMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_API_MODE, enabled).apply()
        coordinator.useLiveApi = enabled
        _uiState.update { it.copy(liveApiMode = enabled) }
    }

    fun setCrashReporting(enabled: Boolean) {
        SentryInitializer.setConsent(context, enabled)
        if (enabled) SentryInitializer.init(context) else SentryInitializer.disable()
        _uiState.update { it.copy(crashReportingEnabled = enabled) }
    }

    fun checkServerHealth() {
        _uiState.update { it.copy(serverStatus = ServerStatus.CHECKING) }
        viewModelScope.launch {
            try {
                healthApi.check()
                _uiState.update { it.copy(serverStatus = ServerStatus.ONLINE) }
            } catch (e: Exception) {
                _uiState.update { it.copy(serverStatus = ServerStatus.OFFLINE) }
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            try {
                val user = authApi.getMe()
                _uiState.update { it.copy(profileName = user.nickname ?: "") }
            } catch (_: Exception) {
                // 비로그인 상태 또는 네트워크 에러
            }
        }
    }

    fun logout() {
        TokenManager.clearToken()
        _logoutEvent.value = true
    }

    // ── 개별 API 키 설정 ──

    enum class ApiKeyType { GEMINI, KAKAO, DATA_GO_KR, OPINET, NAVER_ID, NAVER_SECRET, ODSAY, SPOTIFY_CLIENT_ID }

    /** 단일 API 키만 저장. 온보딩 전체를 다시 타지 않고 키별로 추가/수정. */
    fun setApiKey(type: ApiKeyType, value: String) {
        val v = value.trim()
        when (type) {
            ApiKeyType.GEMINI -> apiKeyProvider.geminiKey = v
            ApiKeyType.KAKAO -> apiKeyProvider.kakaoRestKey = v
            ApiKeyType.DATA_GO_KR -> apiKeyProvider.dataGoKrKey = v
            ApiKeyType.OPINET -> apiKeyProvider.opinetKey = v
            ApiKeyType.NAVER_ID -> apiKeyProvider.naverClientId = v
            ApiKeyType.NAVER_SECRET -> apiKeyProvider.naverClientSecret = v
            ApiKeyType.ODSAY -> apiKeyProvider.odsayKey = v
            ApiKeyType.SPOTIFY_CLIENT_ID -> apiKeyProvider.spotifyClientId = v
        }
        // 개인 키를 하나라도 직접 등록하면 체험 모드 해제
        if (v.isNotBlank() && apiKeyProvider.isTrialMode) apiKeyProvider.isTrialMode = false
        _uiState.update {
            it.copy(
                isTrialMode = apiKeyProvider.isTrialMode,
                geminiKeySet = apiKeyProvider.geminiKey.isNotBlank(),
                kakaoKeySet = apiKeyProvider.kakaoRestKey.isNotBlank(),
                dataGoKrKeySet = apiKeyProvider.dataGoKrKey.isNotBlank(),
                opinetKeySet = apiKeyProvider.opinetKey.isNotBlank(),
                naverKeySet = apiKeyProvider.naverClientId.isNotBlank() && apiKeyProvider.naverClientSecret.isNotBlank(),
                odsayKeySet = apiKeyProvider.odsayKey.isNotBlank(),
                spotifyClientIdSet = apiKeyProvider.spotifyClientId.isNotBlank()
            )
        }
    }

    // ── Spotify 연동 ──

    /** 인증 URL 생성. clientId 미설정이면 null. 화면이 이 URL을 브라우저/Custom Tab으로 연다. */
    fun buildSpotifyAuthUrl(): String? = spotifyAuth.buildAuthUrl()

    fun disconnectSpotify() {
        spotifyAuth.disconnect()
        _uiState.update { it.copy(spotifyConnected = false) }
    }

    /** 화면 복귀 시 연결 상태 새로고침 (Custom Tab 인증 후). */
    fun refreshSpotifyState() {
        _uiState.update {
            it.copy(
                spotifyClientIdSet = apiKeyProvider.spotifyClientId.isNotBlank(),
                spotifyConnected = spotifyAuth.isConnected
            )
        }
    }

    /** 모든 API 키를 초기화하고 온보딩으로 복귀 */
    fun resetApiKeys() {
        apiKeyProvider.geminiKey = ""
        apiKeyProvider.kakaoRestKey = ""
        apiKeyProvider.weatherKey = ""
        apiKeyProvider.dataGoKrKey = ""
        apiKeyProvider.opinetKey = ""
        apiKeyProvider.naverClientId = ""
        apiKeyProvider.naverClientSecret = ""
        apiKeyProvider.odsayKey = ""
        apiKeyProvider.isTrialMode = false
        apiKeyProvider.onboardingCompleted = false
        _uiState.update {
            it.copy(
                isTrialMode = false,
                geminiKeySet = false,
                kakaoKeySet = false
            )
        }
        _resetApiKeysEvent.value = true

        // 메모리도 정리 (기억 초기화)
        viewModelScope.launch {
            memoryGovernor.purgeAll()
        }
    }

    // ── 멀티 프로필 ──

    fun setActiveProfile(type: ProfileType) {
        multiProfileManager.setProfileType(type)
        _uiState.update { it.copy(activeProfileType = type) }
    }

    // ── 에이전트 권한 (PermissionManager) ──

    fun getPermissionLevel(capability: AgentCapability): PermissionLevel =
        permissionManager.getLevel(capability)

    fun setPermissionLevel(capability: AgentCapability, level: PermissionLevel) {
        permissionManager.setLevel(capability, level)
    }

    fun getAllPermissions(): Map<AgentCapability, PermissionLevel> =
        permissionManager.getAllLevels()

    // ── 메모리 관리 ──

    fun purgeAllMemory() {
        viewModelScope.launch {
            memoryGovernor.purgeAll()
        }
    }
}
