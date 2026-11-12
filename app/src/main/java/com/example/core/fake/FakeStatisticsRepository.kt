package com.example.core.fake

import com.example.core.DailyStatistics
import com.example.core.StatisticsRepository
import com.example.model.SessionStatus
import com.example.model.StudySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime

class FakeStatisticsRepository : StatisticsRepository {
    override fun getTodayStatistics(): Flow<DailyStatistics> {
        return flowOf(
            DailyStatistics(
                totalStudyTimeSeconds = 3600 * 2 + 1800, // 2h 30m
                activeStudyTimeSeconds = 3600 * 2, // 2h
                inactiveTimeSeconds = 1800, // 30m
                totalAlerts = 2
            )
        )
    }

    override fun getRecentSessions(): Flow<List<StudySession>> {
        return flowOf(
            listOf(
                StudySession(
                    id = "1",
                    startTime = LocalDateTime.now().minusHours(3),
                    endTime = LocalDateTime.now().minusHours(1),
                    activeStudyTimeSeconds = 6000,
                    inactiveTimeSeconds = 1200,
                    alertCount = 1,
                    status = SessionStatus.COMPLETED
                )
            )
        )
    }

    override suspend fun saveSession(session: StudySession) {
        // No-op for fake
    }
}
