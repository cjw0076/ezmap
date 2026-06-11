package com.example.ez_capstone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.db.dao.AgentNoteDao
import com.example.ez_capstone.db.dao.ConversationDao
import com.example.ez_capstone.db.dao.ProfileDao
import com.example.ez_capstone.db.dao.PreferenceDao
import com.example.ez_capstone.db.dao.RouteHistoryDao
import com.example.ez_capstone.skill.LearnedSkillDao
import com.example.ez_capstone.trace.DecisionTraceDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DbTable(val name: String, val rows: List<Map<String, String>>)

@HiltViewModel
class DbViewerViewModel @Inject constructor(
    private val profileDao: ProfileDao,
    private val preferenceDao: PreferenceDao,
    private val routeHistoryDao: RouteHistoryDao,
    private val learnedSkillDao: LearnedSkillDao,
    private val decisionTraceDao: DecisionTraceDao,
    private val agentNoteDao: AgentNoteDao,
    private val conversationDao: ConversationDao,
) : ViewModel() {

    private val _tables = MutableStateFlow<List<DbTable>>(emptyList())
    val tables: StateFlow<List<DbTable>> = _tables

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            val result = mutableListOf<DbTable>()

            // Profile
            profileDao.getProfile()?.let { p ->
                result += DbTable("profile", listOf(mapOf(
                    "homeAddress" to (p.homeAddress ?: "-"),
                    "workAddress" to (p.workAddress ?: "-"),
                    "vehicleType" to (p.vehicleType ?: "-"),
                    "fuelType" to (p.fuelType ?: "-"),
                    "hasHipass" to p.hasHipass.toString(),
                    "ttsStyle" to (p.ttsStyle ?: "-"),
                )))
            }

            // Preferences (AI learned)
            val prefs = preferenceDao.getAll()
            if (prefs.isNotEmpty()) {
                result += DbTable("preferences (AI학습, ${prefs.size}건)", prefs.map { mapOf(
                    "category" to it.category,
                    "key" to it.key,
                    "value" to it.value.toString(),
                )})
            }

            // Route history (최근 10)
            val routes = routeHistoryDao.getRecent(10)
            if (routes.isNotEmpty()) {
                result += DbTable("route_history (최근${routes.size}건)", routes.map { mapOf(
                    "origin" to (it.originName ?: "-"),
                    "dest" to (it.destName ?: "-"),
                    "distM" to it.distanceM.toString(),
                    "durS" to it.durationS.toString(),
                    "dow" to it.dayOfWeek.toString(),
                )})
            }

            // Learned skills
            val skills = learnedSkillDao.getAll()
            if (skills.isNotEmpty()) {
                result += DbTable("learned_skills (${skills.size}건)", skills.map { mapOf(
                    "id" to it.id.take(8),
                    "utterance" to it.canonicalUtterance.take(28),
                    "conf" to String.format("%.2f", it.confidence),
                    "usage" to it.usageCount.toString(),
                    "status" to it.status,
                )})
            }

            // Decision traces (최근 10)
            val traces = decisionTraceDao.getRecent(10)
            if (traces.isNotEmpty()) {
                result += DbTable("decision_trace (최근${traces.size}건)", traces.map { mapOf(
                    "request" to it.requestText.take(28),
                    "tools" to it.toolsUsed.take(28),
                    "ms" to it.totalDurationMs.toString(),
                    "iter" to it.iterationCount.toString(),
                )})
            }

            // Agent notes (최근 15)
            val notes = agentNoteDao.getRecent(15)
            if (notes.isNotEmpty()) {
                result += DbTable("agent_notes (최근${notes.size}건)", notes.map { mapOf(
                    "category" to it.category,
                    "content" to it.content.take(40),
                    "conf" to String.format("%.2f", it.confidence),
                    "uses" to it.useCount.toString(),
                )})
            }

            // Conversations (최근 메시지 20)
            val convs = conversationDao.getRecentMessages(20)
            if (convs.isNotEmpty()) {
                result += DbTable("conversations (최근${convs.size}건)", convs.map { mapOf(
                    "session" to it.sessionId.take(10),
                    "role" to it.role,
                    "text" to it.text.take(36),
                )})
            }

            _tables.value = result
            _loading.value = false
        }
    }
}
