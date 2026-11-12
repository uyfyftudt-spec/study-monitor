package com.example.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class AndroidActivityMonitor : ActivityMonitor {
    private val _activityStream = MutableSharedFlow<ActivityType>(extraBufferCapacity = 1)
    override val activityStream: Flow<ActivityType> = _activityStream
    
    override fun startMonitoring() {
        // In this phase, we rely on compose UI events being reported manually via notifyUserActivity
    }
    
    override fun stopMonitoring() {
        // No-op for manual compose reporting
    }
    
    fun notifyUserActivity() {
        _activityStream.tryEmit(ActivityType.ACTIVE)
    }
}
