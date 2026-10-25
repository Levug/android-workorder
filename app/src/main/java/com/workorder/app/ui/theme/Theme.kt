package com.workorder.app.ui.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.workorder.app.data.model.CUSTOM_THEME_PRESET
import com.workorder.app.data.model.DEFAULT_CUSTOM_THEME_COLOR
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.ThemeMode

data class ThemePreset(
    val name: String,
    val displayName: String,
    val previewColor: Color,
    val lightScheme: ColorScheme,
    val darkScheme: ColorScheme
)

val themePresets = listOf(
    ThemePreset("DEFAULT", "Сапфир", Color(0xFF415F91), IndigoLight, IndigoDark),
    ThemePreset("OCEAN", "Бирюза", Color(0xFF006A6A), OceanLight, OceanDark),
    ThemePreset("FOREST", "Хвоя", Color(0xFF2F6B4F), ForestLight, ForestDark),
    ThemePreset("AMBER", "Янтарь", Color(0xFF8B5000), AmberLight, AmberDark),
    ThemePreset("LAVENDER", "Слива", Color(0xFF73558D), LavenderLight, LavenderDark)
)

fun getThemePreset(name: String): ThemePreset =
    themePresets.find { it.name == name } ?: themePresets.first()

/** Поддержка Dynamic Color (Material You) доступна с Android 12. */
val supportsDynamicColor: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

fun isDarkTheme(themeMode: ThemeMode, systemInDarkTheme: Boolean): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** Схема пресета или своей темы (без цветов из обоев). */
fun presetColorScheme(presetName: String, customThemeColor: Int, dark: Boolean): ColorScheme =
    if (presetName == CUSTOM_THEME_PRESET) seedColorScheme(customThemeColor, dark)
    else getThemePreset(presetName).let { if (dark) it.darkScheme else it.lightScheme }

/** Акцент для тепловой шкалы календаря, если тема не из готовых пресетов. */
fun customAccent(settings: Settings, primary: Color): Color? = when {
    settings.dynamicColor && supportsDynamicColor -> primary
    settings.themePreset == CUSTOM_THEME_PRESET -> Color(settings.customThemeColor)
    else -> null
}

fun colorSchemeFor(context: Context, settings: Settings, systemInDarkTheme: Boolean): ColorScheme {
    val dark = isDarkTheme(settings.themeMode, systemInDarkTheme)
    return if (settings.dynamicColor && supportsDynamicColor) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        presetColorScheme(settings.themePreset, settings.customThemeColor, dark)
    }
}

/**
 * Цвет окна до первого кадра интерфейса. Тема окна из XML знает только системный режим,
 * поэтому при своей теме приложения окно перекрашивается здесь.
 */
fun windowBackgroundColor(context: Context, settings: Settings, systemInDarkTheme: Boolean): Int =
    colorSchemeFor(context, settings, systemInDarkTheme).background.toArgb()

@Composable
fun WorkOrderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themePresetName: String = "DEFAULT",
    dynamicColor: Boolean = false,
    customThemeColor: Int = DEFAULT_CUSTOM_THEME_COLOR,
    interfaceScale: Double = 1.0,
    content: @Composable () -> Unit
) {
    val darkTheme = isDarkTheme(themeMode, isSystemInDarkTheme())

    val colorScheme = when {
        dynamicColor && supportsDynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> remember(themePresetName, customThemeColor, darkTheme) {
            presetColorScheme(themePresetName, customThemeColor, darkTheme)
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val density = LocalDensity.current
    val scale = if (interfaceScale.isFinite()) interfaceScale.coerceIn(0.85, 1.3).toFloat() else 1f
    CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
      MaterialTheme(
        colorScheme = colorScheme,
        typography = WorkOrderTypography,
        shapes = WorkOrderShapes,
        content = content
      )
    }
}
