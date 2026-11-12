package com.example.model

enum class TreeGrowthStage(
    val stageNumber: Int,
    val stageName: String,
    val requiredSeconds: Long,
    val description: String
) {
    SEEDLING(
        stageNumber = 1,
        stageName = "Seedling",
        requiredSeconds = 0L,
        description = "A tender sprout emerging from the fertile soil."
    ),
    SAPLING(
        stageNumber = 2,
        stageName = "Sapling",
        requiredSeconds = 300L, // 5 minutes
        description = "A young green stem branching out with lively fresh leaves."
    ),
    YOUNG_TREE(
        stageNumber = 3,
        stageName = "Young Tree",
        requiredSeconds = 900L, // 15 minutes
        description = "A sturdy growing trunk with vibrant leafy branches."
    ),
    BLOOMING_TREE(
        stageNumber = 4,
        stageName = "Blooming Tree",
        requiredSeconds = 1500L, // 25 minutes
        description = "A flourishing tree adorned with blossoming flowers."
    ),
    MATURE_TREE(
        stageNumber = 5,
        stageName = "Mature Tree",
        requiredSeconds = 2400L, // 40 minutes
        description = "A magnificent, full-canopied forest tree in full majesty."
    );

    companion object {
        fun fromActiveSeconds(seconds: Long): TreeGrowthStage {
            val stages = values().sortedByDescending { it.requiredSeconds }
            return stages.firstOrNull { seconds >= it.requiredSeconds } ?: SEEDLING
        }

        fun nextStage(current: TreeGrowthStage): TreeGrowthStage? {
            val nextIndex = current.ordinal + 1
            return if (nextIndex < values().size) values()[nextIndex] else null
        }
    }
}

data class CompletedTree(
    val id: String,
    val sessionId: String,
    val timestamp: Long,
    val studyDurationSeconds: Long,
    val stageReached: String
)
