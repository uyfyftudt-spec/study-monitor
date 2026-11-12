package com.example.core

import com.example.model.SessionStatus
import com.example.model.StudySession
import kotlinx.coroutines.flow.StateFlow

interface SessionManager {
    val currentSession: StateFlow<StudySession?>
    val sessionState: StateFlow<SessionStatus>
    val elapsedStudyTime: StateFlow<Long>
    
    fun startSession()
    fun pauseSession()
    fun resumeSession()
    fun endSession()
    fun cancelMonitoring()
}
