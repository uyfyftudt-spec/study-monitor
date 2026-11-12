package com.example

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.core.SettingsRepository
import com.example.ui.theme.AppTheme
import com.example.ui.theme.SoftRed
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class AlarmActivity : ComponentActivity() {

    companion object {
        const val EXTRA_AUTO_WAKEUP = "extra_auto_wakeup"
        const val EXTRA_ATTEMPT_COUNT = "extra_attempt_count"
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var volumeJob: Job? = null
    private var vibrationJob: Job? = null

    private val stopAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.ACTION_STOP_ALARM") {
                stopAlarm()
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        ContextCompat.registerReceiver(
            this,
            stopAlarmReceiver,
            IntentFilter("com.example.ACTION_STOP_ALARM"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        
        turnScreenOnAndKeyguardOff()

        val sessionManager = (application as StudyMonitorApplication).sessionManager
        val settingsRepository = (application as StudyMonitorApplication).settingsRepository
        val isAutoWakeUp = intent.getBooleanExtra(EXTRA_AUTO_WAKEUP, false)
        val attemptCount = intent.getIntExtra(EXTRA_ATTEMPT_COUNT, 1)

        setContent {
            AppTheme {
                val examDateString by settingsRepository.examDate.collectAsState(initial = SettingsRepository.DEFAULT_EXAM_DATE)

                AlarmScreen(
                    examDateString = examDateString,
                    isAutoWakeUp = isAutoWakeUp,
                    attemptCount = attemptCount,
                    onAwake = {
                        stopAlarm()
                        if (isAutoWakeUp) {
                            (application as? StudyMonitorApplication)?.autoWakeUpManager?.onAwakeConfirmed(attemptCount)
                        } else {
                            sessionManager.resumeSession()
                        }
                        finish()
                    },
                    onEndSession = {
                        stopAlarm()
                        sessionManager.endSession()
                        (application as? StudyMonitorApplication)?.floatingTimerManager?.hideTimer()
                        finish()
                    }
                )
            }
        }

        lifecycleScope.launch {
            val customUriString = settingsRepository.alarmSoundUri.firstOrNull()
            startAlarm(customUriString)
        }
    }

    private fun turnScreenOnAndKeyguardOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun startAlarm(customUriString: String?) {
        val alarmUri = try {
            if (customUriString != null) {
                Uri.parse(customUriString)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
        } catch (e: Exception) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
        
        if (alarmUri != null) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(this@AlarmActivity, alarmUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                    prepare()
                    setVolume(0.15f, 0.15f)
                    start()
                }
                startGradualVolumeEscalation()
            } catch (e: Exception) {
                if (!customUriString.isNullOrEmpty()) {
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = null
                        val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        if (fallbackUri != null) {
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(this@AlarmActivity, fallbackUri)
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_ALARM)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                        .build()
                                )
                                isLooping = true
                                prepare()
                                setVolume(0.15f, 0.15f)
                                start()
                            }
                            startGradualVolumeEscalation()
                        }
                    } catch (fallbackE: Exception) {
                        // ignore fallback error
                    }
                }
            }
        }

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        startGradualVibrationEscalation()
    }

    private fun startGradualVolumeEscalation() {
        volumeJob?.cancel()
        volumeJob = lifecycleScope.launch {
            val steps = listOf(
                15_000L to 0.40f, // 15–30s
                15_000L to 0.70f, // 30–45s
                15_000L to 1.00f  // 45–60s+
            )

            for ((delayMs, vol) in steps) {
                delay(delayMs)
                if (!isActive) break
                try {
                    val player = mediaPlayer
                    if (player != null && player.isPlaying) {
                        player.setVolume(vol, vol)
                    } else {
                        break
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun startGradualVibrationEscalation() {
        vibrationJob?.cancel()
        vibrationJob = lifecycleScope.launch {
            val vib = vibrator ?: return@launch
            // 0–15s: gentle vibration (short pulse, long rest)
            applyVibrationPattern(vib, pulseMs = 150L, restMs = 850L)
            delay(15_000L)
            if (!isActive) return@launch

            // 15–30s: moderate vibration
            applyVibrationPattern(vib, pulseMs = 300L, restMs = 600L)
            delay(15_000L)
            if (!isActive) return@launch

            // 30–45s: strong vibration
            applyVibrationPattern(vib, pulseMs = 500L, restMs = 400L)
            delay(15_000L)
            if (!isActive) return@launch

            // 45s+: maximum intensity vibration (repeats until explicitly dismissed)
            applyVibrationPattern(vib, pulseMs = 800L, restMs = 200L)
        }
    }

    private fun applyVibrationPattern(vib: Vibrator, pulseMs: Long, restMs: Long) {
        try {
            vib.cancel()
            val pattern = longArrayOf(0, pulseMs, restMs)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(pattern, 0)
            }
        } catch (e: Exception) {}
    }

    private fun stopAlarm() {
        volumeJob?.cancel()
        volumeJob = null
        vibrationJob?.cancel()
        vibrationJob = null

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        
        try {
            vibrator?.cancel()
        } catch (e: Exception) {}
        vibrator = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
        (application as? StudyMonitorApplication)?.autoWakeUpManager?.dismissNotification()
        com.example.core.AutoWakeUpManager.isAlarmActive = false
        try {
            unregisterReceiver(stopAlarmReceiver)
        } catch (e: Exception) {}
    }
}

data class AuthenticHadith(
    val text: String,
    val source: String
)

val AUTHENTIC_HADITHS = listOf(
    AuthenticHadith(
        text = "نِعْمَتَانِ مَغْبُونٌ فِيهِمَا كَثِيرٌ مِنَ النَّاسِ: الصِّحَّةُ وَالْفَرَاغُ",
        source = "صحيح البخاري 6412"
    ),
    AuthenticHadith(
        text = "كُلُّكُمْ رَاعٍ، وَكُلُّكُمْ مَسْئُولٌ عَنْ رَعِيَّتِهِ",
        source = "صحيح البخاري 7138؛ صحيح مسلم 1829"
    ),
    AuthenticHadith(
        text = "احْرِصْ عَلَى مَا يَنْفَعُكَ، وَاسْتَعِنْ بِاللَّهِ، وَلَا تَعْجَزْ",
        source = "صحيح مسلم 2664"
    ),
    AuthenticHadith(
        text = "بَادِرُوا بِالْأَعْمَالِ سَبْعًا",
        source = "صحيح مسلم 118"
    ),
    AuthenticHadith(
        text = "أَحَبُّ الْأَعْمَالِ إِلَى اللَّهِ أَدْوَمُهَا وَإِنْ قَلَّ",
        source = "صحيح البخاري 6464؛ صحيح مسلم 783"
    )
)

fun getExamCountdownText(examDateString: String): String {
    val examDate = try {
        LocalDate.parse(examDateString)
    } catch (e: Exception) {
        LocalDate.of(2026, 11, 7)
    }
    val today = LocalDate.now()
    val daysRemaining = ChronoUnit.DAYS.between(today, examDate)

    return when {
        daysRemaining > 1 -> "متبقي $daysRemaining يوم على موعد الامتحان"
        daysRemaining == 1L -> "باقي يوم واحد على الامتحان (غداً)"
        daysRemaining == 0L -> "اليوم هو موعد الامتحان!"
        else -> "انتهى موعد الامتحان"
    }
}

fun getDynamicExamReminder(examDateString: String): String = getExamCountdownText(examDateString)

@Composable
fun AlarmScreen(
    examDateString: String,
    isAutoWakeUp: Boolean = false,
    attemptCount: Int = 1,
    onAwake: () -> Unit,
    onEndSession: () -> Unit
) {
    val hadith = remember { AUTHENTIC_HADITHS.random() }
    val examCountdown = remember(examDateString) {
        getExamCountdownText(examDateString)
    }

    // High-contrast pulsing animation for the "Wake Up" visual
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090D12))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Big pulsing "wake up" visual
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                // Outer pulsing ring
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(SoftRed.copy(alpha = pulseAlpha * 0.4f))
                )
                // Mid ring
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .clip(CircleShape)
                        .background(SoftRed.copy(alpha = 0.25f))
                        .border(2.dp, SoftRed.copy(alpha = 0.6f), CircleShape)
                )
                // Core glowing icon
                Surface(
                    shape = CircleShape,
                    color = SoftRed,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(76.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            // Huge readable text "اصحى! ذاكر الآن"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "اصحى! ذاكر الآن",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    fontSize = 38.sp,
                    color = SoftRed,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = if (isAutoWakeUp) "منبه الاستيقاظ التلقائي (المحاولة $attemptCount من 4)" else "تنبيه الخمول - حان وقت التركيز والمذاكرة!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
            }

            // Hadith Card & Exam Countdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF161C24)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "﴿ حكمة تحفيزية ﴾",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = hadith.text,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.White,
                        lineHeight = 32.sp,
                        fontSize = 20.sp
                    )

                    Text(
                        text = hadith.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.SemiBold
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color.White.copy(alpha = 0.12f)
                    )

                    // Prominent Exam Countdown Badge
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = examCountdown,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // Primary large button: "أنا مستيقظ" ("I'm Awake")
            Button(
                onClick = onAwake,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .testTag("im_awake_button"),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "أنا مستيقظ",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            }

            // Secondary button (if not auto wake-up): "إنهاء الجلسة" ("END SESSION")
            if (!isAutoWakeUp) {
                OutlinedButton(
                    onClick = onEndSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = SoftRed
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "إنهاء الجلسة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
