package com.example.core

import com.example.model.StudySession
import kotlinx.coroutines.flow.Flow

data class DailyStatistics(
    val totalStudyTimeSeconds: Long,
    val activeStudyTimeSeconds: Long,
    val inactiveTimeSeconds: Long,
    val totalAlerts: Int
)

interface StatisticsRepository {
    fun getTodayStatistics(): Flow<DailyStatistics>
    fun getRecentSessions(): Flow<List<StudySession>>
    suspend fun saveSession(session: StudySession)
}
