package com.example.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.AlarmActivity
import com.example.StudyMonitorApplication
import com.example.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AutoWakeUpReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutoWakeUpManager.ACTION_TRIGGER_ALARM) return

        val app = context.applicationContext as? StudyMonitorApplication ?: return
        val autoWakeUpManager = app.autoWakeUpManager
        val settingsRepository = app.settingsRepository
        val sessionManager = app.sessionManager

        val attempt = intent.getIntExtra(AlarmActivity.EXTRA_ATTEMPT_COUNT, 1)

        CoroutineScope(Dispatchers.Default).launch {
            val isEnabled = settingsRepository.autoWakeUpEnabled.firstOrNull() ?: false
            if (!isEnabled) {
                Log.d("AutoWakeUpReceiver", "Auto wake-up disabled, skipping alarm")
                return@launch
            }

            // If user is actively studying, do not trigger auto wake-up
            if (sessionManager.sessionState.value == SessionStatus.STUDYING) {
                Log.d("AutoWakeUpReceiver", "User is studying, skipping auto wake-up")
                return@launch
            }

            Log.d("AutoWakeUpReceiver", "Triggering auto wake-up alarm attempt $attempt")
            autoWakeUpManager.triggerAlarmNow(attempt)
        }
    }
}
