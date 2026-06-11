package com.example.ez_capstone.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.server.models.ScheduleCreateRequest
import com.example.ez_capstone.server.models.ScheduleItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ScheduleState {
    data object Loading : ScheduleState()
    data class Success(val schedules: List<ScheduleItem>) : ScheduleState()
    data class Error(val message: String) : ScheduleState()
}

class ScheduleViewModel : ViewModel() {

    private val _state = MutableStateFlow<ScheduleState>(ScheduleState.Loading)
    val state: StateFlow<ScheduleState> = _state

    fun loadSchedules() {
        _state.value = ScheduleState.Loading
        viewModelScope.launch {
            try {
                val response = ApiClient.scheduleApi.getSchedules()
                _state.value = ScheduleState.Success(response.schedules)
            } catch (e: Exception) {
                _state.value = ScheduleState.Error(e.message ?: "일정을 불러올 수 없습니다")
            }
        }
    }

    /**
     * @param title 일정 제목
     * @param cronExpression cron 표현식 (예: "0 8 * * 1-5" = 평일 8시)
     */
    fun createSchedule(title: String, cronExpression: String) {
        viewModelScope.launch {
            try {
                val request = ScheduleCreateRequest(
                    title = title,
                    cron_expression = cronExpression
                )
                ApiClient.scheduleApi.createSchedule(request)
                loadSchedules() // 새로고침
            } catch (e: Exception) {
                _state.value = ScheduleState.Error(e.message ?: "일정 생성에 실패했습니다")
            }
        }
    }

    fun deleteSchedule(id: Int) {
        viewModelScope.launch {
            try {
                ApiClient.scheduleApi.deleteSchedule(id)
                loadSchedules() // 새로고침
            } catch (e: Exception) {
                _state.value = ScheduleState.Error(e.message ?: "일정 삭제에 실패했습니다")
            }
        }
    }
}
