package com.example.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.fake.FakeSettingsRepository
import com.example.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AutoWakeUpManagerTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var testScope: TestScope
    private lateinit var managerScope: CoroutineScope
    private lateinit var manager: AutoWakeUpManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsRepository = FakeSettingsRepository()
        val dispatcher = StandardTestDispatcher()
        testScope = TestScope(dispatcher)
        managerScope = CoroutineScope(testScope.coroutineContext + Job())

        manager = AutoWakeUpManager(
            context = context,
            settingsRepository = settingsRepository,
            coroutineScope = managerScope
        )
    }

    @After
    fun tearDown() {
        managerScope.cancel()
    }

    @Test
    fun `when enabled and app closed, schedules 3-hour countdown`() = testScope.runTest {
        settingsRepository.setAutoWakeUpEnabled(true)
        advanceUntilIdle()

        manager.onAppClosedOrMonitoringStopped(SessionStatus.IDLE)
        advanceUntilIdle()

        val targetTime = settingsRepository.autoWakeUpTargetTimeMs.first()
        val attemptCount = settingsRepository.autoWakeUpAttemptCount.first()

        assertTrue(targetTime > System.currentTimeMillis())
        assertEquals(0, attemptCount)
    }

    @Test
    fun `confirming attempt 1 schedules attempt 2 in 10 minutes`() = testScope.runTest {
        settingsRepository.setAutoWakeUpEnabled(true)
        advanceUntilIdle()

        val before = System.currentTimeMillis()
        manager.onAwakeConfirmed(confirmedAttempt = 1)
        advanceUntilIdle()

        val nextAttempt = settingsRepository.autoWakeUpAttemptCount.first()
        val nextTarget = settingsRepository.autoWakeUpTargetTimeMs.first()

        assertEquals(2, nextAttempt)
        assertTrue(nextTarget >= before + 10 * 60 * 1000L - 1000L)
    }

    @Test
    fun `confirming attempt 4 finishes cycle and restarts 3-hour countdown`() = testScope.runTest {
        settingsRepository.setAutoWakeUpEnabled(true)
        advanceUntilIdle()

        val before = System.currentTimeMillis()
        manager.onAwakeConfirmed(confirmedAttempt = 4)
        advanceUntilIdle()

        val nextAttempt = settingsRepository.autoWakeUpAttemptCount.first()
        val nextTarget = settingsRepository.autoWakeUpTargetTimeMs.first()

        assertEquals(0, nextAttempt)
        assertTrue(nextTarget >= before + 3 * 3600 * 1000L - 1000L)
    }

    @Test
    fun `disabling feature cancels schedule and resets progress`() = testScope.runTest {
        settingsRepository.setAutoWakeUpEnabled(true)
        advanceUntilIdle()
        manager.onAppClosedOrMonitoringStopped(SessionStatus.IDLE)
        advanceUntilIdle()

        settingsRepository.setAutoWakeUpEnabled(false)
        advanceUntilIdle()

        val target = settingsRepository.autoWakeUpTargetTimeMs.first()
        val attempt = settingsRepository.autoWakeUpAttemptCount.first()

        assertEquals(0L, target)
        assertEquals(0, attempt)
    }

    @Test
    fun `starting study session cancels auto wake-up countdown`() = testScope.runTest {
        settingsRepository.setAutoWakeUpEnabled(true)
        advanceUntilIdle()
        manager.onAppClosedOrMonitoringStopped(SessionStatus.IDLE)
        advanceUntilIdle()

        manager.onMonitoringStarted()
        advanceUntilIdle()

        val target = settingsRepository.autoWakeUpTargetTimeMs.first()
        assertEquals(0L, target)
    }
}
