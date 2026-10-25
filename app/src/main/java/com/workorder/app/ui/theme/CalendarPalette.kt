package com.workorder.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Hand-picked, single-family ramps: every channel decreases as attendance increases. */
fun calendarHeatPalette(preset: String, dynamicAccent: Color? = null): List<Color> {
    if (dynamicAccent != null) {
        val hue = hueOf(dynamicAccent)
        return listOf(0.08f to 0.98f, 0.22f to 0.91f, 0.40f to 0.79f,
            0.60f to 0.62f, 0.70f to 0.42f).map { (saturation, value) -> Color.hsv(hue, saturation, value) }
    }
    return when (preset) {
        "OCEAN" -> listOf(0xFFEDFBF9, 0xFFB4E7E0, 0xFF6AC5B9, 0xFF29988C, 0xFF15635D)
        "FOREST" -> listOf(0xFFECF8F0, 0xFFC2E8D0, 0xFF7FC7A0, 0xFF388965, 0xFF18583F)
        "AMBER" -> listOf(0xFFFFF6DE, 0xFFFBE1A0, 0xFFEDBF5B, 0xFFCA8D24, 0xFF89580F)
        "LAVENDER" -> listOf(0xFFF8F0FA, 0xFFE2CAEB, 0xFFC69BD7, 0xFF9A69B1, 0xFF67437C)
        else -> listOf(0xFFEFF4FD, 0xFFC7DAF6, 0xFF86B1E7, 0xFF4A7DBB, 0xFF284D7F)
    }.map { Color(it) }
}

/** Фон и текст дня, который по графику выходной. */
data class CalendarDayOffColors(val background: Color, val content: Color)

/**
 * Выходные окрашены зеленоватым — так они видны сразу, не сливаясь с обычным фоном.
 * Если сама тепловая шкала зелёная («Хвоя» или зелёный системный акцент), выходные
 * получают песочный оттенок, чтобы не путаться с отработанными днями.
 */
fun calendarDayOffColors(
    preset: String,
    dynamicAccent: Color? = null,
    darkTheme: Boolean = false
): CalendarDayOffColors {
    val heatIsGreen = if (dynamicAccent != null) hueOf(dynamicAccent) in 75f..175f else preset == "FOREST"
    return when {
        heatIsGreen && darkTheme -> CalendarDayOffColors(Color(0xFF3B2C1C), Color(0xFFF0D7BB))
        heatIsGreen -> CalendarDayOffColors(Color(0xFFF7E5CF), Color(0xFF5A3B1C))
        darkTheme -> CalendarDayOffColors(Color(0xFF1F3A2A), Color(0xFFC0E5CD))
        else -> CalendarDayOffColors(Color(0xFFDAF1E1), Color(0xFF1B4A31))
    }
}

/** Оттенок цвета в градусах HSV; для серого возвращается синий по умолчанию. */
internal fun hueOf(color: Color): Float {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    if (delta < 0.001f) return 215f
    val sector = when (max) {
        r -> (g - b) / delta
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    }
    return (sector * 60f + 360f) % 360f
}
