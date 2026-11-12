package com.example.model

enum class DayLevel(
    val arabicName: String,
    val defaultGoalHours: Float,
    val defaultThresholdMinutes: Int
) {
    NORMAL(
        arabicName = "عادي",
        defaultGoalHours = 4.0f,
        defaultThresholdMinutes = 10
    ),
    INTENSIVE(
        arabicName = "مكثف",
        defaultGoalHours = 6.0f,
        defaultThresholdMinutes = 7
    ),
    WAR(
        arabicName = "حرب",
        defaultGoalHours = 8.0f,
        defaultThresholdMinutes = 5
    ),
    REST(
        arabicName = "راحة",
        defaultGoalHours = 2.0f,
        defaultThresholdMinutes = 15
    )
}

data class DayLevelConfig(
    val goalHours: Float,
    val thresholdMinutes: Int
)
