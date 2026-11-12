package com.example.core

import android.content.Context
import android.content.Intent
import com.example.AlarmActivity
import com.example.StudyMonitorApplication

class RealAlarmSystem(
    private val context: Context
) : AlarmSystem {

    override fun triggerWarning() {
        // Warning not yet fully utilized in Part 3, could vibrate briefly
    }

    override fun triggerAlarm() {
        // Reset the Overlay Timer to 00:00 immediately at the exact moment the alarm starts
        (context.applicationContext as? StudyMonitorApplication)?.floatingTimerManager?.resetTimer()

        // Launch AlarmActivity, which will handle playing sound, vibrating, and waking screen
        val intent = Intent(context, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    override fun stopAlarm() {
        // Broadcast to AlarmActivity to finish itself, or AlarmActivity will stop itself when buttons are pressed.
        // It's cleaner to handle stopping inside AlarmActivity based on user input, 
        // but if stopped from outside, we can use a broadcast.
        val intent = Intent("com.example.ACTION_STOP_ALARM").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
