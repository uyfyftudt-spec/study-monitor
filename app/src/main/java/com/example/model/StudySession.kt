package com.example.model

import java.time.LocalDateTime

data class StudySession(
    val id: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime? = null,
    val activeStudyTimeSeconds: Long = 0,
    val inactiveTimeSeconds: Long = 0,
    val alertCount: Int = 0,
    val status: SessionStatus = SessionStatus.IDLE
)
