package com.example.core

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val inactivityThresholdMinutes: Flow<Int>
    suspend fun setInactivityThresholdMinutes(minutes: Int)
    
    val activeSessionStartTime: Flow<Long?>
    suspend fun saveActiveSession(startTime: Long)
    suspend fun clearActiveSession()
    
    val alarmSoundUri: Flow<String?>
    suspend fun setAlarmSoundUri(uri: String?)

    val examDate: Flow<String>
    suspend fun setExamDate(dateString: String)

    val autoWakeUpEnabled: Flow<Boolean>
    suspend fun setAutoWakeUpEnabled(enabled: Boolean)

    val autoWakeUpTargetTimeMs: Flow<Long>
    suspend fun setAutoWakeUpTargetTimeMs(timeMs: Long)

    val autoWakeUpAttemptCount: Flow<Int>
    suspend fun setAutoWakeUpAttemptCount(count: Int)

    companion object {
        const val DEFAULT_EXAM_DATE = "2026-11-07"
    }
}
