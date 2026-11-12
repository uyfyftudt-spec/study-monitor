package com.example.ui.forest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CompletedTree
import com.example.model.SessionStatus
import com.example.model.TreeGrowthStage
import com.example.ui.theme.SoftGreen
import com.example.ui.theme.SoftRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForestScreen(
    viewModel: ForestViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "إعادة ضبط بيانات الغابة",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في مسح جميع الأشجار المكتملة وإعادة ضبط غابتك؟ لا يمكن التراجع عن هذا الإجراء.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetForestData()
                        showResetDialog = false
                    }
                ) {
                    Text("مسح الغابة", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "غابتي الخاصة",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (uiState.completedTrees.isNotEmpty()) {
                        IconButton(onClick = { showResetDialog = true }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "إعادة ضبط الغابة",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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

            // 1. Current Tree Hero Card
            item {
                CurrentTreeHeroCard(
                    stage = uiState.currentStage,
                    nextStage = uiState.nextStage,
                    activeStudySeconds = uiState.activeStudySeconds,
                    secondsToNextStage = uiState.secondsToNextStage,
                    progressToNext = uiState.progressToNextStage,
                    sessionStatus = uiState.sessionStatus
                )
            }

            // 2. Forest Overview Stats
            item {
                ForestStatsCard(
                    completedTreesCount = uiState.completedTrees.size,
                    totalStudySeconds = uiState.completedTrees.sumOf { it.studyDurationSeconds }
                )
            }

            // 3. Completed Trees Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الأشجار المكتملة (${uiState.completedTrees.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (uiState.completedTrees.isEmpty()) {
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
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Park,
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                text = "غابتك لا تزال فارغة",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "أكمل جلسة دراسية واكسب شجرتك الأولى لتزرعها في غابتك الشخصية!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Display in clean responsive 2-column pairs
                val treePairs = uiState.completedTrees.chunked(2)
                items(treePairs.size) { index ->
                    val pair = treePairs[index]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CompletedTreeGridCard(
                            tree = pair[0],
                            modifier = Modifier.weight(1f)
                        )
                        if (pair.size > 1) {
                            CompletedTreeGridCard(
                                tree = pair[1],
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun CurrentTreeHeroCard(
    stage: TreeGrowthStage,
    nextStage: TreeGrowthStage?,
    activeStudySeconds: Long,
    secondsToNextStage: Long,
    progressToNext: Float,
    sessionStatus: SessionStatus
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressToNext.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600),
        label = "forestTreeProgress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status Tag
            val (statusText, statusBg, statusColor) = when (sessionStatus) {
                SessionStatus.STUDYING -> Triple("تنمو الآن بنشاط", MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), MaterialTheme.colorScheme.primary)
                SessionStatus.PAUSED -> Triple("الجلسة متوقفة مؤقتاً", Color(0xFF78350F).copy(alpha = 0.5f), Color(0xFFFBBF24))
                SessionStatus.INACTIVE, SessionStatus.WARNING, SessionStatus.ALARM -> Triple("تنبيه خمول", Color(0xFF451A1A).copy(alpha = 0.6f), SoftRed)
                SessionStatus.IDLE, SessionStatus.COMPLETED -> Triple("جاهزة للزراعة", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Surface(
                color = statusBg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Tree Illustration Canvas with ambient subtle glow container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center
            ) {
                TreeCanvas(
                    stage = stage,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .padding(12.dp)
                )
            }

            // Stage Title & Stage Number in Arabic
            Text(
                text = "${stage.arabicName()} (المرحلة ${stage.stageNumber} من 5)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = stage.arabicDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            // Current Session Study Duration
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "مدة جلسة المذاكرة: ${formatDurationArabic(activeStudySeconds)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Progress Bar and Milestone Text in Arabic
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "التقدم نحو المرحلة التالية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                if (nextStage != null) {
                    Text(
                        text = "متبقي ${formatDurationArabic(secondsToNextStage)} للوصول إلى ${nextStage.arabicName()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    Text(
                        text = "شجرة مكتملة النمو بالكامل! استمر في المذاكرة لتعزيز نموها.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SoftGreen,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }
}

@Composable
fun ForestStatsCard(
    completedTreesCount: Int,
    totalStudySeconds: Long
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
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "إجمالي الأشجار",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$completedTreesCount",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            VerticalDivider(
                modifier = Modifier
                    .height(40.dp)
                    .width(1.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "إجمالي وقت التركيز",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatDurationArabic(totalStudySeconds),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun CompletedTreeGridCard(
    tree: CompletedTree,
    modifier: Modifier = Modifier
) {
    val dateStr = remember(tree.timestamp) {
        val sdf = SimpleDateFormat("yyyy/MM/dd • HH:mm", Locale.US)
        sdf.format(Date(tree.timestamp))
    }

    val stage = remember(tree.studyDurationSeconds) {
        TreeGrowthStage.fromActiveSeconds(tree.studyDurationSeconds)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Mini Canvas
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                TreeCanvas(
                    stage = stage,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = stage.arabicName(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = formatDurationArabic(tree.studyDurationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Text(
                text = dateStr,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

fun TreeGrowthStage.arabicName(): String = when (this) {
    TreeGrowthStage.SEEDLING -> "بذرة"
    TreeGrowthStage.SAPLING -> "شتلة"
    TreeGrowthStage.YOUNG_TREE -> "شجرة صغيرة"
    TreeGrowthStage.BLOOMING_TREE -> "شجرة مزهرة"
    TreeGrowthStage.MATURE_TREE -> "شجرة ناضجة"
}

fun TreeGrowthStage.arabicDescription(): String = when (this) {
    TreeGrowthStage.SEEDLING -> "بذرة صغيرة تبدأ رحلتها في النمو بفضل تركيزك."
    TreeGrowthStage.SAPLING -> "شتلة يانعة تستمد قوتها من استمرارك في المذاكرة."
    TreeGrowthStage.YOUNG_TREE -> "شجرة فتية بأغصان قوية وأوراق خضراء نضرة."
    TreeGrowthStage.BLOOMING_TREE -> "شجرة مزهرة ومبهجة تعكس التزامك وتفوقك."
    TreeGrowthStage.MATURE_TREE -> "شجرة وارفة ناضجة ومثمرة، إنجاز دراسي متكامل!"
}

private fun formatDurationArabic(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 -> "${h} س و ${m} د"
        m > 0 -> "${m} د و ${s} ث"
        else -> "${s} ثانية"
    }
}
