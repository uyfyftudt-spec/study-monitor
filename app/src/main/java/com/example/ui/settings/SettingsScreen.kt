package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.example.core.SettingsRepository
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftRed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    val inactivityThresholdMinutes = settingsRepository.inactivityThresholdMinutes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 10
    )
    val alarmSoundUri = settingsRepository.alarmSoundUri.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val examDate = settingsRepository.examDate.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsRepository.DEFAULT_EXAM_DATE
    )

    val autoWakeUpEnabled = settingsRepository.autoWakeUpEnabled.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    fun setThreshold(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setInactivityThresholdMinutes(minutes)
        }
    }

    fun setAlarmSoundUri(uri: String?) {
        viewModelScope.launch {
            settingsRepository.setAlarmSoundUri(uri)
        }
    }

    fun setExamDate(dateIso: String) {
        viewModelScope.launch {
            settingsRepository.setExamDate(dateIso)
        }
    }

    fun setAutoWakeUpEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoWakeUpEnabled(enabled)
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabledServices?.contains("com.example/com.example.service.StudyAccessibilityService") == true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val threshold by viewModel.inactivityThresholdMinutes.collectAsState()
    val alarmSoundUriString by viewModel.alarmSoundUri.collectAsState()
    val examDateString by viewModel.examDate.collectAsState()
    val isAutoWakeUpEnabled by viewModel.autoWakeUpEnabled.collectAsState()
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (e: Exception) {
                // Ignore if content provider doesn't support persistable permissions
            }
            viewModel.setAlarmSoundUri(uri.toString())
        }
    }

    val soundDisplayName = remember(alarmSoundUriString) {
        if (!alarmSoundUriString.isNullOrBlank()) {
            val uri = try {
                Uri.parse(alarmSoundUriString)
            } catch (e: Exception) {
                null
            }
            if (uri != null) {
                var resolvedName: String? = null
                try {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            resolvedName = cursor.getString(nameIndex)
                        }
                    }
                } catch (e: Exception) {
                    // ContentResolver query failed
                }

                if (resolvedName.isNullOrBlank()) {
                    try {
                        resolvedName = RingtoneManager.getRingtone(context, uri)?.getTitle(context)
                    } catch (e: Exception) {
                        // Ringtone resolution failed
                    }
                }

                resolvedName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "ملف صوتي مخصص"
            } else {
                "صوت التنبيه الافتراضي"
            }
        } else {
            "صوت التنبيه الافتراضي"
        }
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlayingPreview by remember { mutableStateOf(false) }

    val stopPreview = {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {}
        mediaPlayer = null
        isPlayingPreview = false
    }

    val startPreview = {
        stopPreview()
        val uri = try {
            if (!alarmSoundUriString.isNullOrBlank()) {
                Uri.parse(alarmSoundUriString)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
        } catch (e: Exception) {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }

        if (uri != null) {
            try {
                val player = MediaPlayer().apply {
                    setDataSource(context, uri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setOnCompletionListener {
                        stopPreview()
                    }
                    prepare()
                    start()
                }
                mediaPlayer = player
                isPlayingPreview = true
            } catch (e: Exception) {
                // If custom sound fails to preview, try fallback default
                try {
                    val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    if (fallbackUri != null && fallbackUri != uri) {
                        val player = MediaPlayer().apply {
                            setDataSource(context, fallbackUri)
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            setOnCompletionListener {
                                stopPreview()
                            }
                            prepare()
                            start()
                        }
                        mediaPlayer = player
                        isPlayingPreview = true
                    }
                } catch (fallbackEx: Exception) {
                    stopPreview()
                }
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                stopPreview()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopPreview()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الإعدادات",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================================================================
            // 1. بطاقة الخمول (Inactivity Threshold & System Monitoring)
            // ================================================================
            SettingsSectionCard(
                icon = Icons.Default.HourglassEmpty,
                title = "مدة الخمول المسموحة",
                subtitle = "إطلاق التنبيه بعد $threshold دقيقة من عدم النشاط"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Slider(
                        value = threshold.toFloat(),
                        onValueChange = { viewModel.setThreshold(it.toInt()) },
                        valueRange = 1f..30f,
                        steps = 29,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "1 دقيقة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "$threshold دقيقة",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = "30 دقيقة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // System activity accessibility status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "مراقبة نشاط النظام",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isAccessibilityEnabled) "الخدمة نشطة وتعمل بدقة" else "الخدمة متوقفة، يرجى تفعيلها",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAccessibilityEnabled) SoftGreen else SoftRed
                            )
                        }

                        if (!isAccessibilityEnabled) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("تفعيل الخدمة", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = SoftGreen.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = SoftGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================================================================
            // 2. بطاقة الصوت (Alarm Sound)
            // ================================================================
            SettingsSectionCard(
                icon = Icons.Default.MusicNote,
                title = "صوت التنبيه",
                subtitle = "النغمة الحالية: $soundDisplayName"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                audioPickerLauncher.launch(arrayOf("audio/*"))
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("اختيار نغمة مخصصة", fontWeight = FontWeight.Bold)
                        }

                        if (alarmSoundUriString != null) {
                            OutlinedButton(
                                onClick = {
                                    stopPreview()
                                    viewModel.setAlarmSoundUri(null)
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("الافتراضي")
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (isPlayingPreview) {
                                stopPreview()
                            } else {
                                startPreview()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (isPlayingPreview) SoftRed else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isPlayingPreview) "إيقاف المعاينة" else "تجربة صوت التنبيه",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ================================================================
            // 3. بطاقة موعد الامتحان (Exam Date)
            // ================================================================
            val parsedExamDate = remember(examDateString) {
                try {
                    LocalDate.parse(examDateString)
                } catch (e: Exception) {
                    LocalDate.of(2026, 11, 7)
                }
            }
            val today = remember { LocalDate.now() }
            val daysRemaining = ChronoUnit.DAYS.between(today, parsedExamDate)

            val formattedExamDate = remember(parsedExamDate) {
                try {
                    val formatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ar"))
                    parsedExamDate.format(formatter)
                } catch (e: Exception) {
                    parsedExamDate.toString()
                }
            }

            val examStatusText = when {
                daysRemaining > 1 -> "متبقي $daysRemaining يوم على موعد الامتحان"
                daysRemaining == 1L -> "باقي يوم واحد فقط - غداً الامتحان!"
                daysRemaining == 0L -> "اليوم هو موعد الامتحان! بالتوفيق"
                else -> "انتهى موعد الامتحان (منذ ${-daysRemaining} يوم)"
            }

            SettingsSectionCard(
                icon = Icons.Default.CalendarMonth,
                title = "موعد الامتحان النهائي",
                subtitle = "التاريخ المحدد: $formattedExamDate"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "⏳", fontSize = 18.sp)
                            Text(
                                text = examStatusText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, dayOfMonth ->
                                        val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                                        viewModel.setExamDate(selectedDate.toString())
                                    },
                                    parsedExamDate.year,
                                    parsedExamDate.monthValue - 1,
                                    parsedExamDate.dayOfMonth
                                ).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("تحديد موعد الامتحان", fontWeight = FontWeight.Bold)
                        }

                        if (examDateString != SettingsRepository.DEFAULT_EXAM_DATE) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.setExamDate(SettingsRepository.DEFAULT_EXAM_DATE)
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("الافتراضي")
                            }
                        }
                    }
                }
            }

            // ================================================================
            // 4. بطاقة الاستيقاظ التلقائي (Auto Wake-Up Monitor)
            // ================================================================
            SettingsSectionCard(
                icon = Icons.Default.WbSunny,
                title = "منبه الاستيقاظ التلقائي",
                subtitle = if (isAutoWakeUpEnabled) "الميزة مفعلة وتراقب أوقات النوم" else "الميزة متوقفة حالياً"
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (isAutoWakeUpEnabled) "مفعل" else "معطل",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isAutoWakeUpEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "يبدأ عداد خمول لمدة 3 ساعات عند إغلاق التطبيق. يُطلق منبهاً تصاعدياً على 4 محاولات كل 10 دقائق حتى تؤكد استيقاظك.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        }
                        Switch(
                            checked = isAutoWakeUpEnabled,
                            onCheckedChange = { viewModel.setAutoWakeUpEnabled(it) },
                            modifier = Modifier.testTag("auto_wakeup_switch"),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            content()
        }
    }
}
