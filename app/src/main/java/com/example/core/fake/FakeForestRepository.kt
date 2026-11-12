package com.example.core.fake

import com.example.core.ForestRepository
import com.example.model.CompletedTree
import com.example.model.StudySession
import com.example.model.TreeGrowthStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class FakeForestRepository : ForestRepository {

    private val _completedTrees = MutableStateFlow<List<CompletedTree>>(emptyList())
    override val completedTrees: Flow<List<CompletedTree>> = _completedTrees.asStateFlow()

    private val _currentTreeActiveSeconds = MutableStateFlow(0L)
    override val currentTreeActiveSeconds: Flow<Long> = _currentTreeActiveSeconds.asStateFlow()

    private val _awardedSessionIds = MutableStateFlow<Set<String>>(emptySet())
    override val awardedSessionIds: Flow<Set<String>> = _awardedSessionIds.asStateFlow()

    override suspend fun saveCurrentTreeProgress(sessionId: String, activeSeconds: Long) {
        _currentTreeActiveSeconds.value = activeSeconds
    }

    override suspend fun clearCurrentTreeProgress() {
        _currentTreeActiveSeconds.value = 0L
    }

    override suspend fun awardCompletedTree(session: StudySession): Boolean {
        if (session.activeStudyTimeSeconds <= 0L) {
            return false
        }

        if (_awardedSessionIds.value.contains(session.id)) {
            return false
        }

        val stage = TreeGrowthStage.fromActiveSeconds(session.activeStudyTimeSeconds)
        val newTree = CompletedTree(
            id = UUID.randomUUID().toString(),
            sessionId = session.id,
            timestamp = System.currentTimeMillis(),
            studyDurationSeconds = session.activeStudyTimeSeconds,
            stageReached = stage.stageName
        )

        _completedTrees.value = listOf(newTree) + _completedTrees.value
        _awardedSessionIds.value = _awardedSessionIds.value + session.id
        _currentTreeActiveSeconds.value = 0L
        return true
    }

    override suspend fun resetForestData() {
        _completedTrees.value = emptyList()
        _awardedSessionIds.value = emptySet()
        _currentTreeActiveSeconds.value = 0L
    }
}
