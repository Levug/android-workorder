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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.data.model.fundHours
import com.workorder.app.data.model.WorkSchedule
import com.workorder.app.domain.SalaryCalculator
import com.workorder.app.ui.theme.CalendarDayOffColors
import com.workorder.app.ui.theme.calendarDayOffColors
import com.workorder.app.ui.theme.calendarHeatPalette
import com.workorder.app.ui.theme.customAccent
import com.workorder.app.util.formatFull
import com.workorder.app.util.formatNumber
import com.workorder.app.util.noOrphans
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
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val heatMax = maxOf(16.0, days.values.maxOfOrNull { it.fundHours } ?: 0.0)
    val accent = customAccent(settings, MaterialTheme.colorScheme.primary)
    val palette = remember(settings.themePreset, accent) { calendarHeatPalette(settings.themePreset, accent) }
    val rotation by animateFloatAsState(
        targetValue = if (showSpecialization && settings.daySpecializationEnabled) 180f else 0f,
        animationSpec = tween(440), label = "calendarFlip"
    )
    val backVisible = rotation > 90f
    // Плитки квадратные и одинаковые всегда: включение специализаций не должно вытягивать календарь.
    // На узком экране или при крупном шрифте плитка вытягивается ровно настолько, чтобы влез текст.
    val minCellHeight = 46.dp * LocalDensity.current.fontScale.coerceAtLeast(1f)
    val dayOff = remember(settings.themePreset, accent, darkTheme) {
        calendarDayOffColors(settings.themePreset, accent, darkTheme)
    }

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
                    // При 5/2 суббота и воскресенье подписаны цветом выходных, при 2/2 выходные плавают.
                    color = if (index >= 5 && settings.workSchedule == WorkSchedule.FIVE_TWO) dayOff.content
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
                                heatEnabled = settings.calendarHeatEnabled,
                                dayOff = dayOff,
                                rotation = rotation,
                                showSpecialization = backVisible,
                                minHeight = minCellHeight,
                                isScheduledOff = when (days[date]?.dayType) {
                                    WorkDayType.HOLIDAY, WorkDayType.COMP_WORK, WorkDayType.UNPAID -> true
                                    WorkDayType.REGULAR, WorkDayType.COMP_OFF -> false
                                    else -> !SalaryCalculator.isScheduledWorkDay(date, settings)
                                },
                                isToday = date == today,
                                onClick = { onDayClick(date) }
                            )
                        } else {
                            Spacer(Modifier.squareAtLeast(minCellHeight))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!backVisible) {
        // Шкала часов — только когда включена цветовая индикация (эксперимент «Цвета часов в календаре»).
        if (settings.calendarHeatEnabled) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("0 ч", style = MaterialTheme.typography.labelSmall)
            for (step in 0..4) {
                Box(Modifier.weight(1f).height(10.dp).clip(MaterialTheme.shapes.small)
                    .background(attendanceHeatColor(heatMax * step / 4, heatMax, palette)))
            }
            Text("${heatMax.formatNumber()} ч", style = MaterialTheme.typography.labelSmall)
        }
        // Пояснение отдельной строкой, образцы цветов — ниже: рядом с образцом длинный текст переносился по слову.
        if (settings.calendarHeatEnabled) Text("Темнее — больше часов за день".noOrphans(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!settings.calendarHeatEnabled) LegendSwatch(MaterialTheme.colorScheme.primaryContainer, "смена")
            LegendSwatch(dayOff.background, "выходной")
            LegendSwatch(MaterialTheme.colorScheme.tertiaryContainer, "выход за отгул")
        }
        } else {
            Text("Специализации — план на день, без добавления часов. Нажми на день, чтобы изменить.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(MaterialTheme.shapes.extraSmall).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Подпись часов в плитке: тип дня виден без открытия наряда. */
private fun hoursLabel(day: WorkDay, isScheduledOff: Boolean): String {
    val hours = "${day.totalHours.formatNumber()}ч"
    return when (day.dayType) {
        // Часы выхода за отгул видны по цвету плитки, подпись — короткая, чтобы влезть в квадрат.
        WorkDayType.COMP_WORK -> "→отг"
        WorkDayType.COMP_OFF -> "отгул"
        WorkDayType.MANUAL -> "$hours✎"
        WorkDayType.UNPAID -> "${hours}·БС"
        // Отгул на часть дня: «4+4отг»; если не влезает — сумма часов.
        else -> if (day.compOffHours > 0) {
            "${day.totalHours.formatNumber()}+${day.compOffHours.formatNumber()}отг".takeIf { it.length <= 7 }
                ?: "${(day.totalHours + day.compOffHours).formatNumber()}ч"
        } else if (isScheduledOff) "$hours ×2" else hours
    }
}

/** Квадрат по ширине ячейки, но не ниже [minHeight], чтобы текст не обрезался. */
private fun Modifier.squareAtLeast(minHeight: Dp): Modifier = layout { measurable, constraints ->
    val width = constraints.maxWidth
    val height = maxOf(width, minHeight.roundToPx())
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.place(0, 0) }
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
    heatEnabled: Boolean,
    dayOff: CalendarDayOffColors,
    rotation: Float,
    showSpecialization: Boolean,
    minHeight: Dp,
    isToday: Boolean,
    isScheduledOff: Boolean,
    onClick: () -> Unit
) {
    val filled = workDay?.isRecorded == true
    // Выход за отгул не входит в часы месяца: своя заливка вместо тепловой шкалы.
    val compWork = !showSpecialization && filled && workDay!!.dayType == WorkDayType.COMP_WORK
    val specialization = workDay?.specialization.orEmpty()
    val background = when {
        showSpecialization && specialization.isNotBlank() -> MaterialTheme.colorScheme.primaryContainer
        compWork -> MaterialTheme.colorScheme.tertiaryContainer
        !showSpecialization && filled && !heatEnabled -> MaterialTheme.colorScheme.primaryContainer
        !showSpecialization && filled -> attendanceHeatColor(workDay!!.fundHours, heatMax, palette)
        isScheduledOff -> dayOff.background
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = when {
        showSpecialization && specialization.isNotBlank() -> MaterialTheme.colorScheme.onPrimaryContainer
        compWork -> MaterialTheme.colorScheme.onTertiaryContainer
        !showSpecialization && filled && !heatEnabled -> MaterialTheme.colorScheme.onPrimaryContainer
        !showSpecialization && filled -> if (background.luminance() > 0.179f) Color.Black else Color.White
        isScheduledOff -> dayOff.content
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .squareAtLeast(minHeight)
            .testTag("calendar-day-$date")
            .graphicsLayer {
                // Switch faces edge-on; subtract 180° so the reverse text isn't mirrored.
                rotationY = if (showSpecialization) rotation - 180f else rotation
                cameraDistance = 12.dp.toPx()
            }
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .semantics(mergeDescendants = true) {
                contentDescription = "${date.formatFull()}: " + (if (showSpecialization) {
                    specialization.ifBlank { "специализация не задана" }
                } else if (filled) "${workDay!!.totalHours.formatNumber()} фактических часов" + dayTypeSpoken(workDay.dayType)
                else "нет наряда") +
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
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            if (showSpecialization) {
                // Две строки мелким шрифтом помещаются в ту же плитку; полный текст — в списке под календарём.
                Text(specialization.ifBlank { "—" },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    color = contentColor, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            } else if (filled) {
                Text(
                    text = hoursLabel(workDay!!, isScheduledOff),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    maxLines = 1
                )
            } else if (workDay?.dayType == WorkDayType.COMP_WORK) {
                Text(text = "→отг", style = MaterialTheme.typography.labelSmall, color = contentColor)
            } else if (workDay?.dayType == WorkDayType.UNPAID) {
                Text(text = "БС", style = MaterialTheme.typography.labelSmall, color = contentColor)
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

private fun dayTypeSpoken(type: WorkDayType): String = when (type) {
    WorkDayType.COMP_WORK -> ", выход за отгул"
    WorkDayType.COMP_OFF -> ", отгул"
    WorkDayType.MANUAL -> ", ручной режим"
    WorkDayType.UNPAID -> ", без содержания"
    else -> ""
}
