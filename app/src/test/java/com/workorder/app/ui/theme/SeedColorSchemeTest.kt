package com.workorder.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.workorder.app.ui.component.parseHexColor
import com.workorder.app.ui.component.toHexColor
import org.junit.Assert.*
import org.junit.Test

class SeedColorSchemeTest {
    private val seeds = listOf(0xFF415F91, 0xFFE53935, 0xFFFFEB3B, 0xFF00FF00, 0xFF0000FF, 0xFF757575, 0xFF000000, 0xFFFFFFFF)
        .map { it.toInt() }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    @Test fun tonesKeepRequestedLightnessAndHue() {
        seeds.forEach { seed ->
            val source = argbToLch(seed)
            val palette = TonalPalette(source.hue, source.chroma)
            listOf(10, 40, 80, 90).forEach { tone ->
                val lch = argbToLch(palette.tone(tone))
                assertEquals("seed ${seed.toHexColor()} tone $tone", tone.toDouble(), lch.lightness, 1.0)
                if (source.chroma > 20 && lch.chroma > 10) {
                    val hueDiff = Math.abs(lch.hue - source.hue).let { minOf(it, 360 - it) }
                    assertTrue("seed ${seed.toHexColor()} tone $tone hue drift $hueDiff", hueDiff < 8)
                }
            }
        }
    }

    @Test fun lchRoundTripIsLossless() {
        seeds.forEach { seed ->
            val lch = argbToLch(seed)
            assertEquals(seed, lchToArgbInGamut(lch.lightness, lch.chroma, lch.hue))
        }
    }

    @Test fun textOnEveryRoleIsReadableInBothModes() {
        seeds.forEach { seed ->
            listOf(false, true).forEach { dark ->
                val scheme = seedColorScheme(seed, dark)
                listOf(
                    scheme.primary to scheme.onPrimary,
                    scheme.primaryContainer to scheme.onPrimaryContainer,
                    scheme.secondaryContainer to scheme.onSecondaryContainer,
                    scheme.tertiaryContainer to scheme.onTertiaryContainer,
                    scheme.surface to scheme.onSurface,
                    scheme.surfaceContainer to scheme.onSurfaceVariant
                ).forEach { (background, text) ->
                    val ratio = contrast(background, text)
                    assertTrue("seed ${seed.toHexColor()} dark=$dark contrast $ratio", ratio >= 4.5)
                }
            }
        }
    }

    @Test fun graySeedGivesMonochromeScheme() {
        val scheme = seedColorScheme(0xFF808080.toInt(), dark = false)
        val primary = argbToLch(scheme.primary.toArgb())
        assertTrue(primary.chroma < 1.0)
    }

    @Test fun hexInputIsParsedStrictly() {
        assertEquals(0xFF3A6EA5.toInt(), parseHexColor("#3a6ea5"))
        assertEquals(0xFF3A6EA5.toInt(), parseHexColor(" 3A6EA5 "))
        listOf("", "#", "#12345", "#1234567", "#GGGGGG", "red").forEach { assertNull(it, parseHexColor(it)) }
        assertEquals("#3A6EA5", 0xFF3A6EA5.toInt().toHexColor())
    }
}
