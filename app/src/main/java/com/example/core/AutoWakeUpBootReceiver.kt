package com.example.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.StudyMonitorApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AutoWakeUpBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        val app = context.applicationContext as? StudyMonitorApplication ?: return
        val settingsRepo = app.settingsRepository
        val autoWakeUpManager = app.autoWakeUpManager

        CoroutineScope(Dispatchers.Default).launch {
            val isEnabled = settingsRepo.autoWakeUpEnabled.firstOrNull() ?: false
            if (!isEnabled) return@launch

            val targetTime = settingsRepo.autoWakeUpTargetTimeMs.firstOrNull() ?: 0L
            val attempt = settingsRepo.autoWakeUpAttemptCount.firstOrNull() ?: 0
            val now = System.currentTimeMillis()

            if (targetTime > 0L) {
                val effectiveAttempt = if (attempt <= 0) 1 else attempt
                if (targetTime <= now) {
                    autoWakeUpManager.scheduleAlarm(now + 2000L, effectiveAttempt)
                } else {
                    autoWakeUpManager.scheduleAlarm(targetTime, effectiveAttempt)
                }
                Log.d("AutoWakeUpBootReceiver", "Rescheduled auto wake-up alarm after reboot for $targetTime")
            } else {
                val freshTarget = now + AutoWakeUpManager.INACTIVITY_COUNTDOWN_MS
                settingsRepo.setAutoWakeUpTargetTimeMs(freshTarget)
                settingsRepo.setAutoWakeUpAttemptCount(0)
                autoWakeUpManager.scheduleAlarm(freshTarget, attempt = 1)
                Log.d("AutoWakeUpBootReceiver", "Rescheduled fresh 3-hour auto wake-up after reboot")
            }
        }
    }
}
