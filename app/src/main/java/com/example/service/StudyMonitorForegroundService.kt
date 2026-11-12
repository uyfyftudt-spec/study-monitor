package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.StudyMonitorApplication
import com.example.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class StudyMonitorForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var isServiceRunning = false

    companion object {
        const val CHANNEL_ID = "StudyMonitorSessionChannel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP_SESSION = "com.example.ACTION_STOP_SESSION"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SESSION) {
            (application as StudyMonitorApplication).sessionManager.endSession()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!isServiceRunning) {
            isServiceRunning = true
            startForeground(NOTIFICATION_ID, createNotification("Starting session...", "00:00:00"))
            observeSessionState()
        }

        return START_STICKY
    }

    private fun observeSessionState() {
        val app = application as StudyMonitorApplication
        val sessionManager = app.sessionManager
        val floatingTimerManager = app.floatingTimerManager
        
        serviceScope.launch {
            combine(sessionManager.sessionState, sessionManager.elapsedStudyTime, floatingTimerManager.isShowing) { state, time, isShowing ->
                Triple(state, time, isShowing)
            }.collect { (state, time, isShowing) ->
                if ((state == SessionStatus.IDLE || state == SessionStatus.COMPLETED) && !isShowing) {
                    isServiceRunning = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    val statusText = when (state) {
                        SessionStatus.STUDYING -> "Studying"
                        SessionStatus.PAUSED -> "Paused"
                        SessionStatus.INACTIVE -> "Inactive"
                        else -> if (isShowing) "Floating Timer Active" else "Unknown"
                    }
                    val timeString = if (state == SessionStatus.IDLE || state == SessionStatus.COMPLETED) "--:--" else formatTime(time)
                    updateNotification(statusText, timeString)
                }
            }
        }
    }

    private fun formatTime(totalSeconds: Long): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun updateNotification(statusText: String, timeString: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(statusText, timeString))
    }

    private fun createNotification(statusText: String, timeString: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, StudyMonitorForegroundService::class.java).apply {
            action = ACTION_STOP_SESSION
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Study Monitor")
            .setContentText("$statusText — $timeString")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Session", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Study Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the study session active in the background"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
