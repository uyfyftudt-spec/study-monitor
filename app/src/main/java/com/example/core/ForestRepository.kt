package com.example.core

import com.example.model.CompletedTree
import com.example.model.StudySession
import kotlinx.coroutines.flow.Flow

interface ForestRepository {
    val completedTrees: Flow<List<CompletedTree>>
    val currentTreeActiveSeconds: Flow<Long>
    val awardedSessionIds: Flow<Set<String>>

    suspend fun saveCurrentTreeProgress(sessionId: String, activeSeconds: Long)
    suspend fun awardCompletedTree(session: StudySession): Boolean
    suspend fun resetForestData()
    suspend fun clearCurrentTreeProgress()
}
