package com.example.ui.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.DayLevelRepository
import com.example.model.DayLevel
import com.example.model.DayLevelConfig
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class WeeklyPlannerViewModel(
    private val dayLevelRepository: DayLevelRepository
) : ViewModel() {

    val isDayLevelsEnabled: StateFlow<Boolean> = dayLevelRepository.isDayLevelsEnabled
    val lastUsedLevel: StateFlow<DayLevel> = dayLevelRepository.lastUsedLevel
    val schedule: StateFlow<Map<LocalDate, DayLevel>> = dayLevelRepository.schedule
    val levelConfigs: StateFlow<Map<DayLevel, DayLevelConfig>> = dayLevelRepository.levelConfigs
    val todayLevel: StateFlow<DayLevel> = dayLevelRepository.todayLevel

    fun setLevelForDate(date: LocalDate, level: DayLevel) {
        viewModelScope.launch {
            dayLevelRepository.setLevelForDate(date, level)
        }
    }

    fun updateLevelConfig(level: DayLevel, goalHours: Float, thresholdMinutes: Int) {
        viewModelScope.launch {
            dayLevelRepository.updateLevelConfig(level, goalHours, thresholdMinutes)
        }
    }

    fun resetLevelConfigsToDefault() {
        viewModelScope.launch {
            dayLevelRepository.resetLevelConfigsToDefault()
        }
    }

    fun setDayLevelsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dayLevelRepository.setDayLevelsEnabled(enabled)
        }
    }
}
