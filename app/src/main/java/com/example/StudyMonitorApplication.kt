package com.example

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.example.core.AlarmSystem
import com.example.core.AndroidActivityMonitor
import com.example.core.AutoWakeUpManager
import com.example.core.DayLevelRepository
import com.example.core.FloatingTimerManager
import com.example.core.ForestRepository
import com.example.core.InactivityDetector
import com.example.core.RealAlarmSystem
import com.example.core.RealDayLevelRepository
import com.example.core.RealForestRepository
import com.example.core.RealInactivityDetector
import com.example.core.RealSessionManager
import com.example.core.RealSettingsRepository
import com.example.core.RealStatisticsRepository
import com.example.core.SessionManager
import com.example.core.SettingsRepository
import com.example.core.StatisticsRepository
import com.example.model.SessionStatus
import com.example.service.StudyMonitorForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class StudyMonitorApplication : Application() {
    lateinit var activityMonitor: AndroidActivityMonitor
    lateinit var inactivityDetector: InactivityDetector
    lateinit var settingsRepository: SettingsRepository
    lateinit var statisticsRepository: StatisticsRepository
    lateinit var sessionManager: SessionManager
    lateinit var alarmSystem: AlarmSystem
    lateinit var floatingTimerManager: FloatingTimerManager
    lateinit var forestRepository: ForestRepository
    lateinit var autoWakeUpManager: AutoWakeUpManager
    lateinit var dayLevelRepository: DayLevelRepository

    var isAppInForeground: Boolean = false
        private set
    private var visibleActivityCount = 0
    
    override fun onCreate() {
        super.onCreate()
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        
        floatingTimerManager = FloatingTimerManager(this)
        
        activityMonitor = AndroidActivityMonitor()
        settingsRepository = RealSettingsRepository(this)
        statisticsRepository = RealStatisticsRepository()
        forestRepository = RealForestRepository(this)
        alarmSystem = RealAlarmSystem(this)
        dayLevelRepository = RealDayLevelRepository(this, appScope)
        
        inactivityDetector = RealInactivityDetector(activityMonitor, appScope)
        
        sessionManager = RealSessionManager(
            activityMonitor = activityMonitor,
            inactivityDetector = inactivityDetector,
            settingsRepository = settingsRepository,
            statisticsRepository = statisticsRepository,
            alarmSystem = alarmSystem,
            coroutineScope = appScope,
            forestRepository = forestRepository,
            dayLevelRepository = dayLevelRepository
        )

        autoWakeUpManager = AutoWakeUpManager(
            context = this,
            settingsRepository = settingsRepository,
            coroutineScope = appScope
        )

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                visibleActivityCount++
                isAppInForeground = true
            }

            override fun onActivityStopped(activity: Activity) {
                visibleActivityCount--
                if (visibleActivityCount <= 0) {
                    visibleActivityCount = 0
                    isAppInForeground = false
                    autoWakeUpManager.onAppClosedOrMonitoringStopped(sessionManager.sessionState.value)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        appScope.launch {
            sessionManager.sessionState.collect { state ->
                if (state == SessionStatus.STUDYING) {
                    floatingTimerManager.showTimer()
                    autoWakeUpManager.onMonitoringStarted()
                } else if (state == SessionStatus.COMPLETED || state == SessionStatus.IDLE) {
                    floatingTimerManager.hideTimer()
                    if (!isAppInForeground) {
                        autoWakeUpManager.onAppClosedOrMonitoringStopped(state)
                    }
                }
            }
        }

        appScope.launch {
            combine(sessionManager.sessionState, floatingTimerManager.isShowing) { state, isShowing ->
                Pair(state, isShowing)
            }.collect { (state, isShowing) ->
                if (state == SessionStatus.STUDYING || isShowing) {
                    val intent = Intent(this@StudyMonitorApplication, StudyMonitorForegroundService::class.java)
                    ContextCompat.startForegroundService(this@StudyMonitorApplication, intent)
                }
            }
        }
    }
}
