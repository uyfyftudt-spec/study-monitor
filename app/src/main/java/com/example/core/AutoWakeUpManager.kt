package com.example.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.AlarmActivity
import com.example.R
import com.example.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class AutoWakeUpManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        const val TAG = "AutoWakeUpManager"
        const val ACTION_TRIGGER_ALARM = "com.example.ACTION_AUTO_WAKEUP_ALARM"
        const val NOTIFICATION_CHANNEL_ID = "AutoWakeUpChannel"
        const val NOTIFICATION_ID = 2002
        const val REQUEST_CODE_ALARM = 3001
        const val REQUEST_CODE_UI = 3002

        const val INACTIVITY_COUNTDOWN_MS = 3 * 60 * 60 * 1000L // 3 hours
        const val RETRY_INTERVAL_MS = 10 * 60 * 1000L           // 10 minutes
        const val MAX_ATTEMPTS = 4

        @Volatile
        var isAlarmActive: Boolean = false
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    init {
        createNotificationChannel()
        coroutineScope.launch {
            settingsRepository.autoWakeUpEnabled.collect { enabled ->
                if (!enabled) {
                    cancelPendingAlarm()
                    stopActiveAlarmIfRinging()
                    settingsRepository.setAutoWakeUpTargetTimeMs(0L)
                    settingsRepository.setAutoWakeUpAttemptCount(0)
                }
            }
        }
    }

    fun onAppClosedOrMonitoringStopped(sessionStatus: SessionStatus) {
        if (sessionStatus == SessionStatus.STUDYING) {
            return
        }
        coroutineScope.launch {
            val enabled = settingsRepository.autoWakeUpEnabled.firstOrNull() ?: false
            if (!enabled) return@launch

            val currentAttempt = settingsRepository.autoWakeUpAttemptCount.firstOrNull() ?: 0
            val targetTime = settingsRepository.autoWakeUpTargetTimeMs.firstOrNull() ?: 0L
            val now = System.currentTimeMillis()

            // If already in an active retry cycle (1..4) with a pending future target, keep it
            if (currentAttempt in 1..MAX_ATTEMPTS && targetTime > now) {
                Log.d(TAG, "Keeping active attempt $currentAttempt scheduled at $targetTime")
                return@launch
            }

            // Start fresh 3-hour countdown
            val nextTargetTime = now + INACTIVITY_COUNTDOWN_MS
            settingsRepository.setAutoWakeUpTargetTimeMs(nextTargetTime)
            settingsRepository.setAutoWakeUpAttemptCount(0)
            scheduleAlarm(nextTargetTime, attempt = 1)
            Log.d(TAG, "Scheduled 3-hour auto wake-up countdown for $nextTargetTime")
        }
    }

    fun onMonitoringStarted() {
        coroutineScope.launch {
            val enabled = settingsRepository.autoWakeUpEnabled.firstOrNull() ?: false
            if (enabled) {
                cancelPendingAlarm()
                settingsRepository.setAutoWakeUpTargetTimeMs(0L)
                settingsRepository.setAutoWakeUpAttemptCount(0)
                Log.d(TAG, "Monitoring started: cancelled auto wake-up countdown")
            }
        }
    }

    fun scheduleAlarm(triggerTimeMs: Long, attempt: Int) {
        val intent = Intent(context, AutoWakeUpReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
            putExtra(AlarmActivity.EXTRA_ATTEMPT_COUNT, attempt)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val showIntent = Intent(context, AlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(AlarmActivity.EXTRA_AUTO_WAKEUP, true)
                    putExtra(AlarmActivity.EXTRA_ATTEMPT_COUNT, attempt)
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    REQUEST_CODE_UI,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val clockInfo = AlarmManager.AlarmClockInfo(triggerTimeMs, showPendingIntent)
                alarmManager.setAlarmClock(clockInfo, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm via setAlarmClock, falling back", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
                }
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Failed fallback scheduling", fallbackEx)
            }
        }
    }

    fun cancelPendingAlarm() {
        val intent = Intent(context, AutoWakeUpReceiver::class.java).apply {
            action = ACTION_TRIGGER_ALARM
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_ALARM,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun stopActiveAlarmIfRinging() {
        if (isAlarmActive) {
            isAlarmActive = false
            val intent = Intent("com.example.ACTION_STOP_ALARM").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
        dismissNotification()
    }

    fun onAwakeConfirmed(confirmedAttempt: Int) {
        isAlarmActive = false
        dismissNotification()
        coroutineScope.launch {
            if (confirmedAttempt < MAX_ATTEMPTS) {
                // Schedule next check 10 minutes later
                val nextAttempt = confirmedAttempt + 1
                val nextTriggerTime = System.currentTimeMillis() + RETRY_INTERVAL_MS
                settingsRepository.setAutoWakeUpAttemptCount(nextAttempt)
                settingsRepository.setAutoWakeUpTargetTimeMs(nextTriggerTime)
                scheduleAlarm(nextTriggerTime, nextAttempt)
                Log.d(TAG, "Attempt $confirmedAttempt confirmed. Scheduled attempt $nextAttempt in 10 minutes")
            } else {
                // All 4 attempts finished! Reset and schedule next 3-hour cycle
                val nextCycleTarget = System.currentTimeMillis() + INACTIVITY_COUNTDOWN_MS
                settingsRepository.setAutoWakeUpAttemptCount(0)
                settingsRepository.setAutoWakeUpTargetTimeMs(nextCycleTarget)
                scheduleAlarm(nextCycleTarget, attempt = 1)
                Log.d(TAG, "All 4 attempts completed. Scheduled next 3-hour countdown")
            }
        }
    }

    fun triggerAlarmNow(attempt: Int) {
        if (isAlarmActive) {
            Log.d(TAG, "Alarm already active, ignoring duplicate trigger")
            return
        }
        isAlarmActive = true

        val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AlarmActivity.EXTRA_AUTO_WAKEUP, true)
            putExtra(AlarmActivity.EXTRA_ATTEMPT_COUNT, attempt)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_UI,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Auto Wake-Up Monitor")
            .setContentText("Wake-Up Check (Attempt $attempt of $MAX_ATTEMPTS)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)

        try {
            context.startActivity(alarmIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not start AlarmActivity directly from background", e)
        }
    }

    fun dismissNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Auto Wake-Up Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority wake-up alarms after inactivity"
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
