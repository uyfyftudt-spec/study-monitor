package com.example.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CompletedTree
import com.example.model.StudySession
import com.example.model.TreeGrowthStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.forestDataStore by preferencesDataStore(name = "forest_data")

class RealForestRepository(private val context: Context) : ForestRepository {

    private val COMPLETED_TREES_KEY = stringPreferencesKey("completed_trees_json")
    private val AWARDED_SESSIONS_KEY = stringSetPreferencesKey("awarded_session_ids")
    private val CURRENT_TREE_SECONDS_KEY = longPreferencesKey("current_tree_seconds")
    private val CURRENT_TREE_SESSION_KEY = stringPreferencesKey("current_tree_session_id")

    override val completedTrees: Flow<List<CompletedTree>> = context.forestDataStore.data
        .map { preferences ->
            parseTreesJson(preferences[COMPLETED_TREES_KEY])
        }

    override val currentTreeActiveSeconds: Flow<Long> = context.forestDataStore.data
        .map { preferences ->
            preferences[CURRENT_TREE_SECONDS_KEY] ?: 0L
        }

    override val awardedSessionIds: Flow<Set<String>> = context.forestDataStore.data
        .map { preferences ->
            preferences[AWARDED_SESSIONS_KEY] ?: emptySet()
        }

    override suspend fun saveCurrentTreeProgress(sessionId: String, activeSeconds: Long) {
        context.forestDataStore.edit { preferences ->
            preferences[CURRENT_TREE_SESSION_KEY] = sessionId
            preferences[CURRENT_TREE_SECONDS_KEY] = activeSeconds
        }
    }

    override suspend fun clearCurrentTreeProgress() {
        context.forestDataStore.edit { preferences ->
            preferences.remove(CURRENT_TREE_SESSION_KEY)
            preferences[CURRENT_TREE_SECONDS_KEY] = 0L
        }
    }

    override suspend fun awardCompletedTree(session: StudySession): Boolean {
        // Only award if user accumulated study time
        if (session.activeStudyTimeSeconds <= 0L) {
            return false
        }

        var awarded = false
        context.forestDataStore.edit { preferences ->
            val awardedIds = preferences[AWARDED_SESSIONS_KEY] ?: emptySet()
            if (awardedIds.contains(session.id)) {
                // Prevent duplicate rewards if completion event is triggered more than once
                awarded = false
                return@edit
            }

            val currentTrees = parseTreesJson(preferences[COMPLETED_TREES_KEY]).toMutableList()
            val stage = TreeGrowthStage.fromActiveSeconds(session.activeStudyTimeSeconds)
            
            val newTree = CompletedTree(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                timestamp = System.currentTimeMillis(),
                studyDurationSeconds = session.activeStudyTimeSeconds,
                stageReached = stage.stageName
            )

            currentTrees.add(0, newTree) // newest first
            preferences[COMPLETED_TREES_KEY] = serializeTreesJson(currentTrees)
            preferences[AWARDED_SESSIONS_KEY] = awardedIds + session.id
            preferences[CURRENT_TREE_SECONDS_KEY] = 0L
            preferences.remove(CURRENT_TREE_SESSION_KEY)
            awarded = true
        }

        return awarded
    }

    override suspend fun resetForestData() {
        context.forestDataStore.edit { preferences ->
            preferences.remove(COMPLETED_TREES_KEY)
            preferences.remove(AWARDED_SESSIONS_KEY)
            preferences[CURRENT_TREE_SECONDS_KEY] = 0L
            preferences.remove(CURRENT_TREE_SESSION_KEY)
        }
    }

    private fun parseTreesJson(json: String?): List<CompletedTree> {
        if (json.isNullOrBlank()) return emptyList()
        val result = mutableListOf<CompletedTree>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    CompletedTree(
                        id = obj.optString("id", UUID.randomUUID().toString()),
                        sessionId = obj.optString("sessionId", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        studyDurationSeconds = obj.optLong("studyDurationSeconds", 0L),
                        stageReached = obj.optString("stageReached", TreeGrowthStage.SEEDLING.stageName)
                    )
                )
            }
        } catch (e: Exception) {
            // Return empty list on parse failure
        }
        return result
    }

    private fun serializeTreesJson(trees: List<CompletedTree>): String {
        val array = JSONArray()
        for (tree in trees) {
            val obj = JSONObject().apply {
                put("id", tree.id)
                put("sessionId", tree.sessionId)
                put("timestamp", tree.timestamp)
                put("studyDurationSeconds", tree.studyDurationSeconds)
                put("stageReached", tree.stageReached)
            }
            array.put(obj)
        }
        return array.toString()
    }
}
