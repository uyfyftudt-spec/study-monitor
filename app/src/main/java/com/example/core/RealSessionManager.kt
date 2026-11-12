package com.example.core

import com.example.model.SessionStatus
import com.example.model.StudySession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class RealSessionManager(
    private val activityMonitor: ActivityMonitor,
    private val inactivityDetector: InactivityDetector,
    private val settingsRepository: SettingsRepository,
    private val statisticsRepository: StatisticsRepository,
    private val alarmSystem: AlarmSystem,
    private val coroutineScope: CoroutineScope,
    private val timeProvider: TimeProvider = DefaultTimeProvider(),
    private val forestRepository: ForestRepository? = null,
    private val dayLevelRepository: DayLevelRepository? = null
) : SessionManager {

    private val _currentSession = MutableStateFlow<StudySession?>(null)
    override val currentSession: StateFlow<StudySession?> = _currentSession.asStateFlow()

    private val _sessionState = MutableStateFlow(SessionStatus.IDLE)
    override val sessionState: StateFlow<SessionStatus> = _sessionState.asStateFlow()

    private val _elapsedStudyTime = MutableStateFlow(0L)
    override val elapsedStudyTime: StateFlow<Long> = _elapsedStudyTime.asStateFlow()
    
    private var timerJob: Job? = null
    private var inactivityJob: Job? = null
    
    private var activeTimeAccumulator: Long = 0
    private var inactiveTimeAccumulator: Long = 0
    private var alertCount: Int = 0
    private var lastSavedActiveSeconds: Long = 0L

    init {
        coroutineScope.launch {
            if (dayLevelRepository != null) {
                combine(
                    settingsRepository.inactivityThresholdMinutes,
                    dayLevelRepository.isDayLevelsEnabled,
                    dayLevelRepository.todayConfig
                ) { manualThreshold, levelsEnabled, levelConfig ->
                    if (levelsEnabled) levelConfig.thresholdMinutes else manualThreshold
                }.collect { activeThreshold ->
                    inactivityDetector.setThreshold(activeThreshold)
                }
            } else {
                settingsRepository.inactivityThresholdMinutes.collect { minutes ->
                    inactivityDetector.setThreshold(minutes)
                }
            }
        }
        
        coroutineScope.launch {
            val savedStartTime = settingsRepository.activeSessionStartTime.firstOrNull()
            if (savedStartTime != null && savedStartTime > 0) {
                // Recover session
                _currentSession.value = StudySession(
                    id = UUID.randomUUID().toString(),
                    startTime = LocalDateTime.now(), // Approximate
                    status = SessionStatus.STUDYING
                )
                _sessionState.value = SessionStatus.STUDYING
                
                val now = timeProvider.currentTimeMillis()
                activeTimeAccumulator = now - savedStartTime
                lastSavedActiveSeconds = activeTimeAccumulator / 1000
                _elapsedStudyTime.value = activeTimeAccumulator / 1000
                
                activityMonitor.startMonitoring()
                startTimer()
                startInactivityDetection()
            }
        }
    }

    override fun startSession() {
        val currentState = _sessionState.value
        if (currentState != SessionStatus.IDLE && currentState != SessionStatus.COMPLETED) {
            return
        }
        
        activeTimeAccumulator = 0
        inactiveTimeAccumulator = 0
        alertCount = 0
        lastSavedActiveSeconds = 0L
        
        _currentSession.value = StudySession(
            id = UUID.randomUUID().toString(),
            startTime = LocalDateTime.now(),
            status = SessionStatus.STUDYING
        )
        _sessionState.value = SessionStatus.STUDYING
        _elapsedStudyTime.value = 0L
        
        coroutineScope.launch {
            settingsRepository.saveActiveSession(timeProvider.currentTimeMillis())
            forestRepository?.clearCurrentTreeProgress()
        }
        
        activityMonitor.startMonitoring()
        startTimer()
        startInactivityDetection()
    }

    override fun pauseSession() {
        val currentState = _sessionState.value
        if (currentState == SessionStatus.STUDYING || currentState == SessionStatus.INACTIVE) {
            flushActiveStudySeconds()
            _sessionState.value = SessionStatus.PAUSED
            updateSessionObject()
            stopTimer()
        }
    }

    override fun resumeSession() {
        if (_sessionState.value == SessionStatus.PAUSED || _sessionState.value == SessionStatus.INACTIVE) {
            _sessionState.value = SessionStatus.STUDYING
            updateSessionObject()
            alarmSystem.stopAlarm()
            startTimer()
            startInactivityDetection()
        }
    }

    override fun endSession() {
        val currentState = _sessionState.value
        if (currentState == SessionStatus.IDLE || currentState == SessionStatus.COMPLETED) return
        
        flushActiveStudySeconds()
        _sessionState.value = SessionStatus.COMPLETED
        stopTimer()
        activityMonitor.stopMonitoring()
        inactivityDetector.stopDetecting()
        alarmSystem.stopAlarm()
        
        val finalSession = _currentSession.value?.copy(
            endTime = LocalDateTime.now(),
            activeStudyTimeSeconds = activeTimeAccumulator / 1000,
            inactiveTimeSeconds = inactiveTimeAccumulator / 1000,
            alertCount = alertCount,
            status = SessionStatus.COMPLETED
        )
        
        _currentSession.value = finalSession
        
        coroutineScope.launch {
            settingsRepository.clearActiveSession()
            finalSession?.let {
                statisticsRepository.saveSession(it)
                forestRepository?.awardCompletedTree(it)
            }
        }
    }
    
    override fun cancelMonitoring() {
        flushActiveStudySeconds()
        stopTimer()
    }

    private fun flushActiveStudySeconds() {
        val currentActiveSec = activeTimeAccumulator / 1000
        val unsavedSec = currentActiveSec - lastSavedActiveSeconds
        if (unsavedSec > 0) {
            val today = LocalDate.now()
            lastSavedActiveSeconds = currentActiveSec
            coroutineScope.launch {
                dayLevelRepository?.addStudiedSeconds(today, unsavedSec)
            }
        }
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = coroutineScope.launch {
            var lastTick = timeProvider.currentTimeMillis()
            while (isActive) {
                delay(1000)
                val now = timeProvider.currentTimeMillis()
                val delta = now - lastTick
                lastTick = now
                
                if (_sessionState.value == SessionStatus.STUDYING) {
                    activeTimeAccumulator += delta
                    val currentActiveSec = activeTimeAccumulator / 1000
                    val unsavedSec = currentActiveSec - lastSavedActiveSeconds
                    if (unsavedSec >= 30) {
                        val today = LocalDate.now()
                        lastSavedActiveSeconds = currentActiveSec
                        dayLevelRepository?.addStudiedSeconds(today, unsavedSec)
                    }
                } else if (_sessionState.value == SessionStatus.INACTIVE) {
                    inactiveTimeAccumulator += delta
                }
                
                if (_sessionState.value != SessionStatus.PAUSED) {
                    _elapsedStudyTime.value = (activeTimeAccumulator + inactiveTimeAccumulator) / 1000
                    updateSessionObject()
                }
            }
        }
    }
    
    private fun startInactivityDetection() {
        inactivityDetector.startDetecting()
        inactivityJob?.cancel()
        inactivityJob = coroutineScope.launch {
            inactivityDetector.inactivityEvents.collect { level ->
                when (level) {
                    InactivityLevel.WARNING -> {
                        if (_sessionState.value == SessionStatus.STUDYING) {
                            flushActiveStudySeconds()
                            _sessionState.value = SessionStatus.INACTIVE
                            alertCount++
                            updateSessionObject()
                            alarmSystem.triggerAlarm()
                        }
                    }
                    InactivityLevel.NORMAL -> {
                        if (_sessionState.value == SessionStatus.INACTIVE) {
                            _sessionState.value = SessionStatus.STUDYING
                            updateSessionObject()
                            alarmSystem.stopAlarm()
                        }
                    }
                    else -> {}
                }
            }
        }
    }
    
    private fun stopTimer() {
        timerJob?.cancel()
        inactivityJob?.cancel()
        inactivityDetector.stopDetecting()
    }
    
    private fun updateSessionObject() {
        _currentSession.value = _currentSession.value?.copy(
            activeStudyTimeSeconds = activeTimeAccumulator / 1000,
            inactiveTimeSeconds = inactiveTimeAccumulator / 1000,
            alertCount = alertCount,
            status = _sessionState.value
        )
    }
}
