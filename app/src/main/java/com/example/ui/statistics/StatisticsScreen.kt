package com.example.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.DailyStatistics
import com.example.core.StatisticsRepository
import com.example.model.StudySession
import com.example.ui.dashboard.SessionItem
import com.example.ui.dashboard.formatTime
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftRed
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class StatisticsViewModel(
    statisticsRepository: StatisticsRepository
) : ViewModel() {
    val todayStatistics = statisticsRepository.getTodayStatistics().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val recentSessions = statisticsRepository.getRecentSessions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onNavigateBack: () -> Unit
) {
    val todayStats by viewModel.todayStatistics.collectAsState()
    val recentSessions by viewModel.recentSessions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الإحصائيات والتقارير",
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Today's Statistics Card
            item {
                TodayDetailedStatsCard(todayStats = todayStats)
            }

            // 2. Simple 7-Day Bar Chart (Drawn with Compose Canvas)
            item {
                SevenDaysStudyChartCard(
                    recentSessions = recentSessions,
                    todayStats = todayStats
                )
            }

            // 3. Clean Session History Section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "سجل الجلسات السابقة (${recentSessions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (recentSessions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "📊", fontSize = 32.sp)
                            Text(
                                text = "لا توجد جلسات مسجلة بعد",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "ابدأ جلستك الأولى من الشاشة الرئيسية لتسجيل إحصائياتك هنا.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(recentSessions) { session ->
                    SessionItem(session)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

// ============================================================================
// 1. Today's Detailed Stats Card
// ============================================================================
@Composable
fun TodayDetailedStatsCard(todayStats: DailyStatistics?) {
    val totalTime = todayStats?.totalStudyTimeSeconds ?: 0L
    val activeTime = todayStats?.activeStudyTimeSeconds ?: 0L
    val inactiveTime = todayStats?.inactiveTimeSeconds ?: 0L
    val totalAlerts = todayStats?.totalAlerts ?: 0

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "📅", fontSize = 18.sp)
                    Text(
                        text = "ملخص دراسة اليوم",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = "اليوم",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatMetricBox(
                    modifier = Modifier.weight(1f),
                    title = "الإجمالي",
                    value = formatTime(totalTime),
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatMetricBox(
                    modifier = Modifier.weight(1f),
                    title = "النشطة",
                    value = formatTime(activeTime),
                    color = MaterialTheme.colorScheme.primary
                )
                StatMetricBox(
                    modifier = Modifier.weight(1f),
                    title = "الخمول",
                    value = formatTime(inactiveTime),
                    color = Color(0xFFFBBF24)
                )
                StatMetricBox(
                    modifier = Modifier.weight(1f),
                    title = "التنبيهات",
                    value = totalAlerts.toString(),
                    color = SoftRed
                )
            }
        }
    }
}

@Composable
fun StatMetricBox(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// ============================================================================
// 2. Simple 7-Day Bar Chart (Drawn with Compose Canvas)
// ============================================================================
data class DayStudyData(
    val dayName: String,
    val studyHours: Float,
    val isToday: Boolean
)

@Composable
fun SevenDaysStudyChartCard(
    recentSessions: List<StudySession>,
    todayStats: DailyStatistics?
) {
    val localeArabic = remember { Locale("ar") }
    val today = remember { LocalDate.now() }

    val daysData = remember(recentSessions, todayStats) {
        val list = mutableListOf<DayStudyData>()
        // 6 days ago up to today
        for (i in 6 downTo 0) {
            val date = today.minusDays(i.toLong())
            val dayName = if (i == 0) {
                "اليوم"
            } else {
                date.dayOfWeek.getDisplayName(TextStyle.SHORT, localeArabic)
            }

            // Sum active study time for sessions on this date
            val sessionsOnDay = recentSessions.filter { it.startTime.toLocalDate() == date }
            var totalActiveSeconds = sessionsOnDay.sumOf { it.activeStudyTimeSeconds }
            if (i == 0 && todayStats != null) {
                totalActiveSeconds = maxOf(totalActiveSeconds, todayStats.activeStudyTimeSeconds)
            }
            val hours = (totalActiveSeconds / 3600f)

            list.add(
                DayStudyData(
                    dayName = dayName,
                    studyHours = hours,
                    isToday = (i == 0)
                )
            )
        }
        list
    }

    val maxHours = remember(daysData) {
        val peak = daysData.maxOfOrNull { it.studyHours } ?: 1f
        if (peak <= 0.1f) 4f else (kotlin.math.ceil(peak * 1.2f).coerceAtLeast(2f))
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "📊", fontSize = 18.sp)
                    Text(
                        text = "نشاط المذاكرة (آخر 7 أيام)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "بالساعات",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Canvas Bar Chart
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(vertical = 4.dp)
            ) {
                val chartWidth = size.width
                val chartHeight = size.height
                val bottomLabelHeight = 28f
                val barAreaHeight = chartHeight - bottomLabelHeight
                val barCount = daysData.size
                val barSpacing = chartWidth / barCount
                val barWidth = barSpacing * 0.42f

                // Draw horizontal guide lines
                val gridLines = 3
                for (g in 1..gridLines) {
                    val y = barAreaHeight * (g.toFloat() / gridLines.toFloat())
                    drawLine(
                        color = surfaceVariant.copy(alpha = 0.5f),
                        start = Offset(0f, y),
                        end = Offset(chartWidth, y),
                        strokeWidth = 1f
                    )
                }

                // Draw bars for each day
                daysData.forEachIndexed { index, day ->
                    val centerX = (index + 0.5f) * barSpacing
                    val left = centerX - barWidth / 2f

                    // Background track bar
                    drawRoundRect(
                        color = surfaceVariant.copy(alpha = 0.35f),
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, barAreaHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )

                    // Active filled bar with gradient
                    val fillHeight = if (maxHours > 0f) {
                        (day.studyHours / maxHours) * barAreaHeight
                    } else 0f

                    val actualBarHeight = fillHeight.coerceIn(barWidth / 2f, barAreaHeight)
                    val barTop = barAreaHeight - actualBarHeight

                    val barBrush = if (day.isToday) {
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF5EEAD4), primaryColor),
                            startY = barTop,
                            endY = barAreaHeight
                        )
                    } else {
                        Brush.verticalGradient(
                            colors = listOf(primaryColor, Color(0xFF0F766E)),
                            startY = barTop,
                            endY = barAreaHeight
                        )
                    }

                    if (day.studyHours > 0.05f) {
                        drawRoundRect(
                            brush = barBrush,
                            topLeft = Offset(left, barTop),
                            size = Size(barWidth, actualBarHeight),
                            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                        )
                    }

                    // Native Canvas Text for Day labels
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = if (day.isToday) primaryColor.toArgb() else onSurfaceVariant.toArgb()
                            textSize = 28f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                        drawText(day.dayName, centerX, chartHeight - 4f, paint)

                        // If hours > 0, draw hours text on top of bar
                        if (day.studyHours > 0.1f) {
                            val valuePaint = android.graphics.Paint().apply {
                                color = onSurface.toArgb()
                                textSize = 22f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                            }
                            val hoursText = String.format(Locale.US, "%.1f", day.studyHours)
                            drawText(hoursText, centerX, (barTop - 6f).coerceAtLeast(20f), valuePaint)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "مجموع ساعات الأسبوع: ${String.format(Locale.US, "%.1f", daysData.sumOf { it.studyHours.toDouble() })} ساعة",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "أعلى يوم: ${String.format(Locale.US, "%.1f", daysData.maxOfOrNull { it.studyHours } ?: 0f)} س",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
