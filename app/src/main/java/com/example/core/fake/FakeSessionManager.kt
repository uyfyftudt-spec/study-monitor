package com.example.core.fake

import com.example.core.SessionManager
import com.example.model.SessionStatus
import com.example.model.StudySession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

class FakeSessionManager : SessionManager {
    private val _currentSession = MutableStateFlow<StudySession?>(null)
    override val currentSession: StateFlow<StudySession?> = _currentSession.asStateFlow()

    private val _sessionState = MutableStateFlow(SessionStatus.IDLE)
    override val sessionState: StateFlow<SessionStatus> = _sessionState.asStateFlow()

    private val _elapsedStudyTime = MutableStateFlow(0L)
    override val elapsedStudyTime: StateFlow<Long> = _elapsedStudyTime.asStateFlow()

    override fun startSession() {
        _sessionState.value = SessionStatus.STUDYING
        _currentSession.value = StudySession(
            id = "test_id",
            startTime = LocalDateTime.now(),
            status = SessionStatus.STUDYING
        )
    }

    override fun pauseSession() {
        _sessionState.value = SessionStatus.PAUSED
    }

    override fun resumeSession() {
        _sessionState.value = SessionStatus.STUDYING
    }

    override fun endSession() {
        _sessionState.value = SessionStatus.COMPLETED
        _currentSession.value = null
        _elapsedStudyTime.value = 0L
    }
    
    override fun cancelMonitoring() {
        // no-op
    }
}
