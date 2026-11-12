package com.example.core

import com.example.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.After
import org.junit.Test
import kotlinx.coroutines.cancel

@OptIn(ExperimentalCoroutinesApi::class)
class RealSessionManagerTest {

    private lateinit var sessionManager: RealSessionManager
    private lateinit var activityMonitor: FakeActivityMonitor
    private lateinit var inactivityDetector: RealInactivityDetector
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var statisticsRepository: FakeStatisticsRepository
    private lateinit var testTimeProvider: TestTimeProvider
    private lateinit var testScope: TestScope
    private lateinit var managerScope: CoroutineScope

    private lateinit var alarmSystem: FakeAlarmSystem

    @Before
    fun setup() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        managerScope = CoroutineScope(testScope.coroutineContext + kotlinx.coroutines.Job())
        testTimeProvider = TestTimeProvider(testScope)

        activityMonitor = FakeActivityMonitor()
        settingsRepository = FakeSettingsRepository()
        statisticsRepository = FakeStatisticsRepository()
        alarmSystem = FakeAlarmSystem()
        
        inactivityDetector = RealInactivityDetector(activityMonitor, managerScope, testTimeProvider)

        sessionManager = RealSessionManager(
            activityMonitor = activityMonitor,
            inactivityDetector = inactivityDetector,
            settingsRepository = settingsRepository,
            statisticsRepository = statisticsRepository,
            alarmSystem = alarmSystem,
            coroutineScope = managerScope,
            timeProvider = testTimeProvider
        )
    }

    @After
    fun tearDown() {
        sessionManager.cancelMonitoring()
        inactivityDetector.stopDetecting()
        managerScope.cancel()
        testScope.cancel()
    }

    @Test
    fun testStartSession() = testScope.runTest {
        assertEquals(SessionStatus.IDLE, sessionManager.sessionState.value)
        
        sessionManager.startSession()
        
        assertEquals(SessionStatus.STUDYING, sessionManager.sessionState.value)
        assertNotNull(sessionManager.currentSession.value)
        
        sessionManager.endSession()
        managerScope.cancel()
    }

    @Test
    fun testPausingStopsTimer() = testScope.runTest {
        sessionManager.startSession()
        
        advanceTimeBy(5000)
        
        val elapsedBeforePause = sessionManager.elapsedStudyTime.value
        
        sessionManager.pauseSession()
        assertEquals(SessionStatus.PAUSED, sessionManager.sessionState.value)
        
        advanceTimeBy(5000)
        
        // Timer should not have advanced
        assertEquals(elapsedBeforePause, sessionManager.elapsedStudyTime.value)
        sessionManager.endSession()
        managerScope.cancel()
    }

    @Test
    fun testResumingContinuesTimer() = testScope.runTest {
        sessionManager.startSession()
        advanceTimeBy(5000)
        
        val elapsedBeforePause = sessionManager.elapsedStudyTime.value
        sessionManager.pauseSession()
        advanceTimeBy(5000)
        sessionManager.resumeSession()
        
        assertEquals(SessionStatus.STUDYING, sessionManager.sessionState.value)
        
        advanceTimeBy(5000)
        
        val elapsedAfterResume = sessionManager.elapsedStudyTime.value
        assert(elapsedAfterResume > elapsedBeforePause)
        sessionManager.endSession()
        managerScope.cancel()
    }

    @Test
    fun testInactivityThresholdTriggersInactiveState() = testScope.runTest {
        sessionManager.startSession()
        
        // Threshold is 10 minutes (600,000 ms). We'll advance by 10 minutes and 1 second.
        advanceTimeBy(601_000)
        
        assertEquals(SessionStatus.INACTIVE, sessionManager.sessionState.value)
        // Alarm should have triggered
        assert(alarmSystem.alarmTriggered)
        
        sessionManager.endSession()
        managerScope.cancel()
    }

    @Test
    fun testActivityResetsInactivityTimer() = testScope.runTest {
        sessionManager.startSession()
        
        // Advance by 5 minutes
        advanceTimeBy(300_000)
        
        // Emit activity
        activityMonitor.emitActivity()
        
        // Advance by another 6 minutes (total 11 mins). Without activity, this would trigger inactivity.
        advanceTimeBy(360_000)
        
        // Since we emitted activity at 5 mins, the timer reset. 6 mins < 10 mins threshold.
        assertEquals(SessionStatus.STUDYING, sessionManager.sessionState.value)
        
        sessionManager.endSession()
        managerScope.cancel()
    }

    @Test
    fun testEndingSessionSavesData() = testScope.runTest {
        sessionManager.startSession()
        advanceTimeBy(5000)
        sessionManager.endSession()
        testScope.advanceUntilIdle()
        
        assertEquals(SessionStatus.COMPLETED, sessionManager.sessionState.value)
        assertEquals(1, statisticsRepository.savedSessions.size)
        
        val saved = statisticsRepository.savedSessions.first()
        assert(saved.activeStudyTimeSeconds >= 0)
        assertEquals(0L, saved.inactiveTimeSeconds)
        assertEquals(SessionStatus.COMPLETED, saved.status)
        managerScope.cancel()
    }

    @Test
    fun testInvalidStateTransitions() = testScope.runTest {
        // IDLE -> PAUSED is invalid
        sessionManager.pauseSession()
        assertEquals(SessionStatus.IDLE, sessionManager.sessionState.value)
        
        sessionManager.startSession() // IDLE -> STUDYING (valid)
        
        // STUDYING -> START is ignored
        val initialId = sessionManager.currentSession.value?.id
        sessionManager.startSession()
        assertEquals(initialId, sessionManager.currentSession.value?.id)
        
        sessionManager.endSession() // STUDYING -> COMPLETED
        
        // COMPLETED -> PAUSED is invalid
        sessionManager.pauseSession()
        assertEquals(SessionStatus.COMPLETED, sessionManager.sessionState.value)
        managerScope.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TestTimeProvider(private val testScope: TestScope) : TimeProvider {
    override fun currentTimeMillis(): Long {
        return testScope.testScheduler.currentTime
    }
}

class FakeActivityMonitor : ActivityMonitor {
    private val _stream = MutableSharedFlow<ActivityType>(extraBufferCapacity = 1)
    override val activityStream = _stream

    override fun startMonitoring() {}
    override fun stopMonitoring() {}

    fun emitActivity() {
        _stream.tryEmit(ActivityType.ACTIVE)
    }
}

class FakeSettingsRepository : SettingsRepository {
    override val inactivityThresholdMinutes = MutableStateFlow(10)
    override suspend fun setInactivityThresholdMinutes(minutes: Int) {}
    
    override val activeSessionStartTime = MutableStateFlow<Long?>(null)
    override suspend fun saveActiveSession(startTime: Long) {
        activeSessionStartTime.value = startTime
    }
    override suspend fun clearActiveSession() {
        activeSessionStartTime.value = null
    }
    
    override val alarmSoundUri = MutableStateFlow<String?>(null)
    override suspend fun setAlarmSoundUri(uri: String?) {}

    override val examDate = MutableStateFlow(SettingsRepository.DEFAULT_EXAM_DATE)
    override suspend fun setExamDate(dateString: String) {
        examDate.value = dateString
    }

    override val autoWakeUpEnabled = MutableStateFlow(false)
    override suspend fun setAutoWakeUpEnabled(enabled: Boolean) {
        autoWakeUpEnabled.value = enabled
    }

    override val autoWakeUpTargetTimeMs = MutableStateFlow(0L)
    override suspend fun setAutoWakeUpTargetTimeMs(timeMs: Long) {
        autoWakeUpTargetTimeMs.value = timeMs
    }

    override val autoWakeUpAttemptCount = MutableStateFlow(0)
    override suspend fun setAutoWakeUpAttemptCount(count: Int) {
        autoWakeUpAttemptCount.value = count
    }
}

class FakeStatisticsRepository : StatisticsRepository {
    val savedSessions = mutableListOf<com.example.model.StudySession>()
    override fun getTodayStatistics() = flowOf(DailyStatistics(0, 0, 0, 0))
    override fun getRecentSessions() = flowOf(emptyList<com.example.model.StudySession>())
    override suspend fun saveSession(session: com.example.model.StudySession) {
        savedSessions.add(session)
    }
}

class FakeAlarmSystem : AlarmSystem {
    var warningTriggered = false
    var alarmTriggered = false
    var alarmStopped = false
    
    override fun triggerWarning() {
        warningTriggered = true
    }
    
    override fun triggerAlarm() {
        alarmTriggered = true
    }
    
    override fun stopAlarm() {
        alarmStopped = true
    }
}
