package com.example.ez_capstone.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ez_capstone.db.dao.ScheduleDao
import com.example.ez_capstone.db.entity.ScheduleEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val scheduleDao: ScheduleDao
) : ViewModel() {

    companion object {
        private const val TAG = "ScheduleViewModel"
    }

    data class ScheduleUiState(
        val isLoading: Boolean = false,
        val schedules: List<ScheduleEntity> = emptyList(),
        val selectedDate: LocalDate = LocalDate.now(),
        val currentMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
        val showAddDialog: Boolean = false,
        val error: String? = null
    )

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadSchedules()
    }

    fun loadSchedules() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            try {
                val schedules = scheduleDao.getActive()
                _uiState.update { it.copy(isLoading = false, schedules = schedules) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load schedules", e)
                _uiState.update { it.copy(isLoading = false, error = "일정을 불러올 수 없습니다") }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun navigateMonth(offset: Int) {
        _uiState.update {
            it.copy(currentMonth = it.currentMonth.plusMonths(offset.toLong()))
        }
    }

    fun showAddDialog() {
        _uiState.update { it.copy(showAddDialog = true) }
    }

    fun hideAddDialog() {
        _uiState.update { it.copy(showAddDialog = false) }
    }

    fun createSchedule(title: String, hour: Int, minute: Int) {
        val date = _uiState.value.selectedDate
        val cronExpression = "$minute $hour ${date.dayOfMonth} ${date.monthValue} *"
        viewModelScope.launch {
            try {
                scheduleDao.insert(
                    ScheduleEntity(
                        title = title,
                        cronExpression = cronExpression,
                        date = date.toString(),
                        isActive = true
                    )
                )
                _uiState.update { it.copy(showAddDialog = false) }
                loadSchedules()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create schedule", e)
                _uiState.update { it.copy(error = "일정 추가에 실패했습니다") }
            }
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            try {
                val all = scheduleDao.getAll()
                val entity = all.find { it.id == id }
                if (entity != null) {
                    scheduleDao.delete(entity)
                    loadSchedules()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete schedule", e)
            }
        }
    }

    fun getSchedulesForDate(date: LocalDate): List<ScheduleEntity> {
        val dateStr = date.toString() // "yyyy-MM-dd"
        return _uiState.value.schedules.filter { schedule ->
            schedule.isActive && (
                schedule.date == dateStr ||  // 특정 날짜 일정
                schedule.date == null        // 레거시: 날짜 미지정은 매일
            )
        }
    }
}
