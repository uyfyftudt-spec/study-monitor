package com.example.core.fake

import com.example.core.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSettingsRepository : SettingsRepository {
    private val _threshold = MutableStateFlow(10)
    override val inactivityThresholdMinutes: Flow<Int> = _threshold.asStateFlow()

    override suspend fun setInactivityThresholdMinutes(minutes: Int) {
        _threshold.value = minutes
    }
    
    private val _activeSession = MutableStateFlow<Long?>(null)
    override val activeSessionStartTime = _activeSession.asStateFlow()

    override suspend fun saveActiveSession(startTime: Long) {
        _activeSession.value = startTime
    }

    override suspend fun clearActiveSession() {
        _activeSession.value = null
    }

    private val _alarmSoundUri = MutableStateFlow<String?>(null)
    override val alarmSoundUri = _alarmSoundUri.asStateFlow()

    override suspend fun setAlarmSoundUri(uri: String?) {
        _alarmSoundUri.value = uri
    }

    private val _examDate = MutableStateFlow(SettingsRepository.DEFAULT_EXAM_DATE)
    override val examDate: Flow<String> = _examDate.asStateFlow()

    override suspend fun setExamDate(dateString: String) {
        _examDate.value = dateString
    }

    private val _autoWakeUpEnabled = MutableStateFlow(false)
    override val autoWakeUpEnabled: Flow<Boolean> = _autoWakeUpEnabled.asStateFlow()

    override suspend fun setAutoWakeUpEnabled(enabled: Boolean) {
        _autoWakeUpEnabled.value = enabled
    }

    private val _autoWakeUpTargetTimeMs = MutableStateFlow(0L)
    override val autoWakeUpTargetTimeMs: Flow<Long> = _autoWakeUpTargetTimeMs.asStateFlow()

    override suspend fun setAutoWakeUpTargetTimeMs(timeMs: Long) {
        _autoWakeUpTargetTimeMs.value = timeMs
    }

    private val _autoWakeUpAttemptCount = MutableStateFlow(0)
    override val autoWakeUpAttemptCount: Flow<Int> = _autoWakeUpAttemptCount.asStateFlow()

    override suspend fun setAutoWakeUpAttemptCount(count: Int) {
        _autoWakeUpAttemptCount.value = count
    }
}
