package com.workorder.app.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.domain.SalaryCalculator
import com.workorder.app.ui.theme.calendarHeatPalette
import com.workorder.app.ui.theme.supportsDynamicColor
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
    modifier: Modifier = Modifier,
    showSpecialization: Boolean = false
) {
    val today = LocalDate.now()
    val heatMax = maxOf(16.0, days.values.filter { it.isRecorded }.maxOfOrNull { it.totalHours } ?: 0.0)
    val accent = MaterialTheme.colorScheme.primary.takeIf { settings.dynamicColor && supportsDynamicColor }
    val palette = remember(settings.themePreset, accent) { calendarHeatPalette(settings.themePreset, accent) }
    val rotation by animateFloatAsState(
        targetValue = if (showSpecialization && settings.daySpecializationEnabled) 180f else 0f,
        animationSpec = tween(440), label = "calendarFlip"
    )
    val backVisible = rotation > 90f
    val cellHeight = if (settings.daySpecializationEnabled) 88.dp else 64.dp

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
                                palette = palette,
                                rotation = rotation,
                                showSpecialization = backVisible,
                                cellHeight = cellHeight * LocalDensity.current.fontScale.coerceAtLeast(1f),
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
        if (!backVisible) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("0 ч", style = MaterialTheme.typography.labelSmall)
            for (step in 0..4) {
                Box(Modifier.weight(1f).height(10.dp).clip(MaterialTheme.shapes.small)
                    .background(attendanceHeatColor(heatMax * step / 4, heatMax, palette)))
            }
            Text("${heatMax.formatNumber()} ч", style = MaterialTheme.typography.labelSmall)
        }
        Text("Темнее — больше фактических часов присутствия", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Специализации — план на день, без добавления часов. Нажми на день, чтобы изменить.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun attendanceHeatColor(hours: Double, maxHours: Double, heatColors: List<Color> = calendarHeatPalette("DEFAULT")): Color {
    val fraction = if (hours.isFinite() && maxHours.isFinite() && maxHours > 0)
        (hours / maxHours).toFloat().coerceIn(0f, 1f) else 0f
    val position = fraction * (heatColors.size - 1)
    val index = position.toInt().coerceAtMost(heatColors.lastIndex - 1)
    val blend = position - index
    val from = heatColors[index]
    val to = heatColors[index + 1]
    // Interpolation within one theme's ramp avoids mixing unrelated accent colours.
    fun channel(a: Float, b: Float) = a + (b - a) * blend
    return Color(channel(from.red, to.red), channel(from.green, to.green), channel(from.blue, to.blue))
}

@Composable
private fun DayCell(
    date: LocalDate,
    workDay: WorkDay?,
    heatMax: Double,
    palette: List<Color>,
    rotation: Float,
    showSpecialization: Boolean,
    cellHeight: androidx.compose.ui.unit.Dp,
    isToday: Boolean,
    isScheduledOff: Boolean,
    onClick: () -> Unit
) {
    val filled = workDay?.isRecorded == true
    val specialization = workDay?.specialization.orEmpty()
    val background = when {
        showSpecialization && specialization.isNotBlank() -> MaterialTheme.colorScheme.primaryContainer
        !showSpecialization && filled -> attendanceHeatColor(workDay!!.totalHours, heatMax, palette)
        isScheduledOff -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        showSpecialization && specialization.isNotBlank() -> MaterialTheme.colorScheme.onPrimaryContainer
        !showSpecialization && filled -> if (background.luminance() > 0.179f) Color.Black else Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(cellHeight)
            .testTag("calendar-day-$date")
            .graphicsLayer {
                // Switch faces edge-on; subtract 180° so the reverse text isn't mirrored.
                rotationY = if (showSpecialization) rotation - 180f else rotation
                cameraDistance = 12.dp.toPx()
            }
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .semantics(mergeDescendants = true) {
                contentDescription = "$date: " + (if (showSpecialization) {
                    specialization.ifBlank { "специализация не задана" }
                } else if (filled) "${workDay!!.totalHours.formatNumber()} фактических часов" else "нет наряда") +
                    (if (isScheduledOff) ", выходной" else "")
            }
            .then(
                if (isToday) Modifier.border(
                    width = 2.dp,
                    color = if (filled && !showSpecialization) contentColor else MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small
                ) else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            if (showSpecialization) {
                Text(specialization.ifBlank { "—" }, style = MaterialTheme.typography.labelSmall,
                    color = contentColor, maxLines = 3, minLines = 3, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else if (filled) {
                Text(
                    text = "${workDay!!.totalHours.formatNumber()}ч${if (isScheduledOff) " ×2" else ""}",
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
