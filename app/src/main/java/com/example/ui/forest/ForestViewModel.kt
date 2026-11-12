package com.example.ui.forest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.ForestRepository
import com.example.core.SessionManager
import com.example.model.CompletedTree
import com.example.model.SessionStatus
import com.example.model.TreeGrowthStage
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ForestUiState(
    val sessionStatus: SessionStatus = SessionStatus.IDLE,
    val activeStudySeconds: Long = 0L,
    val currentStage: TreeGrowthStage = TreeGrowthStage.SEEDLING,
    val nextStage: TreeGrowthStage? = TreeGrowthStage.SAPLING,
    val secondsToNextStage: Long = 300L,
    val progressToNextStage: Float = 0f,
    val completedTrees: List<CompletedTree> = emptyList()
)

class ForestViewModel(
    private val sessionManager: SessionManager,
    private val forestRepository: ForestRepository
) : ViewModel() {

    init {
        // Automatically save current tree active study progress for persistence
        viewModelScope.launch {
            sessionManager.currentSession.collect { session ->
                if (session != null && session.activeStudyTimeSeconds > 0) {
                    forestRepository.saveCurrentTreeProgress(
                        sessionId = session.id,
                        activeSeconds = session.activeStudyTimeSeconds
                    )
                }
            }
        }
    }

    val uiState: StateFlow<ForestUiState> = combine(
        sessionManager.sessionState,
        sessionManager.currentSession,
        forestRepository.completedTrees,
        forestRepository.currentTreeActiveSeconds
    ) { status, session, completedList, savedActiveSeconds ->
        // Use activeStudyTimeSeconds from ongoing session, or savedActiveSeconds if session was recovered
        val activeSeconds = if (session != null && (status == SessionStatus.STUDYING || status == SessionStatus.PAUSED || status == SessionStatus.INACTIVE)) {
            session.activeStudyTimeSeconds
        } else {
            savedActiveSeconds
        }

        val currentStage = TreeGrowthStage.fromActiveSeconds(activeSeconds)
        val nextStage = TreeGrowthStage.nextStage(currentStage)

        val secondsToNextStage = if (nextStage != null) {
            maxOf(0L, nextStage.requiredSeconds - activeSeconds)
        } else {
            0L
        }

        val progress = if (nextStage != null) {
            val stageSpan = (nextStage.requiredSeconds - currentStage.requiredSeconds).toFloat()
            if (stageSpan > 0f) {
                ((activeSeconds - currentStage.requiredSeconds).toFloat() / stageSpan).coerceIn(0f, 1f)
            } else 1f
        } else {
            1f
        }

        ForestUiState(
            sessionStatus = status,
            activeStudySeconds = activeSeconds,
            currentStage = currentStage,
            nextStage = nextStage,
            secondsToNextStage = secondsToNextStage,
            progressToNextStage = progress,
            completedTrees = completedList
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ForestUiState()
    )

    fun resetForestData() {
        viewModelScope.launch {
            forestRepository.resetForestData()
        }
    }
}
