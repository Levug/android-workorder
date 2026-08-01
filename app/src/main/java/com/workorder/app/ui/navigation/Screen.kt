package com.workorder.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    // Вкладки нижней навигации
    data object Today : Screen("today")
    data object Calendar : Screen("calendar")
    data object Reports : Screen("reports")
    data object Settings : Screen("settings")

    // Детальные экраны
    data object WorkDay : Screen("day/{date}") {
        fun createRoute(date: String) = "day/$date"
    }

    data object Operations : Screen("operations")
}

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Today, Icons.Outlined.Today, Icons.Filled.Today, "Сегодня"),
    BottomNavItem(Screen.Calendar, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth, "Календарь"),
    BottomNavItem(Screen.Reports, Icons.Outlined.Insights, Icons.Filled.Insights, "Отчёты"),
    BottomNavItem(Screen.Settings, Icons.Outlined.Settings, Icons.Filled.Settings, "Настройки")
)

private val tabRootRoutes = setOf(
    Screen.Today.route,
    Screen.Calendar.route,
    Screen.Reports.route,
    Screen.Settings.route
)

fun shouldShowBottomBar(currentRoute: String?): Boolean =
    currentRoute in tabRootRoutes

fun getTabForRoute(currentRoute: String?): Screen? = when {
    currentRoute == null -> null
    currentRoute == Screen.Today.route -> Screen.Today
    currentRoute == Screen.Calendar.route || currentRoute.startsWith("day/") -> Screen.Calendar
    currentRoute == Screen.Reports.route -> Screen.Reports
    currentRoute == Screen.Settings.route || currentRoute == Screen.Operations.route -> Screen.Settings
    else -> null
}
