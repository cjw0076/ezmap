package com.example.ez_capstone.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.db.dao.ConversationDao
import com.example.ez_capstone.db.dao.FrequentPlaceDao
import com.example.ez_capstone.db.dao.PreferenceDao
import com.example.ez_capstone.db.dao.ProfileDao
import com.example.ez_capstone.db.dao.RouteHistoryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import com.example.ez_capstone.db.entity.ProfileEntity
import com.example.ez_capstone.config.ApiKeyProvider
import com.example.ez_capstone.memory.MemoryGovernor
import com.example.ez_capstone.skill.CustomRoutineDao
import com.example.ez_capstone.skill.CustomRoutineEntity
import com.example.ez_capstone.skill.SkillHealth
import com.example.ez_capstone.skill.SkillLifecycleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val preferenceDao: PreferenceDao,
    private val routeHistoryDao: RouteHistoryDao,
    private val frequentPlaceDao: FrequentPlaceDao,
    private val conversationDao: ConversationDao,
    private val apiKeyProvider: ApiKeyProvider,
    private val skillLifecycleManager: SkillLifecycleManager,
    private val memoryGovernor: MemoryGovernor,
    private val customRoutineDao: CustomRoutineDao,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    companion object {
        private const val TAG = "ProfileViewModel"
    }

    data class PreferenceBar(
        val label: String,
        val value: Float
    )

    data class FrequentRoute(
        val originName: String,
        val destName: String,
        val count: Int
    )

    data class ServiceStatus(
        val geminiConnected: Boolean = false,
        val kakaoConnected: Boolean = false,
        val publicDataConnected: Boolean = false,
        val opinetConnected: Boolean = false,
        val naverConnected: Boolean = false,
        val odsayConnected: Boolean = false
    )

    data class ProfileUiState(
        val isLoading: Boolean = false,
        val totalRoutes: Int = 0,
        val totalDistance: Int = 0,
        val voiceUsageRate: Float = 0f,
        val preferences: List<PreferenceBar> = emptyList(),
        val frequentRoutes: List<FrequentRoute> = emptyList(),
        val serviceStatus: ServiceStatus = ServiceStatus()
    )

    private val _uiState = MutableStateFlow(ProfileUiState(isLoading = true))
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _editProfile = MutableStateFlow<ProfileEntity?>(null)
    val editProfile: StateFlow<ProfileEntity?> = _editProfile.asStateFlow()

    // Phase 4.5: 매니저 데이터 노출
    private val _skillHealthList = MutableStateFlow<List<SkillHealth>>(emptyList())
    val skillHealthList: StateFlow<List<SkillHealth>> = _skillHealthList.asStateFlow()

    private val _memoryStats = MutableStateFlow(MemoryGovernor.MemoryStats())
    val memoryStats: StateFlow<MemoryGovernor.MemoryStats> = _memoryStats.asStateFlow()

    private val _routines = MutableStateFlow<List<CustomRoutineEntity>>(emptyList())
    val routines: StateFlow<List<CustomRoutineEntity>> = _routines.asStateFlow()

    init {
        loadProfile()
        loadManagerData()
    }

    private fun loadManagerData() {
        viewModelScope.launch {
            try {
                _skillHealthList.value = skillLifecycleManager.getAllHealth()
                _memoryStats.value = memoryGovernor.getStats()
                _routines.value = customRoutineDao.getAll()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load manager data", e)
            }
        }
    }

    fun toggleRoutine(id: Long, active: Boolean) {
        viewModelScope.launch {
            customRoutineDao.setActive(id, active)
            _routines.value = customRoutineDao.getAll()
        }
    }

    fun loadProfile() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            try {
                // Load profile for edit form
                val profile = profileDao.getProfile() ?: run {
                    // 첫 실행 시 데모용 기본 프로필 주입 — AI가 맥락을 잡을 수 있도록
                    val demo = ProfileEntity(
                        homeAddress = "서울특별시 강남구 강남대로 396 (역삼동, 강남역)",
                        homeLat = 37.4979, homeLng = 127.0276,
                        workAddress = "서울특별시 중구 세종대로 110 (태평로1가, 서울시청)",
                        workLat = 37.5663, workLng = 126.9779,
                        vehicleType = "sedan", fuelType = "gasoline",
                        ttsStyle = "polite"
                    )
                    profileDao.upsertProfile(demo)
                    demo
                }
                _editProfile.value = profile

                // Route statistics from Room
                val routes = routeHistoryDao.getRecent(1000)
                val totalRoutes = routes.size
                val totalDistance = routes.sumOf { it.distanceM }

                // Preferences from Room
                val prefs = preferenceDao.getAll()

                // Frequent places from Room (used as frequent routes)
                val topPlaces = frequentPlaceDao.getTopPlaces(5)

                val services = ServiceStatus(
                    geminiConnected = apiKeyProvider.activeGeminiKey.isNotBlank(),
                    kakaoConnected = apiKeyProvider.activeKakaoKey.isNotBlank(),
                    publicDataConnected = apiKeyProvider.isPublicDataConfigured,
                    opinetConnected = apiKeyProvider.opinetKey.isNotBlank(),
                    naverConnected = apiKeyProvider.naverClientId.isNotBlank(),
                    odsayConnected = apiKeyProvider.odsayKey.isNotBlank()
                )

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalRoutes = totalRoutes,
                        totalDistance = totalDistance,
                        voiceUsageRate = run {
                            val voiceCount = appContext
                                .getSharedPreferences("ez_stats", Context.MODE_PRIVATE)
                                .getInt("voice_count", 0)
                            val totalMessages = conversationDao.getTotalUserMessageCount()
                            if (totalMessages > 0) voiceCount.toFloat() / totalMessages else 0f
                        },
                        preferences = prefs.map { p ->
                            PreferenceBar(label = p.key, value = p.value)
                        },
                        frequentRoutes = topPlaces.map { place ->
                            FrequentRoute(
                                originName = "",
                                destName = place.name,
                                count = place.visitCount
                            )
                        },
                        serviceStatus = services
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load profile from Room DB", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun saveProfile(profile: ProfileEntity) {
        viewModelScope.launch {
            try {
                profileDao.upsertProfile(profile)
                _editProfile.value = profile
                Log.d(TAG, "Profile saved successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save profile", e)
            }
        }
    }
}
