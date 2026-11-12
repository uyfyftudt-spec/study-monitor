package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DayLevel
import com.example.model.DayLevelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

private val Context.dayLevelsDataStore by preferencesDataStore(name = "day_levels_data")

class RealDayLevelRepository(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : DayLevelRepository {

    private val DAY_LEVELS_ENABLED_KEY = booleanPreferencesKey("day_levels_enabled")
    private val LAST_USED_LEVEL_KEY = stringPreferencesKey("last_used_level")
    private val LEVEL_CONFIGS_JSON_KEY = stringPreferencesKey("level_configs_json")
    private val SCHEDULE_JSON_KEY = stringPreferencesKey("schedule_json")
    private val STUDIED_SECONDS_JSON_KEY = stringPreferencesKey("studied_seconds_json")

    private val _isDayLevelsEnabled = MutableStateFlow(true)
    override val isDayLevelsEnabled: StateFlow<Boolean> = _isDayLevelsEnabled.asStateFlow()

    private val _lastUsedLevel = MutableStateFlow(DayLevel.NORMAL)
    override val lastUsedLevel: StateFlow<DayLevel> = _lastUsedLevel.asStateFlow()

    private val _levelConfigs = MutableStateFlow(defaultLevelConfigs())
    override val levelConfigs: StateFlow<Map<DayLevel, DayLevelConfig>> = _levelConfigs.asStateFlow()

    private val _schedule = MutableStateFlow<Map<LocalDate, DayLevel>>(emptyMap())
    override val schedule: StateFlow<Map<LocalDate, DayLevel>> = _schedule.asStateFlow()

    private val _studiedSecondsByDate = MutableStateFlow<Map<LocalDate, Long>>(emptyMap())
    override val studiedSecondsByDate: StateFlow<Map<LocalDate, Long>> = _studiedSecondsByDate.asStateFlow()

    private val _todayLevel = MutableStateFlow(DayLevel.NORMAL)
    override val todayLevel: StateFlow<DayLevel> = _todayLevel.asStateFlow()

    private val _todayConfig = MutableStateFlow(DayLevelConfig(DayLevel.NORMAL.defaultGoalHours, DayLevel.NORMAL.defaultThresholdMinutes))
    override val todayConfig: StateFlow<DayLevelConfig> = _todayConfig.asStateFlow()

    private val _todayGoalSeconds = MutableStateFlow((DayLevel.NORMAL.defaultGoalHours * 3600f).toLong())
    override val todayGoalSeconds: StateFlow<Long> = _todayGoalSeconds.asStateFlow()

    private val _todayStudiedSeconds = MutableStateFlow(0L)
    override val todayStudiedSeconds: StateFlow<Long> = _todayStudiedSeconds.asStateFlow()

    private val _currentStreak = MutableStateFlow(0)
    override val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    init {
        coroutineScope.launch {
            loadInitialData()
        }

        // Midnight auto-updater
        coroutineScope.launch {
            while (isActive) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                val delayMillis = ChronoUnit.MILLIS.between(now, nextMidnight) + 1000L
                delay(delayMillis.coerceAtLeast(1000L))
                refreshForDate(LocalDate.now())
            }
        }
    }

    private suspend fun loadInitialData() {
        val preferences = context.dayLevelsDataStore.data.first()

        val enabled = preferences[DAY_LEVELS_ENABLED_KEY] ?: true
        _isDayLevelsEnabled.value = enabled

        val lastUsedName = preferences[LAST_USED_LEVEL_KEY] ?: DayLevel.NORMAL.name
        val lastUsed = try { DayLevel.valueOf(lastUsedName) } catch (e: Exception) { DayLevel.NORMAL }
        _lastUsedLevel.value = lastUsed

        val configsJson = preferences[LEVEL_CONFIGS_JSON_KEY]
        _levelConfigs.value = parseConfigsJson(configsJson)

        val scheduleJson = preferences[SCHEDULE_JSON_KEY]
        _schedule.value = parseScheduleJson(scheduleJson)

        val studiedJson = preferences[STUDIED_SECONDS_JSON_KEY]
        _studiedSecondsByDate.value = parseStudiedSecondsJson(studiedJson)

        refreshForDate(LocalDate.now())
    }

    override fun refreshForDate(date: LocalDate) {
        val scheduled = _schedule.value[date]
        val currentLevel = scheduled ?: _lastUsedLevel.value
        _todayLevel.value = currentLevel

        val config = _levelConfigs.value[currentLevel] ?: DayLevelConfig(currentLevel.defaultGoalHours, currentLevel.defaultThresholdMinutes)
        _todayConfig.value = config
        _todayGoalSeconds.value = (config.goalHours * 3600f).toLong()

        val studied = _studiedSecondsByDate.value[date] ?: 0L
        _todayStudiedSeconds.value = studied

        _currentStreak.value = calculateStreak(
            today = date,
            studiedMap = _studiedSecondsByDate.value,
            scheduleMap = _schedule.value,
            configs = _levelConfigs.value,
            defaultLevel = _lastUsedLevel.value
        )
    }

    override suspend fun setDayLevelsEnabled(enabled: Boolean) {
        _isDayLevelsEnabled.value = enabled
        context.dayLevelsDataStore.edit { preferences ->
            preferences[DAY_LEVELS_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setLastUsedLevel(level: DayLevel) {
        _lastUsedLevel.value = level
        context.dayLevelsDataStore.edit { preferences ->
            preferences[LAST_USED_LEVEL_KEY] = level.name
        }
        refreshForDate(LocalDate.now())
    }

    override suspend fun updateLevelConfig(level: DayLevel, goalHours: Float, thresholdMinutes: Int) {
        val current = _levelConfigs.value.toMutableMap()
        current[level] = DayLevelConfig(goalHours, thresholdMinutes)
        _levelConfigs.value = current

        context.dayLevelsDataStore.edit { preferences ->
            preferences[LEVEL_CONFIGS_JSON_KEY] = serializeConfigsJson(current)
        }
        refreshForDate(LocalDate.now())
    }

    override suspend fun resetLevelConfigsToDefault() {
        val defaults = defaultLevelConfigs()
        _levelConfigs.value = defaults
        context.dayLevelsDataStore.edit { preferences ->
            preferences.remove(LEVEL_CONFIGS_JSON_KEY)
        }
        refreshForDate(LocalDate.now())
    }

    override suspend fun setLevelForDate(date: LocalDate, level: DayLevel) {
        val current = _schedule.value.toMutableMap()
        current[date] = level
        _schedule.value = current

        val isToday = (date == LocalDate.now())
        if (isToday) {
            _lastUsedLevel.value = level
        }

        context.dayLevelsDataStore.edit { preferences ->
            preferences[SCHEDULE_JSON_KEY] = serializeScheduleJson(current)
            if (isToday) {
                preferences[LAST_USED_LEVEL_KEY] = level.name
            }
        }
        refreshForDate(LocalDate.now())
    }

    override suspend fun setTodayLevel(level: DayLevel) {
        setLevelForDate(LocalDate.now(), level)
    }

    override suspend fun addStudiedSeconds(date: LocalDate, seconds: Long) {
        if (seconds <= 0) return
        val current = _studiedSecondsByDate.value.toMutableMap()
        val existing = current[date] ?: 0L
        val updated = existing + seconds
        current[date] = updated
        _studiedSecondsByDate.value = current

        if (date == LocalDate.now()) {
            _todayStudiedSeconds.value = updated
        }

        context.dayLevelsDataStore.edit { preferences ->
            preferences[STUDIED_SECONDS_JSON_KEY] = serializeStudiedSecondsJson(current)
        }

        _currentStreak.value = calculateStreak(
            today = LocalDate.now(),
            studiedMap = current,
            scheduleMap = _schedule.value,
            configs = _levelConfigs.value,
            defaultLevel = _lastUsedLevel.value
        )
    }

    override suspend fun setStudiedSeconds(date: LocalDate, seconds: Long) {
        val current = _studiedSecondsByDate.value.toMutableMap()
        current[date] = seconds
        _studiedSecondsByDate.value = current

        if (date == LocalDate.now()) {
            _todayStudiedSeconds.value = seconds
        }

        context.dayLevelsDataStore.edit { preferences ->
            preferences[STUDIED_SECONDS_JSON_KEY] = serializeStudiedSecondsJson(current)
        }

        _currentStreak.value = calculateStreak(
            today = LocalDate.now(),
            studiedMap = current,
            scheduleMap = _schedule.value,
            configs = _levelConfigs.value,
            defaultLevel = _lastUsedLevel.value
        )
    }

    private fun calculateStreak(
        today: LocalDate,
        studiedMap: Map<LocalDate, Long>,
        scheduleMap: Map<LocalDate, DayLevel>,
        configs: Map<DayLevel, DayLevelConfig>,
        defaultLevel: DayLevel
    ): Int {
        var streak = 0

        // Check today
        val todayStudied = studiedMap[today] ?: 0L
        val todayLevel = scheduleMap[today] ?: defaultLevel
        val todayConfig = configs[todayLevel] ?: DayLevelConfig(todayLevel.defaultGoalHours, todayLevel.defaultThresholdMinutes)
        val todayGoalSec = (todayConfig.goalHours * 3600f).toLong()

        val todayCompleted = todayGoalSec > 0 && todayStudied >= todayGoalSec
        if (todayCompleted) {
            streak++
        }

        // Count backwards from yesterday
        var checkDate = today.minusDays(1)
        while (true) {
            val studied = studiedMap[checkDate] ?: 0L
            val level = scheduleMap[checkDate] ?: defaultLevel
            val config = configs[level] ?: DayLevelConfig(level.defaultGoalHours, level.defaultThresholdMinutes)
            val goalSec = (config.goalHours * 3600f).toLong()

            if (goalSec > 0 && studied >= goalSec) {
                streak++
                checkDate = checkDate.minusDays(1)
            } else {
                break
            }
        }
        return streak
    }

    private fun defaultLevelConfigs(): Map<DayLevel, DayLevelConfig> = mapOf(
        DayLevel.NORMAL to DayLevelConfig(DayLevel.NORMAL.defaultGoalHours, DayLevel.NORMAL.defaultThresholdMinutes),
        DayLevel.INTENSIVE to DayLevelConfig(DayLevel.INTENSIVE.defaultGoalHours, DayLevel.INTENSIVE.defaultThresholdMinutes),
        DayLevel.WAR to DayLevelConfig(DayLevel.WAR.defaultGoalHours, DayLevel.WAR.defaultThresholdMinutes),
        DayLevel.REST to DayLevelConfig(DayLevel.REST.defaultGoalHours, DayLevel.REST.defaultThresholdMinutes)
    )

    private fun parseConfigsJson(json: String?): Map<DayLevel, DayLevelConfig> {
        val map = defaultLevelConfigs().toMutableMap()
        if (json.isNullOrBlank()) return map
        try {
            val root = JSONObject(json)
            for (level in DayLevel.values()) {
                if (root.has(level.name)) {
                    val obj = root.getJSONObject(level.name)
                    val goal = obj.optDouble("goalHours", level.defaultGoalHours.toDouble()).toFloat()
                    val threshold = obj.optInt("thresholdMinutes", level.defaultThresholdMinutes)
                    map[level] = DayLevelConfig(goal, threshold)
                }
            }
        } catch (e: Exception) {
            // fallback to default
        }
        return map
    }

    private fun serializeConfigsJson(configs: Map<DayLevel, DayLevelConfig>): String {
        val root = JSONObject()
        for ((level, config) in configs) {
            val obj = JSONObject().apply {
                put("goalHours", config.goalHours.toDouble())
                put("thresholdMinutes", config.thresholdMinutes)
            }
            root.put(level.name, obj)
        }
        return root.toString()
    }

    private fun parseScheduleJson(json: String?): Map<LocalDate, DayLevel> {
        if (json.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<LocalDate, DayLevel>()
        try {
            val root = JSONObject(json)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val date = LocalDate.parse(key)
                val levelName = root.getString(key)
                result[date] = DayLevel.valueOf(levelName)
            }
        } catch (e: Exception) {
            // ignore
        }
        return result
    }

    private fun serializeScheduleJson(schedule: Map<LocalDate, DayLevel>): String {
        val root = JSONObject()
        for ((date, level) in schedule) {
            root.put(date.toString(), level.name)
        }
        return root.toString()
    }

    private fun parseStudiedSecondsJson(json: String?): Map<LocalDate, Long> {
        if (json.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<LocalDate, Long>()
        try {
            val root = JSONObject(json)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val date = LocalDate.parse(key)
                val seconds = root.getLong(key)
                result[date] = seconds
            }
        } catch (e: Exception) {
            // ignore
        }
        return result
    }

    private fun serializeStudiedSecondsJson(studied: Map<LocalDate, Long>): String {
        val root = JSONObject()
        for ((date, sec) in studied) {
            root.put(date.toString(), sec)
        }
        return root.toString()
    }
}
