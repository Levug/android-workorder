package com.workorder.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.domain.SalaryCalculator
import com.workorder.app.util.formatNumber
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val weekDayLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/**
 * Сетка месяца с понедельника. Дни с нарядами подсвечены и показывают часы.
 */
@Composable
fun CalendarGrid(
    month: YearMonth,
    days: Map<LocalDate, WorkDay>,
    settings: Settings,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val heatMax = maxOf(16.0, days.values.maxOfOrNull { it.totalHours } ?: 0.0)

    val weeks: List<List<LocalDate?>> = remember(month) {
        val first = month.atDay(1)
        val leadingEmpty = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val cells = buildList<LocalDate?> {
            repeat(leadingEmpty) { add(null) }
            for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
            while (size % 7 != 0) add(null)
        }
        cells.chunked(7)
    }

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDayLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index >= 5) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        weeks.forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                week.forEach { date ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (date != null) {
                            DayCell(
                                date = date,
                                workDay = days[date],
                                heatMax = heatMax,
                                isScheduledOff = when (days[date]?.dayType) {
                                    WorkDayType.HOLIDAY -> true
                                    WorkDayType.REGULAR -> false
                                    else -> !SalaryCalculator.isScheduledWorkDay(date, settings)
                                },
                                isToday = date == today,
                                onClick = { onDayClick(date) }
                            )
                        } else {
                            Spacer(Modifier.aspectRatio(1f))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("0 ч", style = MaterialTheme.typography.labelSmall)
            for (step in 0..4) {
                Box(Modifier.weight(1f).height(10.dp).clip(MaterialTheme.shapes.small)
                    .background(attendanceHeatColor(heatMax * step / 4, heatMax, MaterialTheme.colorScheme.primary)))
            }
            Text("${heatMax.formatNumber()} ч", style = MaterialTheme.typography.labelSmall)
        }
        Text("Темнее — больше фактических часов присутствия", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun attendanceHeatColor(hours: Double, maxHours: Double, accent: Color): Color {
    val fraction = if (hours.isFinite() && maxHours.isFinite() && maxHours > 0)
        (hours / maxHours).toFloat().coerceIn(0f, 1f) else 0f
    // Every RGB channel decreases with attendance; do not interpolate via Oklab,
    // which can clip channels at the ends of some palettes.
    fun channel(value: Float): Float {
        val light = 0.88f + value * 0.12f
        val dark = value * 0.35f
        return light + (dark - light) * fraction
    }
    return Color(channel(accent.red), channel(accent.green), channel(accent.blue))
}

@Composable
private fun DayCell(
    date: LocalDate,
    workDay: WorkDay?,
    heatMax: Double,
    isToday: Boolean,
    isScheduledOff: Boolean,
    onClick: () -> Unit
) {
    val filled = workDay != null
    val background = when {
        filled -> attendanceHeatColor(workDay!!.totalHours, heatMax, MaterialTheme.colorScheme.primary)
        isScheduledOff -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        filled -> if (background.luminance() > 0.179f) Color.Black else Color.White
        isScheduledOff -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .semantics(mergeDescendants = true) {
                contentDescription = "$date: " + (workDay?.let { "${it.totalHours.formatNumber()} фактических часов" }
                    ?: "нет наряда") + if (isScheduledOff) ", выходной" else ""
            }
            .then(
                if (isToday) Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            if (workDay != null) {
                Text(
                    text = "${workDay.totalHours.formatNumber()}ч${if (isScheduledOff) " ×2" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            } else if (isScheduledOff) {
                Text(
                    text = "вых",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor
                )
            }
        }
    }
}
