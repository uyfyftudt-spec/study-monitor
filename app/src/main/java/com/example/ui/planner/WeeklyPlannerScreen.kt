package com.example.ui.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.DayLevel
import com.example.model.DayLevelConfig
import com.example.ui.theme.SoftGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyPlannerScreen(
    viewModel: WeeklyPlannerViewModel,
    onNavigateBack: () -> Unit
) {
    val isEnabled by viewModel.isDayLevelsEnabled.collectAsState()
    val schedule by viewModel.schedule.collectAsState()
    val levelConfigs by viewModel.levelConfigs.collectAsState()
    val lastUsedLevel by viewModel.lastUsedLevel.collectAsState()

    val localeArabic = remember { Locale("ar") }
    val today = remember { LocalDate.now() }
    val next7Days = remember(today) {
        (0L..6L).map { today.plusDays(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "مخطط الأسبوع",
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

            // 1. Overview & Toggle Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "تفعيل مستويات الأيام",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "تطبيق هدف المذاكرة ومدة الخمول تلقائياً لكل يوم",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { viewModel.setDayLevelsEnabled(it) }
                            )
                        }

                        // Levels Legend Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DayLevel.values().forEach { level ->
                                val config = levelConfigs[level] ?: DayLevelConfig(level.defaultGoalHours, level.defaultThresholdMinutes)
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = level.containerColor().copy(alpha = 0.5f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, level.color().copy(alpha = 0.4f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = level.arabicName,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = level.color()
                                        )
                                        Text(
                                            text = "${config.goalHours.toInt()} س",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Next 7 Days Planning Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "جدول الأيام القادمة (7 أيام)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // 3. The 7 Days List
            items(next7Days.size) { index ->
                val date = next7Days[index]
                val isToday = (date == today)
                val isTomorrow = (date == today.plusDays(1))

                val dayLabel = when {
                    isToday -> "اليوم (${date.format(DateTimeFormatter.ofPattern("EEEE", localeArabic))})"
                    isTomorrow -> "غداً (${date.format(DateTimeFormatter.ofPattern("EEEE", localeArabic))})"
                    else -> date.format(DateTimeFormatter.ofPattern("EEEE، d MMMM", localeArabic))
                }

                val currentSelectedLevel = schedule[date] ?: lastUsedLevel

                PlannerDayCard(
                    dayLabel = dayLabel,
                    date = date,
                    selectedLevel = currentSelectedLevel,
                    isToday = isToday,
                    onSelectLevel = { level ->
                        viewModel.setLevelForDate(date, level)
                    }
                )
            }

            // 4. Level Configurations Customizer
            item {
                Spacer(modifier = Modifier.height(4.dp))
                LevelSettingsCustomizerCard(
                    configs = levelConfigs,
                    onUpdateConfig = { level, goal, threshold ->
                        viewModel.updateLevelConfig(level, goal, threshold)
                    },
                    onResetDefaults = {
                        viewModel.resetLevelConfigsToDefault()
                    }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun PlannerDayCard(
    dayLabel: String,
    date: LocalDate,
    selectedLevel: DayLevel,
    isToday: Boolean,
    onSelectLevel: (DayLevel) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isToday) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isToday) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
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
                    if (isToday) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
                    Text(
                        text = dayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = selectedLevel.containerColor().copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, selectedLevel.color().copy(alpha = 0.5f))
                ) {
                    Text(
                        text = selectedLevel.arabicName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = selectedLevel.color(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // 4 Level Selection Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DayLevel.values().forEach { level ->
                    val isSelected = (level == selectedLevel)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectLevel(level) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) level.color() else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = level.arabicName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color(0xFF0F1419) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LevelSettingsCustomizerCard(
    configs: Map<DayLevel, DayLevelConfig>,
    onUpdateConfig: (DayLevel, Float, Int) -> Unit,
    onResetDefaults: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
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
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "تخصيص أهداف المستويات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                TextButton(onClick = onResetDefaults) {
                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("الافتراضي", fontSize = 12.sp)
                }
            }

            // Each level config slider
            DayLevel.values().forEach { level ->
                val config = configs[level] ?: DayLevelConfig(level.defaultGoalHours, level.defaultThresholdMinutes)
                var currentGoal by remember(config.goalHours) { mutableStateOf(config.goalHours) }
                var currentThreshold by remember(config.thresholdMinutes) { mutableStateOf(config.thresholdMinutes) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                            Surface(
                                shape = CircleShape,
                                color = level.color(),
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Text(
                                text = "مستوى ${level.arabicName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = level.color()
                            )
                        }

                        Text(
                            text = "${currentGoal.toInt()} ساعات • ${currentThreshold} د خمول",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Goal hours slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "الهدف:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(50.dp)
                        )
                        Slider(
                            value = currentGoal,
                            onValueChange = {
                                currentGoal = it
                                onUpdateConfig(level, it, currentThreshold)
                            },
                            valueRange = 1f..12f,
                            steps = 10,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = level.color(),
                                activeTrackColor = level.color()
                            )
                        )
                        Text(
                            text = "${currentGoal.toInt()} س",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp)
                        )
                    }

                    // Inactivity threshold slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "الخمول:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(50.dp)
                        )
                        Slider(
                            value = currentThreshold.toFloat(),
                            onValueChange = {
                                currentThreshold = it.toInt()
                                onUpdateConfig(level, currentGoal, it.toInt())
                            },
                            valueRange = 1f..30f,
                            steps = 28,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = level.color(),
                                activeTrackColor = level.color()
                            )
                        )
                        Text(
                            text = "${currentThreshold} د",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }
            }
        }
    }
}

fun DayLevel.color(): Color = when (this) {
    DayLevel.NORMAL -> Color(0xFF2DD4BF) // Teal
    DayLevel.INTENSIVE -> Color(0xFFF59E0B) // Amber
    DayLevel.WAR -> Color(0xFFEF4444) // Red
    DayLevel.REST -> Color(0xFF818CF8) // Indigo/Purple
}

fun DayLevel.containerColor(): Color = when (this) {
    DayLevel.NORMAL -> Color(0xFF134E48)
    DayLevel.INTENSIVE -> Color(0xFF78350F)
    DayLevel.WAR -> Color(0xFF7F1D1D)
    DayLevel.REST -> Color(0xFF312E81)
}
