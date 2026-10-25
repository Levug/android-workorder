package com.workorder.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Своя тема из одного цвета. Тон Material 3 — это светлота L* из CIELAB, поэтому тональные ряды
 * строятся в LCh: оттенок и насыщенность берутся из выбранного цвета, светлота — по ролям M3
 * (40/100/90/10 в светлой схеме, 80/20/30/90 в тёмной). Так контраст текста не зависит от того,
 * насколько светлый или тёмный цвет выбран.
 */

/** Цвет в цилиндрических координатах CIELAB: светлота 0…100, насыщенность, оттенок в градусах. */
internal data class Lch(val lightness: Double, val chroma: Double, val hue: Double)

/** Ряд одного оттенка и насыщенности: tone(40) — тот же цвет со светлотой 40. */
internal class TonalPalette(private val hue: Double, private val chroma: Double) {
    fun tone(lightness: Int): Int = lchToArgbInGamut(lightness.toDouble(), chroma, hue)
}

internal fun argbToLch(argb: Int): Lch {
    val r = linearize((argb shr 16) and 0xFF)
    val g = linearize((argb shr 8) and 0xFF)
    val b = linearize(argb and 0xFF)
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / WHITE_X
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / WHITE_Z
    val fx = labF(x)
    val fy = labF(y)
    val fz = labF(z)
    val a = 500.0 * (fx - fy)
    val bb = 200.0 * (fy - fz)
    val hue = (Math.toDegrees(atan2(bb, a)) + 360.0) % 360.0
    return Lch(116.0 * fy - 16.0, hypot(a, bb), hue)
}

/**
 * Самый насыщенный цвет заданной светлоты и оттенка, не выходящий за sRGB:
 * насыщенность уменьшается, пока цвет не станет отображаемым.
 */
internal fun lchToArgbInGamut(lightness: Double, chroma: Double, hue: Double): Int {
    if (lightness <= 0.0) return 0xFF000000.toInt()
    if (lightness >= 100.0) return 0xFFFFFFFF.toInt()
    lchToLinear(lightness, chroma, hue)?.let { return linearToArgb(it) }
    var low = 0.0
    var high = chroma
    repeat(24) {
        val mid = (low + high) / 2
        if (lchToLinear(lightness, mid, hue) != null) low = mid else high = mid
    }
    return linearToArgb(lchToLinear(lightness, low, hue) ?: doubleArrayOf(0.0, 0.0, 0.0))
}

/** Линейные RGB или null, если цвет вне sRGB. */
private fun lchToLinear(lightness: Double, chroma: Double, hue: Double): DoubleArray? {
    val radians = Math.toRadians(hue)
    val fy = (lightness + 16.0) / 116.0
    val fx = fy + chroma * cos(radians) / 500.0
    val fz = fy - chroma * sin(radians) / 200.0
    val x = labFInverse(fx) * WHITE_X
    val y = if (lightness > KAPPA * EPSILON) fy * fy * fy else lightness / KAPPA
    val z = labFInverse(fz) * WHITE_Z
    val rgb = doubleArrayOf(
        3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
        -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
        0.0556434 * x - 0.2040259 * y + 1.0572252 * z
    )
    return rgb.takeIf { channels -> channels.all { it in -GAMUT_SLACK..1.0 + GAMUT_SLACK } }
}

private fun linearToArgb(rgb: DoubleArray): Int {
    fun channel(value: Double): Int {
        val v = value.coerceIn(0.0, 1.0)
        val encoded = if (v <= 0.0031308) 12.92 * v else 1.055 * v.pow(1.0 / 2.4) - 0.055
        return (encoded * 255.0).roundToInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or (channel(rgb[0]) shl 16) or (channel(rgb[1]) shl 8) or channel(rgb[2])
}

private fun linearize(channel: Int): Double {
    val c = channel / 255.0
    return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
}

private fun labF(t: Double): Double = if (t > EPSILON) cbrt(t) else (KAPPA * t + 16.0) / 116.0

private fun labFInverse(f: Double): Double {
    val cube = f * f * f
    return if (cube > EPSILON) cube else (116.0 * f - 16.0) / KAPPA
}

private const val EPSILON = 216.0 / 24389.0
private const val KAPPA = 24389.0 / 27.0
private const val WHITE_X = 0.95047
private const val WHITE_Z = 1.08883
private const val GAMUT_SLACK = 1e-4

/** Ряды ролей M3 для выбранного цвета. Серый цвет даёт монохромную тему. */
internal class SeedPalettes(seed: Int) {
    private val lch = argbToLch(seed)
    val primary = TonalPalette(lch.hue, lch.chroma)
    val secondary = TonalPalette(lch.hue, minOf(lch.chroma * 0.35, 18.0))
    val tertiary = TonalPalette((lch.hue + 60.0) % 360.0, minOf(lch.chroma * 0.5, 30.0))
    val neutral = TonalPalette(lch.hue, minOf(lch.chroma * 0.08, 5.0))
    val neutralVariant = TonalPalette(lch.hue, minOf(lch.chroma * 0.14, 9.0))
}

/** Полная светлая или тёмная схема Material 3 из исходного цвета ARGB. */
fun seedColorScheme(seed: Int, dark: Boolean): ColorScheme {
    val p = SeedPalettes(seed)
    fun Int.c() = Color(this)
    return if (!dark) lightColorScheme(
        primary = p.primary.tone(40).c(),
        onPrimary = p.primary.tone(100).c(),
        primaryContainer = p.primary.tone(90).c(),
        onPrimaryContainer = p.primary.tone(10).c(),
        inversePrimary = p.primary.tone(80).c(),
        secondary = p.secondary.tone(40).c(),
        onSecondary = p.secondary.tone(100).c(),
        secondaryContainer = p.secondary.tone(90).c(),
        onSecondaryContainer = p.secondary.tone(10).c(),
        tertiary = p.tertiary.tone(40).c(),
        onTertiary = p.tertiary.tone(100).c(),
        tertiaryContainer = p.tertiary.tone(90).c(),
        onTertiaryContainer = p.tertiary.tone(10).c(),
        background = p.neutral.tone(98).c(),
        onBackground = p.neutral.tone(10).c(),
        surface = p.neutral.tone(98).c(),
        onSurface = p.neutral.tone(10).c(),
        surfaceVariant = p.neutralVariant.tone(90).c(),
        onSurfaceVariant = p.neutralVariant.tone(30).c(),
        surfaceTint = p.primary.tone(40).c(),
        inverseSurface = p.neutral.tone(20).c(),
        inverseOnSurface = p.neutral.tone(95).c(),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = p.neutralVariant.tone(50).c(),
        outlineVariant = p.neutralVariant.tone(80).c(),
        surfaceBright = p.neutral.tone(98).c(),
        surfaceDim = p.neutral.tone(87).c(),
        surfaceContainerLowest = p.neutral.tone(100).c(),
        surfaceContainerLow = p.neutral.tone(96).c(),
        surfaceContainer = p.neutral.tone(94).c(),
        surfaceContainerHigh = p.neutral.tone(92).c(),
        surfaceContainerHighest = p.neutral.tone(90).c()
    ) else darkColorScheme(
        primary = p.primary.tone(80).c(),
        onPrimary = p.primary.tone(20).c(),
        primaryContainer = p.primary.tone(30).c(),
        onPrimaryContainer = p.primary.tone(90).c(),
        inversePrimary = p.primary.tone(40).c(),
        secondary = p.secondary.tone(80).c(),
        onSecondary = p.secondary.tone(20).c(),
        secondaryContainer = p.secondary.tone(30).c(),
        onSecondaryContainer = p.secondary.tone(90).c(),
        tertiary = p.tertiary.tone(80).c(),
        onTertiary = p.tertiary.tone(20).c(),
        tertiaryContainer = p.tertiary.tone(30).c(),
        onTertiaryContainer = p.tertiary.tone(90).c(),
        background = p.neutral.tone(6).c(),
        onBackground = p.neutral.tone(90).c(),
        surface = p.neutral.tone(6).c(),
        onSurface = p.neutral.tone(90).c(),
        surfaceVariant = p.neutralVariant.tone(30).c(),
        onSurfaceVariant = p.neutralVariant.tone(80).c(),
        surfaceTint = p.primary.tone(80).c(),
        inverseSurface = p.neutral.tone(90).c(),
        inverseOnSurface = p.neutral.tone(20).c(),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        outline = p.neutralVariant.tone(60).c(),
        outlineVariant = p.neutralVariant.tone(30).c(),
        surfaceBright = p.neutral.tone(24).c(),
        surfaceDim = p.neutral.tone(6).c(),
        surfaceContainerLowest = p.neutral.tone(4).c(),
        surfaceContainerLow = p.neutral.tone(10).c(),
        surfaceContainer = p.neutral.tone(12).c(),
        surfaceContainerHigh = p.neutral.tone(17).c(),
        surfaceContainerHighest = p.neutral.tone(22).c()
    )
}
