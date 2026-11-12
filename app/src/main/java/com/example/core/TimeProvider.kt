package com.example.core

interface TimeProvider {
    fun currentTimeMillis(): Long
}

class DefaultTimeProvider : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
