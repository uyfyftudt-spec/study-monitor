package com.example.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.StudyMonitorApplication

class StudyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("StudyMonitor", "ACCESSIBILITY_SERVICE_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkgName = event.packageName?.toString()
            val className = event.className?.toString()
            Log.d("StudyMonitor", "WINDOW_STATE_CHANGED: pkg=$pkgName, class=$className")

            if (isJNotesPackage(pkgName)) {
                Log.d("StudyMonitor", "JNOTES_DETECTED_IN_FOREGROUND: pkg=$pkgName")
                handleJNotesForeground()
            }
        }

        if (event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
            Log.d("StudyMonitor", "TOUCH_INTERACTION_START")
        } else if (event.eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) {
            Log.d("StudyMonitor", "TOUCH_INTERACTION_END")
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_END -> {
                Log.d("StudyMonitor", "USER_ACTIVITY_DETECTED")
                notifyActivity()
            }
        }
    }

    private fun isJNotesPackage(pkgName: String?): Boolean {
        if (pkgName == null) return false
        val lower = pkgName.lowercase()
        return lower.contains("jnotes") || 
               lower.contains("jideos") || 
               lower == "com.jideos.jnotes.overseas.google" ||
               lower == "com.jideos.jnotes"
    }

    private fun handleJNotesForeground() {
        val app = application as? StudyMonitorApplication ?: return
        val currentStatus = app.sessionManager.sessionState.value
        // Only start if not already active to avoid duplicate sessions, duplicate timers, or resets
        if (currentStatus == com.example.model.SessionStatus.IDLE || 
            currentStatus == com.example.model.SessionStatus.COMPLETED) {
            Log.d("StudyMonitor", "AUTO_STARTING_STUDY_SESSION_FOR_JNOTES")
            app.sessionManager.startSession()
        }
    }

    private fun notifyActivity() {
        val app = application as? StudyMonitorApplication
        if (app != null) {
            Log.d("StudyMonitor", "LAST_ACTIVITY_UPDATED")
            app.activityMonitor.notifyUserActivity()
        }
    }

    override fun onInterrupt() {
        Log.d("StudyMonitor", "ACCESSIBILITY_SERVICE_INTERRUPTED")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("StudyMonitor", "ACCESSIBILITY_SERVICE_DISABLED")
    }
}
