package com.example.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.DayLevelRepository
import com.example.core.FloatingTimerManager
import com.example.core.SessionManager
import com.example.core.SettingsRepository
import com.example.core.StatisticsRepository
import com.example.model.DayLevel
import com.example.model.DayLevelConfig
import com.example.model.SessionStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val sessionManager: SessionManager,
    private val statisticsRepository: StatisticsRepository,
    private val settingsRepository: SettingsRepository,
    private val floatingTimerManager: FloatingTimerManager,
    private val dayLevelRepository: DayLevelRepository
) : ViewModel() {

    val isFloatingTimerShowing = floatingTimerManager.isShowing.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun toggleFloatingTimer() {
        floatingTimerManager.toggleTimer()
    }

    fun resetFloatingTimer() {
        floatingTimerManager.resetTimer()
    }

    val currentSession = sessionManager.currentSession.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val sessionState = sessionManager.sessionState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SessionStatus.IDLE
    )

    val elapsedStudyTime = sessionManager.elapsedStudyTime.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0L
    )

    val todayStatistics = statisticsRepository.getTodayStatistics().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val recentSessions = statisticsRepository.getRecentSessions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val thresholdMinutes = settingsRepository.inactivityThresholdMinutes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 10
    )

    // Day Level integration
    val isDayLevelsEnabled: StateFlow<Boolean> = dayLevelRepository.isDayLevelsEnabled
    val todayLevel: StateFlow<DayLevel> = dayLevelRepository.todayLevel
    val todayConfig: StateFlow<DayLevelConfig> = dayLevelRepository.todayConfig
    val todayGoalSeconds: StateFlow<Long> = dayLevelRepository.todayGoalSeconds
    val todayStudiedSeconds: StateFlow<Long> = dayLevelRepository.todayStudiedSeconds
    val currentStreak: StateFlow<Int> = dayLevelRepository.currentStreak

    fun setTodayLevel(level: DayLevel) {
        viewModelScope.launch {
            dayLevelRepository.setTodayLevel(level)
        }
    }

    fun startSession() {
        sessionManager.startSession()
        floatingTimerManager.showTimer()
    }

    fun ensureOverlayVisible() {
        if (!isFloatingTimerShowing.value) {
            floatingTimerManager.showTimer()
        }
    }

    fun pauseSession() {
        sessionManager.pauseSession()
    }

    fun resumeSession() {
        sessionManager.resumeSession()
    }

    fun endSession() {
        sessionManager.endSession()
        floatingTimerManager.hideTimer()
    }
}
