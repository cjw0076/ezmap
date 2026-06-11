package com.example.ez_capstone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.db.dao.ConversationDao
import com.example.ez_capstone.db.dao.DrivingScoreDao
import com.example.ez_capstone.db.dao.FeedbackDao
import com.example.ez_capstone.db.dao.FrequentPlaceDao
import com.example.ez_capstone.db.dao.PreferenceDao
import com.example.ez_capstone.db.dao.ProfileDao
import com.example.ez_capstone.db.dao.RouteHistoryDao
import com.example.ez_capstone.db.entity.ConversationEntity
import com.example.ez_capstone.db.entity.DrivingScoreEntity
import com.example.ez_capstone.db.entity.FeedbackEntity
import com.example.ez_capstone.db.entity.FrequentPlaceEntity
import com.example.ez_capstone.db.entity.PreferenceEntity
import com.example.ez_capstone.db.entity.ProfileEntity
import com.example.ez_capstone.db.entity.RouteHistoryEntity
import com.example.ez_capstone.skill.LearnedSkillDao
import com.example.ez_capstone.skill.LearnedSkillEntity
import com.example.ez_capstone.eval.AgentFailureAnalyzer
import com.example.ez_capstone.eval.FailureFinding
import com.example.ez_capstone.trace.DecisionTraceDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AgentMemoryUiState(
    val profile: ProfileEntity? = null,
    val frequentPlaces: List<FrequentPlaceEntity> = emptyList(),
    val recentRoutes: List<RouteHistoryEntity> = emptyList(),
    val recentMessages: List<ConversationEntity> = emptyList(),
    val preferences: List<PreferenceEntity> = emptyList(),
    val recentScores: List<DrivingScoreEntity> = emptyList(),
    val recentFeedback: List<FeedbackEntity> = emptyList(),
    val learnedSkills: List<LearnedSkillEntity> = emptyList(),
    val selfDiagnostics: List<FailureFinding> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AgentMemoryViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val frequentPlaceDao: FrequentPlaceDao,
    private val routeHistoryDao: RouteHistoryDao,
    private val conversationDao: ConversationDao,
    private val preferenceDao: PreferenceDao,
    private val drivingScoreDao: DrivingScoreDao,
    private val feedbackDao: FeedbackDao,
    private val learnedSkillDao: LearnedSkillDao,
    private val decisionTraceDao: DecisionTraceDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgentMemoryUiState())
    val uiState: StateFlow<AgentMemoryUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            _uiState.value = AgentMemoryUiState(
                profile = profileDao.getProfile(),
                frequentPlaces = frequentPlaceDao.getTopPlaces(5),
                recentRoutes = routeHistoryDao.getRecent(5),
                recentMessages = conversationDao.getRecentMessages(10),
                preferences = preferenceDao.getAll(),
                recentScores = drivingScoreDao.getRecent(3),
                recentFeedback = feedbackDao.getRecent(5),
                learnedSkills = learnedSkillDao.getAll(),
                // 자가진단: 최근 결정 기록에서 반복 실패 패턴(원인 triage) 추출 — 읽기 전용
                selfDiagnostics = AgentFailureAnalyzer.analyze(decisionTraceDao.getRecent(100)).findings,
                isLoading = false
            )
        }
    }

    /**
     * Skill 삭제 (결정 3: 삭제 허용).
     * 비전 원칙 1: 터치 1회로 즉시 삭제, 확인 다이얼로그 없음 (재학습 가능하므로).
     */
    fun deleteLearnedSkill(id: String) {
        viewModelScope.launch {
            learnedSkillDao.delete(id)
            load()
        }
    }

    /**
     * Skill 고정/해제 (결정 3: Pin).
     * Pin된 Skill은 TTL 만료되지 않고 confidence 임계 완화 혜택.
     */
    fun toggleLearnedSkillPin(id: String) {
        viewModelScope.launch {
            val current = learnedSkillDao.getById(id) ?: return@launch
            val now = System.currentTimeMillis()
            val newPin = if (current.pinnedUntil == null) Long.MAX_VALUE else null
            learnedSkillDao.updatePin(id, newPin, now)
            load()
        }
    }
}
