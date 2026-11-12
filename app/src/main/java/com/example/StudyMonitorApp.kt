package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.AndroidActivityMonitor
import com.example.core.ForestRepository
import com.example.core.SessionManager
import com.example.core.SettingsRepository
import com.example.core.StatisticsRepository
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.forest.ForestScreen
import com.example.ui.forest.ForestViewModel
import com.example.ui.habits.HabitsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.statistics.StatisticsScreen
import com.example.ui.statistics.StatisticsViewModel
import com.example.ui.theme.AppTheme

sealed class BottomNavItem(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector
) {
    object Home : BottomNavItem("dashboard", R.string.tab_home, Icons.Default.Home)
    object Habits : BottomNavItem("habits", R.string.tab_habits, Icons.Default.CheckCircle)
    object Statistics : BottomNavItem("statistics", R.string.tab_statistics, Icons.Default.BarChart)
    object Forest : BottomNavItem("forest", R.string.tab_forest, Icons.Default.Park)
    object Settings : BottomNavItem("settings", R.string.tab_settings, Icons.Default.Settings)
}

@Composable
fun StudyMonitorApp(
    activityMonitor: AndroidActivityMonitor,
    sessionManager: SessionManager,
    settingsRepository: SettingsRepository,
    statisticsRepository: StatisticsRepository,
    floatingTimerManager: com.example.core.FloatingTimerManager,
    forestRepository: ForestRepository
) {
    AppTheme {
        val navController = rememberNavController()

        val dashboardViewModel = remember {
            DashboardViewModel(sessionManager, statisticsRepository, settingsRepository, floatingTimerManager)
        }
        val settingsViewModel = remember {
            SettingsViewModel(settingsRepository)
        }
        val statisticsViewModel = remember {
            StatisticsViewModel(statisticsRepository)
        }
        val forestViewModel = remember {
            ForestViewModel(sessionManager, forestRepository)
        }

        val navItems = listOf(
            BottomNavItem.Home,
            BottomNavItem.Habits,
            BottomNavItem.Statistics,
            BottomNavItem.Forest,
            BottomNavItem.Settings
        )

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                            activityMonitor.notifyUserActivity()
                        }
                    }
                }
        ) {
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        navItems.forEach { item ->
                            val selected = currentRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (currentRoute != item.route) {
                                        navController.navigate(item.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = stringResource(item.titleResId)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(item.titleResId),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = BottomNavItem.Home.route,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable(BottomNavItem.Home.route) {
                        DashboardScreen(
                            viewModel = dashboardViewModel,
                            onNavigateToSettings = { navController.navigate(BottomNavItem.Settings.route) },
                            onNavigateToStatistics = { navController.navigate(BottomNavItem.Statistics.route) },
                            onNavigateToForest = { navController.navigate(BottomNavItem.Forest.route) }
                        )
                    }
                    composable(BottomNavItem.Habits.route) {
                        HabitsScreen()
                    }
                    composable(BottomNavItem.Statistics.route) {
                        StatisticsScreen(
                            viewModel = statisticsViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(BottomNavItem.Forest.route) {
                        ForestScreen(
                            viewModel = forestViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                    composable(BottomNavItem.Settings.route) {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
