package com.workorder.app.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.workorder.app.ui.theme.calendarHeatPalette
import org.junit.Assert.*
import org.junit.Test

class CalendarHeatTest {
    private val palettes = listOf("DEFAULT", "OCEAN", "FOREST", "AMBER", "LAVENDER").map { calendarHeatPalette(it) } +
        listOf(Color.Red, Color.Green, Color.Blue, Color.Gray, Color(0xFFAE72CE)).map { calendarHeatPalette("DEFAULT", it) }

    @Test fun moreHoursAlwaysMeansDarker() {
        palettes.forEach { palette ->
        listOf(16.0, 24.0).forEach { maximum ->
            val luminances = (0..100).map { attendanceHeatColor(it * maximum / 100, maximum, palette).luminance() }
            luminances.zipWithNext().forEach { (before, after) -> assertTrue("$before -> $after", after < before) }
        }
        }
    }

    @Test fun chosenTextHasAccessibleContrast() {
        palettes.forEach { palette ->
        for (step in 0..240) {
            val luminance = attendanceHeatColor(step / 10.0, 24.0, palette).luminance()
            val contrast = if (luminance > 0.179f) (luminance + 0.05f) / 0.05f else 1.05f / (luminance + 0.05f)
            assertTrue(contrast >= 4.5f)
        }
        }
    }

    @Test fun invalidHoursAreSafeAndEveryPresetHasItsOwnRamp() {
        assertEquals(5, palettes.take(5).distinct().size)
        palettes.forEach { palette ->
            listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
                assertEquals(palette.first(), attendanceHeatColor(it, 16.0, palette))
            }
            assertEquals(palette.last(), attendanceHeatColor(100.0, 16.0, palette))
            assertEquals(palette.first(), attendanceHeatColor(5.0, 0.0, palette))
        }
    }
}
