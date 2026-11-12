package com.example.ui.dashboard

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.DailyStatistics
import com.example.model.DayLevel
import com.example.model.DayLevelConfig
import com.example.model.SessionStatus
import com.example.model.StudySession
import com.example.model.TreeGrowthStage
import com.example.ui.forest.TreeCanvas
import com.example.ui.planner.color
import com.example.ui.planner.containerColor
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftRed
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToForest: () -> Unit = {},
    onNavigateToWeeklyPlanner: () -> Unit = {}
) {
    val sessionState by viewModel.sessionState.collectAsState()
    val elapsedStudyTime by viewModel.elapsedStudyTime.collectAsState()
    val thresholdMinutes by viewModel.thresholdMinutes.collectAsState()
    val todayStats by viewModel.todayStatistics.collectAsState()
    val recentSessions by viewModel.recentSessions.collectAsState()

    // Day level states
    val todayLevel by viewModel.todayLevel.collectAsState()
    val todayConfig by viewModel.todayConfig.collectAsState()
    val todayStudiedSeconds by viewModel.todayStudiedSeconds.collectAsState()
    val currentStreak by viewModel.currentStreak.collectAsState()
    val isDayLevelsEnabled by viewModel.isDayLevelsEnabled.collectAsState()

    var showChangeLevelDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Maintain overlay consistency across lifecycles
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, sessionState) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (sessionState == SessionStatus.STUDYING) {
                    viewModel.ensureOverlayVisible()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showChangeLevelDialog) {
        ChangeTodayLevelDialog(
            currentLevel = todayLevel,
            onSelectLevel = {
                viewModel.setTodayLevel(it)
                showChangeLevelDialog = false
            },
            onDismiss = { showChangeLevelDialog = false }
        )
    }

    val onStartAction = {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        }
        viewModel.startSession()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWideScreen = maxWidth >= 680.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Greeting Bar
            item {
                TopGreetingBar()
            }

            // 2. Today's Level Card
            item {
                TodayLevelCard(
                    todayLevel = todayLevel,
                    todayConfig = todayConfig,
                    isDayLevelsEnabled = isDayLevelsEnabled,
                    onChangeLevelClick = { showChangeLevelDialog = true },
                    onWeeklyPlannerClick = onNavigateToWeeklyPlanner
                )
            }

            if (isWideScreen) {
                // Adaptive 2-column layout for tablet landscape / wide screens
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // First column (Right in RTL)
                        Column(
                            modifier = Modifier.weight(1.1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CentralTimerCard(
                                sessionState = sessionState,
                                elapsedStudyTime = elapsedStudyTime,
                                onStart = onStartAction,
                                onPause = viewModel::pauseSession,
                                onResume = viewModel::resumeSession,
                                onEnd = viewModel::endSession
                            )

                            DailyGoalCard(
                                todayStudiedSeconds = todayStudiedSeconds,
                                goalHours = todayConfig.goalHours,
                                currentSessionActiveSeconds = if (sessionState == SessionStatus.STUDYING) elapsedStudyTime else 0L
                            )
                        }

                        // Second column (Left in RTL)
                        Column(
                            modifier = Modifier.weight(0.9f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            StatCardsRow(
                                totalAlerts = todayStats?.totalAlerts ?: 0,
                                sessionCount = recentSessions.size,
                                consecutiveDays = currentStreak
                            )

                            NextHabitCard()

                            ForestPreviewCard(
                                elapsedStudyTime = elapsedStudyTime,
                                onClick = onNavigateToForest
                            )
                        }
                    }
                }
            } else {
                // Single column layout for compact / portrait screens
                item {
                    CentralTimerCard(
                        sessionState = sessionState,
                        elapsedStudyTime = elapsedStudyTime,
                        onStart = onStartAction,
                        onPause = viewModel::pauseSession,
                        onResume = viewModel::resumeSession,
                        onEnd = viewModel::endSession
                    )
                }

                item {
                    DailyGoalCard(
                        todayStudiedSeconds = todayStudiedSeconds,
                        goalHours = todayConfig.goalHours,
                        currentSessionActiveSeconds = if (sessionState == SessionStatus.STUDYING) elapsedStudyTime else 0L
                    )
                }

                item {
                    StatCardsRow(
                        totalAlerts = todayStats?.totalAlerts ?: 0,
                        sessionCount = recentSessions.size,
                        consecutiveDays = currentStreak
                    )
                }

                item {
                    NextHabitCard()
                }

                item {
                    ForestPreviewCard(
                        elapsedStudyTime = elapsedStudyTime,
                        onClick = onNavigateToForest
                    )
                }
            }

            // Recent Sessions Section
            if (recentSessions.isNotEmpty()) {
                item {
                    Text(
                        text = "الجلسات الأخيرة",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                items(recentSessions.take(5)) { session ->
                    SessionItem(session)
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// ============================================================================
// Today Level Card & Quick Change Dialog
// ============================================================================
@Composable
fun TodayLevelCard(
    todayLevel: DayLevel,
    todayConfig: DayLevelConfig,
    isDayLevelsEnabled: Boolean,
    onChangeLevelClick: () -> Unit,
    onWeeklyPlannerClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Colored Level Badge
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = todayLevel.containerColor().copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, todayLevel.color()),
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = todayLevel.arabicName.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = todayLevel.color()
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "مستوى اليوم: ${todayLevel.arabicName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "الهدف: ${todayConfig.goalHours.toInt()} ساعات • الخمول: ${todayConfig.thresholdMinutes} د",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onChangeLevelClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("تغيير", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onWeeklyPlannerClick,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("المخطط", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChangeTodayLevelDialog(
    currentLevel: DayLevel,
    onSelectLevel: (DayLevel) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "اختر مستوى اليوم",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DayLevel.values().forEach { level ->
                    val isSelected = (level == currentLevel)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onSelectLevel(level) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) level.color() else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = level.arabicName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFF0F1419) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${level.defaultGoalHours.toInt()} ساعات • ${level.defaultThresholdMinutes} د خمول",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) Color(0xFF0F1419) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        }
    )
}

// ============================================================================
// 1. Top Greeting Bar
// ============================================================================
@Composable
fun TopGreetingBar() {
    val arabicDate = remember {
        try {
            val now = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("EEEE، d MMMM yyyy", Locale("ar"))
            now.format(formatter)
        } catch (e: Exception) {
            "اليوم"
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "السلام عليكم",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = arabicDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "🌱",
                    fontSize = 22.sp
                )
            }
        }
    }
}

// ============================================================================
// 2. Large Central Timer Card
// ============================================================================
@Composable
fun CentralTimerCard(
    sessionState: SessionStatus,
    elapsedStudyTime: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit
) {
    val hours = elapsedStudyTime / 3600
    val minutes = (elapsedStudyTime % 3600) / 60
    val seconds = elapsedStudyTime % 60
    val timeString = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)

    val (statusLabel, statusColor, statusBg) = when (sessionState) {
        SessionStatus.STUDYING -> Triple("يذاكر الآن", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
        SessionStatus.PAUSED -> Triple("متوقف مؤقتاً", Color(0xFFFBBF24), Color(0xFF78350F).copy(alpha = 0.35f))
        SessionStatus.INACTIVE -> Triple("خامل", SoftRed, Color(0xFF451A1A).copy(alpha = 0.5f))
        SessionStatus.WARNING, SessionStatus.ALARM -> Triple("تنبيه!", SoftRed, Color(0xFF451A1A).copy(alpha = 0.5f))
        else -> Triple("جاهز للدراسة", MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceVariant)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Big bold study timer
            Text(
                text = timeString,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 50.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 2.sp
            )

            // Session status chip
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusBg,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = statusColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Primary action button ("ابدأ المذاكرة" / "إنهاء الجلسة")
            val isSessionActive = sessionState == SessionStatus.STUDYING ||
                    sessionState == SessionStatus.PAUSED ||
                    sessionState == SessionStatus.INACTIVE

            if (!isSessionActive) {
                Button(
                    onClick = onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ابدأ المذاكرة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Secondary action: Pause or Resume
                    if (sessionState == SessionStatus.PAUSED) {
                        OutlinedButton(
                            onClick = onResume,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("استئناف", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedButton(
                            onClick = onPause,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface
                            )
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إيقاف مؤقت", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Primary big button: End Session
                    Button(
                        onClick = onEnd,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SoftRed,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إنهاء الجلسة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. Daily-Goal Card (Day Levels Integrated)
// ============================================================================
@Composable
fun DailyGoalCard(
    todayStudiedSeconds: Long,
    goalHours: Float,
    currentSessionActiveSeconds: Long = 0L
) {
    val totalActiveSeconds = todayStudiedSeconds + currentSessionActiveSeconds
    val targetSeconds = (goalHours * 3600f).toLong().coerceAtLeast(1L)
    val progress = (totalActiveSeconds.toFloat() / targetSeconds.toFloat()).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 700),
        label = "goalProgress"
    )

    val hoursStudied = totalActiveSeconds / 3600.0
    val hoursFormatted = String.format(Locale.US, "%.1f", hoursStudied)
    val remainingSeconds = maxOf(0L, targetSeconds - totalActiveSeconds)
    val remainingHours = remainingSeconds / 3600.0
    val remainingFormatted = String.format(Locale.US, "%.1f", remainingHours)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "🎯", fontSize = 18.sp)
                    Text(
                        text = "هدف اليوم",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Wide smooth progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ذاكرت $hoursFormatted ساعة من ${goalHours.toInt()} ساعات",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (progress >= 1f) "اكتمل الهدف! 🎉" else "متبقي $remainingFormatted ساعة",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (progress >= 1f) SoftGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
// 4. Small Row of 3 Stat Cards (Real Streak integrated)
// ============================================================================
@Composable
fun StatCardsRow(
    totalAlerts: Int,
    sessionCount: Int,
    consecutiveDays: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Stat 1: Alerts
        MiniStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.NotificationsActive,
            iconTint = SoftRed,
            title = totalAlerts.toString(),
            label = "تنبيهات اليوم"
        )

        // Stat 2: Sessions
        MiniStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Timer,
            iconTint = MaterialTheme.colorScheme.primary,
            title = sessionCount.toString(),
            label = "عدد الجلسات"
        )

        // Stat 3: Real Consecutive Streak
        MiniStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.LocalFireDepartment,
            iconTint = Color(0xFFFB923C),
            title = "$consecutiveDays أيام",
            label = "أيام متتالية"
        )
    }
}

@Composable
fun MiniStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ============================================================================
// 5. Next Habit Card
// ============================================================================
@Composable
fun NextHabitCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircleOutline,
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
                    text = "العادة القادمة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "لا توجد عادات بعد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
// 6. Compact Forest Preview Card
// ============================================================================
@Composable
fun ForestPreviewCard(
    elapsedStudyTime: Long,
    onClick: () -> Unit
) {
    val stage = TreeGrowthStage.fromActiveSeconds(elapsedStudyTime)
    val stageArabicName = when (stage) {
        TreeGrowthStage.SEEDLING -> "بذرة"
        TreeGrowthStage.SAPLING -> "شتلة"
        TreeGrowthStage.YOUNG_TREE -> "شجرة صغيرة"
        TreeGrowthStage.BLOOMING_TREE -> "شجرة مزهرة"
        TreeGrowthStage.MATURE_TREE -> "شجرة ناضجة"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    TreeCanvas(stage = stage, modifier = Modifier.size(46.dp))
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "شجرتك الحالية",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = stageArabicName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "تنمو مع تقدم وقت دراستك",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "عرض الغابة",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================================================
// Compatibility Components (Preserved for StatisticsScreen.kt & other imports)
// ============================================================================
@Composable
fun StatisticsSummary(stats: DailyStatistics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "ملخص اليوم",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("الإجمالي", formatTime(stats.totalStudyTimeSeconds))
                StatItem("النشطة", formatTime(stats.activeStudyTimeSeconds))
                StatItem("الخمول", formatTime(stats.inactiveTimeSeconds))
                StatItem("التنبيهات", stats.totalAlerts.toString())
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}س ${m}د" else "${m}د"
}

@Composable
fun SessionItem(session: StudySession) {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.US)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = session.startTime.format(formatter),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "وقت الدراسة: ${formatTime(session.activeStudyTimeSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (session.alertCount > 0) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "${session.alertCount} تنبيهات",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (session.alertCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
