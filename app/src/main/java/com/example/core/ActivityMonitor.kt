package com.example.core

import kotlinx.coroutines.flow.Flow

enum class ActivityType {
    ACTIVE,
    IDLE
}

interface ActivityMonitor {
    val activityStream: Flow<ActivityType>
    
    fun startMonitoring()
    fun stopMonitoring()
}
