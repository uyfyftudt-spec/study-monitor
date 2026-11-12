package com.example.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RealInactivityDetector(
    private val activityMonitor: ActivityMonitor,
    private val coroutineScope: CoroutineScope,
    private val timeProvider: TimeProvider = DefaultTimeProvider()
) : InactivityDetector {
    
    private val _inactivityEvents = MutableSharedFlow<InactivityLevel>(extraBufferCapacity = 1)
    override val inactivityEvents: Flow<InactivityLevel> = _inactivityEvents
    
    private var thresholdMs: Long = 10 * 60 * 1000L
    private var detectionJob: Job? = null
    private var lastActivityTime: Long = timeProvider.currentTimeMillis()
    private var isInactive = false
    
    override fun setThreshold(minutes: Int) {
        thresholdMs = minutes * 60 * 1000L
    }
    
    override fun startDetecting() {
        lastActivityTime = timeProvider.currentTimeMillis()
        isInactive = false
        detectionJob?.cancel()
        detectionJob = coroutineScope.launch {
            launch {
                activityMonitor.activityStream.collect {
                    lastActivityTime = timeProvider.currentTimeMillis()
                    if (isInactive) {
                        isInactive = false
                        _inactivityEvents.tryEmit(InactivityLevel.NORMAL)
                    }
                }
            }
            launch {
                while (isActive) {
                    delay(1000)
                    if (!isInactive) {
                        val elapsed = timeProvider.currentTimeMillis() - lastActivityTime
                        if (elapsed >= thresholdMs) {
                            isInactive = true
                            _inactivityEvents.tryEmit(InactivityLevel.WARNING)
                        }
                    }
                }
            }
        }
    }
    
    override fun stopDetecting() {
        detectionJob?.cancel()
    }
}
