package com.example.core

import com.example.model.StudySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

class RealStatisticsRepository : StatisticsRepository {
    // In-memory implementation for now.
    // TODO: Move to Room database in the future for persistent session history.
    private val _sessions = MutableStateFlow<List<StudySession>>(emptyList())
    
    override fun getTodayStatistics(): Flow<DailyStatistics> {
        return _sessions.map { sessions ->
            val today = LocalDate.now()
            val todaySessions = sessions.filter { it.startTime.toLocalDate() == today }
            
            DailyStatistics(
                totalStudyTimeSeconds = todaySessions.sumOf { it.activeStudyTimeSeconds + it.inactiveTimeSeconds },
                activeStudyTimeSeconds = todaySessions.sumOf { it.activeStudyTimeSeconds },
                inactiveTimeSeconds = todaySessions.sumOf { it.inactiveTimeSeconds },
                totalAlerts = todaySessions.sumOf { it.alertCount }
            )
        }
    }
    
    override fun getRecentSessions(): Flow<List<StudySession>> {
        return _sessions.map { sessions ->
            sessions.sortedByDescending { it.startTime }.take(10)
        }
    }
    
    override suspend fun saveSession(session: StudySession) {
        _sessions.update { current ->
            current + session
        }
    }
}
