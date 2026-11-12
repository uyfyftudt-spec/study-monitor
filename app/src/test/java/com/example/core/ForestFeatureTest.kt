package com.example.core

import com.example.core.fake.FakeForestRepository
import com.example.model.SessionStatus
import com.example.model.StudySession
import com.example.model.TreeGrowthStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ForestFeatureTest {

    private lateinit var sessionManager: RealSessionManager
    private lateinit var activityMonitor: FakeActivityMonitor
    private lateinit var inactivityDetector: RealInactivityDetector
    private lateinit var settingsRepository: FakeSettingsRepository
    private lateinit var statisticsRepository: FakeStatisticsRepository
    private lateinit var forestRepository: FakeForestRepository
    private lateinit var alarmSystem: FakeAlarmSystem
    private lateinit var testTimeProvider: TestTimeProvider
    private lateinit var testScope: TestScope
    private lateinit var managerScope: CoroutineScope

    @Before
    fun setup() {
        val testDispatcher = StandardTestDispatcher()
        testScope = TestScope(testDispatcher)
        managerScope = CoroutineScope(testScope.coroutineContext + kotlinx.coroutines.Job())
        testTimeProvider = TestTimeProvider(testScope)

        activityMonitor = FakeActivityMonitor()
        settingsRepository = FakeSettingsRepository()
        statisticsRepository = FakeStatisticsRepository()
        forestRepository = FakeForestRepository()
        alarmSystem = FakeAlarmSystem()

        inactivityDetector = RealInactivityDetector(activityMonitor, managerScope, testTimeProvider)

        sessionManager = RealSessionManager(
            activityMonitor = activityMonitor,
            inactivityDetector = inactivityDetector,
            settingsRepository = settingsRepository,
            statisticsRepository = statisticsRepository,
            alarmSystem = alarmSystem,
            coroutineScope = managerScope,
            timeProvider = testTimeProvider,
            forestRepository = forestRepository
        )
    }

    @After
    fun tearDown() {
        managerScope.cancel()
    }

    @Test
    fun testTreeGrowthStagesCalculation() {
        assertEquals(TreeGrowthStage.SEEDLING, TreeGrowthStage.fromActiveSeconds(0L))
        assertEquals(TreeGrowthStage.SEEDLING, TreeGrowthStage.fromActiveSeconds(299L))
        assertEquals(TreeGrowthStage.SAPLING, TreeGrowthStage.fromActiveSeconds(300L))
        assertEquals(TreeGrowthStage.SAPLING, TreeGrowthStage.fromActiveSeconds(899L))
        assertEquals(TreeGrowthStage.YOUNG_TREE, TreeGrowthStage.fromActiveSeconds(900L))
        assertEquals(TreeGrowthStage.YOUNG_TREE, TreeGrowthStage.fromActiveSeconds(1499L))
        assertEquals(TreeGrowthStage.BLOOMING_TREE, TreeGrowthStage.fromActiveSeconds(1500L))
        assertEquals(TreeGrowthStage.BLOOMING_TREE, TreeGrowthStage.fromActiveSeconds(2399L))
        assertEquals(TreeGrowthStage.MATURE_TREE, TreeGrowthStage.fromActiveSeconds(2400L))
        assertEquals(TreeGrowthStage.MATURE_TREE, TreeGrowthStage.fromActiveSeconds(5000L))
    }

    @Test
    fun testSessionCompletionAwardsExactlyOneTree() = testScope.runTest {
        sessionManager.startSession()
        advanceTimeBy(350_000) // Study for ~350 seconds

        sessionManager.endSession()
        testScope.advanceUntilIdle()

        val completed = forestRepository.completedTrees.first()
        assertEquals(1, completed.size)
        assertEquals(TreeGrowthStage.SAPLING.stageName, completed[0].stageReached)
        assertTrue(completed[0].studyDurationSeconds in 340L..360L)
    }

    @Test
    fun testDuplicateCompletionDoesNotAwardMultipleTrees() = testScope.runTest {
        val testSession = StudySession(
            id = UUID.randomUUID().toString(),
            startTime = LocalDateTime.now(),
            activeStudyTimeSeconds = 600L,
            status = SessionStatus.COMPLETED
        )

        val firstAward = forestRepository.awardCompletedTree(testSession)
        assertTrue(firstAward)

        // Attempting to award again for the same session ID
        val secondAward = forestRepository.awardCompletedTree(testSession)
        assertFalse(secondAward)

        val completed = forestRepository.completedTrees.first()
        assertEquals(1, completed.size)
    }

    @Test
    fun testZeroStudyTimeDoesNotAwardTree() = testScope.runTest {
        val zeroSession = StudySession(
            id = UUID.randomUUID().toString(),
            startTime = LocalDateTime.now(),
            activeStudyTimeSeconds = 0L,
            status = SessionStatus.COMPLETED
        )

        val awarded = forestRepository.awardCompletedTree(zeroSession)
        assertFalse(awarded)

        val completed = forestRepository.completedTrees.first()
        assertEquals(0, completed.size)
    }

    @Test
    fun testTemporaryPauseDoesNotDestroyTreeProgress() = testScope.runTest {
        sessionManager.startSession()
        advanceTimeBy(400_000) // ~400 seconds of active study

        sessionManager.pauseSession()
        advanceTimeBy(100_000) // Paused for 100 seconds

        assertEquals(SessionStatus.PAUSED, sessionManager.sessionState.value)
        val activeBeforeResume = sessionManager.currentSession.value?.activeStudyTimeSeconds ?: 0L
        assertTrue(activeBeforeResume in 390L..410L)

        sessionManager.resumeSession()
        advanceTimeBy(600_000) // ~600 more seconds

        val activeAfterResume = sessionManager.currentSession.value?.activeStudyTimeSeconds ?: 0L
        assertTrue(activeAfterResume in 990L..1010L)
        assertEquals(TreeGrowthStage.YOUNG_TREE, TreeGrowthStage.fromActiveSeconds(activeAfterResume))

        sessionManager.endSession()
        testScope.advanceUntilIdle()

        val completed = forestRepository.completedTrees.first()
        assertEquals(1, completed.size)
        assertEquals(TreeGrowthStage.YOUNG_TREE.stageName, completed[0].stageReached)
        assertTrue(completed[0].studyDurationSeconds in 990L..1010L)
    }

    @Test
    fun testResetForestDataClearsTreesAndAwards() = testScope.runTest {
        val testSession = StudySession(
            id = UUID.randomUUID().toString(),
            startTime = LocalDateTime.now(),
            activeStudyTimeSeconds = 500L,
            status = SessionStatus.COMPLETED
        )

        forestRepository.awardCompletedTree(testSession)
        assertEquals(1, forestRepository.completedTrees.first().size)

        forestRepository.resetForestData()
        assertEquals(0, forestRepository.completedTrees.first().size)
        assertEquals(0, forestRepository.awardedSessionIds.first().size)
    }
}
