package com.example.core

import kotlinx.coroutines.flow.Flow

interface InactivityDetector {
    val inactivityEvents: Flow<InactivityLevel>
    
    fun setThreshold(minutes: Int)
    fun startDetecting()
    fun stopDetecting()
}

enum class InactivityLevel {
    NORMAL,
    WARNING,
    CRITICAL
}
