package com.workorder.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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

@Composable
fun WorkOrderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    themePresetName: String = "DEFAULT",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && supportsDynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            val preset = getThemePreset(themePresetName)
            if (darkTheme) preset.darkScheme else preset.lightScheme
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WorkOrderTypography,
        shapes = WorkOrderShapes,
        content = content
    )
}
