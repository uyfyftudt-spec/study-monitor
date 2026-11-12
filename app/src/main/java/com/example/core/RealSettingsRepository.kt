package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class RealSettingsRepository(private val context: Context) : SettingsRepository {
    private val THRESHOLD_KEY = intPreferencesKey("inactivity_threshold")
    private val ACTIVE_SESSION_START_KEY = longPreferencesKey("active_session_start")
    private val ALARM_SOUND_URI_KEY = androidx.datastore.preferences.core.stringPreferencesKey("alarm_sound_uri")
    private val EXAM_DATE_KEY = androidx.datastore.preferences.core.stringPreferencesKey("exam_date")

    override val inactivityThresholdMinutes: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[THRESHOLD_KEY] ?: 10 }
        
    override suspend fun setInactivityThresholdMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[THRESHOLD_KEY] = minutes
        }
    }
    
    override val activeSessionStartTime: Flow<Long?> = context.dataStore.data
        .map { preferences -> 
            val time = preferences[ACTIVE_SESSION_START_KEY] 
            if (time != null && time > 0) time else null
        }
        
    override suspend fun saveActiveSession(startTime: Long) {
        context.dataStore.edit { preferences ->
            preferences[ACTIVE_SESSION_START_KEY] = startTime
        }
    }
    
    override suspend fun clearActiveSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(ACTIVE_SESSION_START_KEY)
        }
    }
    
    override val alarmSoundUri: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[ALARM_SOUND_URI_KEY] }
        
    override suspend fun setAlarmSoundUri(uri: String?) {
        context.dataStore.edit { preferences ->
            if (uri != null) {
                preferences[ALARM_SOUND_URI_KEY] = uri
            } else {
                preferences.remove(ALARM_SOUND_URI_KEY)
            }
        }
    }

    private val AUTO_WAKE_UP_ENABLED_KEY = androidx.datastore.preferences.core.booleanPreferencesKey("auto_wake_up_enabled")
    private val AUTO_WAKE_UP_TARGET_TIME_KEY = androidx.datastore.preferences.core.longPreferencesKey("auto_wake_up_target_time")
    private val AUTO_WAKE_UP_ATTEMPT_COUNT_KEY = androidx.datastore.preferences.core.intPreferencesKey("auto_wake_up_attempt_count")

    override val examDate: Flow<String> = context.dataStore.data
        .map { preferences -> preferences[EXAM_DATE_KEY] ?: SettingsRepository.DEFAULT_EXAM_DATE }

    override suspend fun setExamDate(dateString: String) {
        context.dataStore.edit { preferences ->
            preferences[EXAM_DATE_KEY] = dateString
        }
    }

    override val autoWakeUpEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[AUTO_WAKE_UP_ENABLED_KEY] ?: false }

    override suspend fun setAutoWakeUpEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_WAKE_UP_ENABLED_KEY] = enabled
        }
    }

    override val autoWakeUpTargetTimeMs: Flow<Long> = context.dataStore.data
        .map { preferences -> preferences[AUTO_WAKE_UP_TARGET_TIME_KEY] ?: 0L }

    override suspend fun setAutoWakeUpTargetTimeMs(timeMs: Long) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_WAKE_UP_TARGET_TIME_KEY] = timeMs
        }
    }

    override val autoWakeUpAttemptCount: Flow<Int> = context.dataStore.data
        .map { preferences -> preferences[AUTO_WAKE_UP_ATTEMPT_COUNT_KEY] ?: 0 }

    override suspend fun setAutoWakeUpAttemptCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_WAKE_UP_ATTEMPT_COUNT_KEY] = count
        }
    }
}
