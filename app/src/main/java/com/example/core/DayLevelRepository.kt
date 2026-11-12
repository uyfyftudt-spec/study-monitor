package com.example.core

import com.example.model.DayLevel
import com.example.model.DayLevelConfig
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

interface DayLevelRepository {
    val isDayLevelsEnabled: StateFlow<Boolean>
    suspend fun setDayLevelsEnabled(enabled: Boolean)

    val lastUsedLevel: StateFlow<DayLevel>
    suspend fun setLastUsedLevel(level: DayLevel)

    val levelConfigs: StateFlow<Map<DayLevel, DayLevelConfig>>
    suspend fun updateLevelConfig(level: DayLevel, goalHours: Float, thresholdMinutes: Int)
    suspend fun resetLevelConfigsToDefault()

    val schedule: StateFlow<Map<LocalDate, DayLevel>>
    suspend fun setLevelForDate(date: LocalDate, level: DayLevel)

    val studiedSecondsByDate: StateFlow<Map<LocalDate, Long>>
    suspend fun addStudiedSeconds(date: LocalDate, seconds: Long)
    suspend fun setStudiedSeconds(date: LocalDate, seconds: Long)

    val todayLevel: StateFlow<DayLevel>
    val todayConfig: StateFlow<DayLevelConfig>
    val todayGoalSeconds: StateFlow<Long>
    val todayStudiedSeconds: StateFlow<Long>
    val currentStreak: StateFlow<Int>

    suspend fun setTodayLevel(level: DayLevel)
    fun refreshForDate(date: LocalDate = LocalDate.now())
}
