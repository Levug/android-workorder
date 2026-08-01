package com.workorder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.workorder.app.data.model.Settings
import com.workorder.app.ui.navigation.Screen
import com.workorder.app.ui.navigation.bottomNavItems
import com.workorder.app.ui.navigation.getTabForRoute
import com.workorder.app.ui.navigation.shouldShowBottomBar
import com.workorder.app.ui.screen.CalendarScreen
import com.workorder.app.ui.screen.OperationsScreen
import com.workorder.app.ui.screen.ReportsScreen
import com.workorder.app.ui.screen.SettingsScreen
import com.workorder.app.ui.screen.WorkDayScreen
import com.workorder.app.ui.theme.WorkOrderTheme
import com.workorder.app.ui.viewmodel.CalendarViewModel
import com.workorder.app.ui.viewmodel.OperationsViewModel
import com.workorder.app.ui.viewmodel.ReportViewModel
import com.workorder.app.ui.viewmodel.SettingsViewModel
import com.workorder.app.ui.viewmodel.WorkDayViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val settingsRepository = (application as WorkOrderApp).container.settingsRepository

        setContent {
            val settings by settingsRepository.observe()
                .collectAsStateWithLifecycle(initialValue = Settings())

            WorkOrderTheme(
                themeMode = settings.themeMode,
                themePresetName = settings.themePreset,
                dynamicColor = settings.dynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (shouldShowBottomBar(currentRoute)) {
                                NavigationBar {
                                    val selectedTab = getTabForRoute(currentRoute)
                                    bottomNavItems.forEach { item ->
                                        val selected = selectedTab == item.screen
                                        NavigationBarItem(
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) item.selectedIcon else item.icon,
                                                    contentDescription = item.label
                                                )
                                            },
                                            label = { Text(item.label) },
                                            selected = selected,
                                            onClick = {
                                                navController.navigate(item.screen.route) {
                                                    popUpTo(navController.graph.startDestinationId) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = Screen.Today.route,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            // Вкладка «Сегодня»: наряд на текущую дату
                            composable(Screen.Today.route) {
                                val today by rememberCurrentLocalDate()
                                key(today) {
                                    val vm = viewModel<WorkDayViewModel>(
                                        key = "today-$today",
                                        factory = WorkDayViewModel.Factory
                                    )
                                    WorkDayScreen(viewModel = vm, onNavigateBack = null)
                                }
                            }

                            // Наряд на произвольную дату (из календаря)
                            composable(
                                route = Screen.WorkDay.route,
                                arguments = listOf(navArgument("date") { type = NavType.StringType })
                            ) {
                                val vm = viewModel<WorkDayViewModel>(factory = WorkDayViewModel.Factory)
                                WorkDayScreen(
                                    viewModel = vm,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }

                            composable(Screen.Calendar.route) {
                                val vm = viewModel<CalendarViewModel>(factory = CalendarViewModel.Factory)
                                CalendarScreen(
                                    viewModel = vm,
                                    onNavigateToDay = { date ->
                                        navController.navigate(Screen.WorkDay.createRoute(date))
                                    }
                                )
                            }

                            composable(Screen.Reports.route) {
                                val vm = viewModel<ReportViewModel>(factory = ReportViewModel.Factory)
                                ReportsScreen(viewModel = vm)
                            }

                            composable(Screen.Settings.route) {
                                val vm = viewModel<SettingsViewModel>(factory = SettingsViewModel.Factory)
                                SettingsScreen(
                                    viewModel = vm,
                                    onNavigateToOperations = {
                                        navController.navigate(Screen.Operations.route)
                                    }
                                )
                            }

                            composable(Screen.Operations.route) {
                                val vm = viewModel<OperationsViewModel>(factory = OperationsViewModel.Factory)
                                OperationsScreen(
                                    viewModel = vm,
                                    onNavigateBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Обновляет вкладку «Сегодня» после полуночи и при возврате приложения из фона. */
@Composable
private fun rememberCurrentLocalDate(): State<LocalDate> {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(initialValue = LocalDate.now(), lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) value = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        try {
            while (true) {
                delay(60_000)
                value = LocalDate.now()
            }
        } finally {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
