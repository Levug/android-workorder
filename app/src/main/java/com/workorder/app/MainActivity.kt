package com.workorder.app

import android.app.UiModeManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.os.Build
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
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
import com.workorder.app.data.model.ThemeMode
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
import com.workorder.app.ui.theme.windowBackgroundColor
import com.workorder.app.ui.viewmodel.CalendarViewModel
import com.workorder.app.ui.viewmodel.OperationsViewModel
import com.workorder.app.ui.viewmodel.ReportViewModel
import com.workorder.app.ui.viewmodel.SettingsViewModel
import com.workorder.app.ui.viewmodel.WorkDayViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    /**
     * Стартовый экран системы рисуется по ночному режиму до запуска приложения, поэтому при своей
     * светлой теме на тёмной системе виден тёмный кадр. С Android 12 приложение может объявить
     * системе собственный режим — тогда стартовый экран сразу правильного цвета.
     * Вызов пересоздаёт экран, поэтому выполняется только при смене режима.
     */
    private fun applyApplicationNightMode(themeMode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val preferences = (application as WorkOrderApp).container.themePreferences
        val applied = preferences.appliedNightMode()
        val target = when (themeMode) {
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
            // Для «Системы» режим не навязываем; сбрасываем, только если раньше навязали свой.
            ThemeMode.SYSTEM -> if (applied == ThemePreferences.UNSET) return else UiModeManager.MODE_NIGHT_AUTO
        }
        if (applied == target) return
        runCatching {
            (getSystemService(UI_MODE_SERVICE) as UiModeManager).setApplicationNightMode(target)
        }.onSuccess { preferences.setAppliedNightMode(target) }
    }

    override fun onStart() {
        super.onStart()
        if (BuildConfig.ENABLE_WEAR_SYNC) (application as WorkOrderApp).container.wearSyncManager.retryPendingEvents()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as WorkOrderApp).container
        val settingsRepository = container.settingsRepository

        // Оформление из базы придёт позже: до этого окно и первый кадр красим последним известным.
        val savedAppearance = container.themePreferences.read()
        applyApplicationNightMode(savedAppearance.themeMode)
        val systemInDarkTheme =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        window.setBackgroundDrawable(
            ColorDrawable(windowBackgroundColor(this, savedAppearance, systemInDarkTheme))
        )

        setContent {
            val settings by settingsRepository.observe()
                .collectAsStateWithLifecycle(initialValue = savedAppearance)

            LaunchedEffect(settings.themeMode, settings.themePreset, settings.dynamicColor, settings.customThemeColor, settings.interfaceScale) {
                container.themePreferences.write(settings)
                applyApplicationNightMode(settings.themeMode)
            }

            WorkOrderTheme(
                themeMode = settings.themeMode,
                themePresetName = settings.themePreset,
                dynamicColor = settings.dynamicColor,
                customThemeColor = settings.customThemeColor,
                interfaceScale = settings.interfaceScale
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
                                            label = { NavigationLabel(item.label) },
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
                        fun openTab(screen: Screen) {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
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
                                    WorkDayScreen(
                                        viewModel = vm,
                                        onNavigateBack = null,
                                        onNavigateToDate = { date ->
                                            if (date != today) navController.navigate(Screen.WorkDay.createRoute(date.toString()))
                                        },
                                        onOpenCatalog = { navController.navigate(Screen.Operations.route) },
                                        onOpenSettings = { openTab(Screen.Settings) }
                                    )
                                }
                            }

                            // Наряд на произвольную дату (из календаря и отчётов)
                            composable(
                                route = Screen.WorkDay.route,
                                arguments = listOf(navArgument("date") { type = NavType.StringType })
                            ) {
                                val vm = viewModel<WorkDayViewModel>(factory = WorkDayViewModel.Factory)
                                WorkDayScreen(
                                    viewModel = vm,
                                    onNavigateBack = { navController.popBackStack() },
                                    // Соседний день заменяет текущий: «Назад» ведёт туда, откуда открыли наряд.
                                    onNavigateToDate = { date ->
                                        navController.navigate(Screen.WorkDay.createRoute(date.toString())) {
                                            popUpTo(Screen.WorkDay.route) { inclusive = true }
                                        }
                                    },
                                    onOpenCatalog = { navController.navigate(Screen.Operations.route) }
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
                                ReportsScreen(
                                    viewModel = vm,
                                    onOpenDay = { date -> navController.navigate(Screen.WorkDay.createRoute(date.toString())) },
                                    onOpenSettings = { openTab(Screen.Settings) }
                                )
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

/**
 * Подпись вкладки всегда в одну строку: при крупном системном шрифте «Календарь» и «Настройки»
 * переносились по буквам. Рост шрифта здесь ограничен 115 %, сами иконки остаются прежними.
 */
@Composable
private fun NavigationLabel(text: String) {
    val fontScale = LocalDensity.current.fontScale
    val base = MaterialTheme.typography.labelMedium
    val style = if (fontScale > MAX_LABEL_SCALE) base.copy(fontSize = base.fontSize * (MAX_LABEL_SCALE / fontScale)) else base
    Text(text, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
}

private const val MAX_LABEL_SCALE = 1.15f
