package com.workorder.app.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class CalendarHeatTest {
    @Test fun moreHoursAlwaysMeansDarkerInEveryPalette() {
        listOf(Color(0xFFF2B544), Color(0xFF486AB4), Color(0xFF8DCAAC), Color.Black, Color.White).forEach { accent ->
            val luminances = (0..24).map { attendanceHeatColor(it.toDouble(), 24.0, accent).luminance() }
            luminances.zipWithNext().forEach { (before, after) -> assertTrue("$before -> $after", after < before) }
        }
    }

    @Test fun chosenTextHasAccessibleContrast() {
        for (hour in 0..24) {
            val luminance = attendanceHeatColor(hour.toDouble(), 24.0, Color(0xFFF2B544)).luminance()
            val contrast = if (luminance > 0.179f) (luminance + 0.05f) / 0.05f else 1.05f / (luminance + 0.05f)
            assertTrue(contrast >= 4.5f)
        }
    }
}
