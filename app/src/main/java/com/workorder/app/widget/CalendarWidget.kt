package com.workorder.app.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.workorder.app.R
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.data.model.fundHours
import com.workorder.app.domain.SalaryCalculator
import com.workorder.app.ui.component.attendanceHeatColor
import com.workorder.app.ui.theme.calendarDayOffColors
import com.workorder.app.ui.theme.calendarHeatPalette
import com.workorder.app.ui.theme.colorSchemeFor
import com.workorder.app.ui.theme.customAccent
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class CalendarWidgetProvider : DayWidgetProvider()

const val ACTION_WIDGET_DATE = "com.workorder.app.widget.DATE"
const val ACTION_WIDGET_CALENDAR = "com.workorder.app.widget.CALENDAR"

internal fun calendarWidgetIntent(context: Context, date: LocalDate? = null): Intent =
    widgetIntent(context, false).apply {
        action = if (date == null) ACTION_WIDGET_CALENDAR else ACTION_WIDGET_DATE
        // Дата участвует в идентичности PendingIntent, поэтому соседние дни не перезаписывают друг друга.
        data = date?.let { Uri.parse("workorder-widget://day/$it") }
    }

fun Intent.widgetDate(): LocalDate? = if (action == ACTION_WIDGET_DATE && data?.scheme == "workorder-widget" && data?.host == "day")
    runCatching { LocalDate.parse(data?.lastPathSegment) }.getOrNull() else null

fun Intent.widgetAction(): String? = action?.takeIf {
    it == ACTION_WIDGET_TODAY || it == ACTION_WIDGET_ADD || it == ACTION_WIDGET_CALENDAR ||
        (it == ACTION_WIDGET_DATE && widgetDate() != null)
}

/** Понедельник первым; шесть строк вмещают любой месяц, включая високосный февраль. */
internal fun calendarWidgetDates(month: YearMonth): List<LocalDate?> {
    val offset = month.atDay(1).dayOfWeek.value - 1
    return List(42) { index ->
        val day = index - offset + 1
        if (day in 1..month.lengthOfMonth()) month.atDay(day) else null
    }
}

/** Короткие подписи для семи узких столбцов; полное пояснение есть у ячейки для TalkBack. */
internal fun calendarWidgetHoursLabel(day: WorkDay, off: Boolean): String {
    val format = DecimalFormat("0.##", DecimalFormatSymbols(Locale("ru")))
    val hours = format.format(day.totalHours)
    return when (day.dayType) {
        WorkDayType.COMP_WORK -> "→отг"
        WorkDayType.COMP_OFF -> "отг"
        WorkDayType.UNPAID -> if (day.totalHours == 0.0) "БС" else "$hours·БС"
        WorkDayType.SICK_LEAVE -> "бол."
        WorkDayType.MANUAL -> "$hours✎"
        else -> if (day.compOffHours > 0) {
            "$hours+${format.format(day.compOffHours)}".takeIf { it.length <= 5 }
                ?: "Σ${format.format(day.totalHours + day.compOffHours)}"
        } else if (off) "$hours×2" else hours
    }
}

internal fun renderCalendarWidget(
    context: Context, month: YearMonth, today: LocalDate, days: List<WorkDay>, settings: Settings
): RemoteViews {
    val systemDark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val colors = colorSchemeFor(context, settings, systemDark)
    val accent = customAccent(settings, colors.primary)
    val palette = calendarHeatPalette(settings.themePreset, accent)
    val dayOff = calendarDayOffColors(settings.themePreset, accent, colors.background.luminance() < 0.5f)
    val byDate = days.associateBy { it.date }
    val heatMax = maxOf(16.0, days.maxOfOrNull { it.fundHours } ?: 0.0)
    val ru = Locale("ru")
    val monthTitle = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", ru)).replaceFirstChar { it.titlecase(ru) }
    fun pending(date: LocalDate? = null) = PendingIntent.getActivity(context, 2,
        calendarWidgetIntent(context, date), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    return RemoteViews(context.packageName, R.layout.widget_calendar).apply {
        setInt(R.id.calendar_background, "setColorFilter", colors.surfaceContainer.toArgb())
        setTextViewText(R.id.calendar_title, monthTitle)
        setTextColor(R.id.calendar_title, colors.onSurface.toArgb())
        setTextColor(R.id.calendar_hint, colors.onSurfaceVariant.toArgb())
        setOnClickPendingIntent(R.id.calendar_title, pending())
        removeAllViews(R.id.calendar_weekdays)
        listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { label ->
            addView(R.id.calendar_weekdays, RemoteViews(context.packageName, R.layout.widget_calendar_weekday).apply {
                setTextViewText(R.id.calendar_weekday, label)
                setTextColor(R.id.calendar_weekday, colors.onSurfaceVariant.toArgb())
            })
        }
        removeAllViews(R.id.calendar_weeks)
        calendarWidgetDates(month).chunked(7).forEach { week ->
            val row = RemoteViews(context.packageName, R.layout.widget_calendar_week)
            week.forEach { date ->
                val cell = RemoteViews(context.packageName, R.layout.widget_calendar_cell)
                if (date == null) {
                    cell.setViewVisibility(R.id.calendar_cell, View.INVISIBLE)
                } else {
                    val day = byDate[date.toString()]
                    val filled = day?.isRecorded == true
                    val sickLeave = day?.dayType == WorkDayType.SICK_LEAVE
                    val off = when (day?.dayType) {
                        WorkDayType.HOLIDAY, WorkDayType.COMP_WORK, WorkDayType.UNPAID -> true
                        WorkDayType.REGULAR, WorkDayType.COMP_OFF -> false
                        else -> !SalaryCalculator.isScheduledWorkDay(date, settings)
                    }
                    val compWork = filled && day!!.dayType == WorkDayType.COMP_WORK
                    val background = when {
                        compWork || sickLeave -> colors.tertiaryContainer
                        filled && settings.calendarHeatEnabled -> attendanceHeatColor(day!!.fundHours, heatMax, palette)
                        filled -> colors.primaryContainer
                        off -> dayOff.background
                        else -> colors.surface
                    }
                    val foreground = when {
                        compWork || sickLeave -> colors.onTertiaryContainer
                        filled && settings.calendarHeatEnabled -> if (background.luminance() > 0.179f) Color.Black else Color.White
                        filled -> colors.onPrimaryContainer
                        off -> dayOff.content
                        else -> colors.onSurface
                    }
                    val label = if (filled || sickLeave) calendarWidgetHoursLabel(day!!, off) else if (day != null) "план" else ""
                    cell.setTextViewText(R.id.calendar_number, date.dayOfMonth.toString())
                    cell.setTextViewText(R.id.calendar_hours, label)
                    cell.setTextColor(R.id.calendar_number, foreground.toArgb())
                    cell.setTextColor(R.id.calendar_hours, foreground.toArgb())
                    cell.setInt(R.id.calendar_cell_background, "setColorFilter", background.toArgb())
                    cell.setInt(R.id.calendar_today_border, "setColorFilter", colors.primary.toArgb())
                    cell.setViewVisibility(R.id.calendar_today_border, if (date == today) View.VISIBLE else View.GONE)
                    cell.setContentDescription(R.id.calendar_cell, buildString {
                        append(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", ru)))
                        if (date == today) append(", сегодня")
                        if (off && !sickLeave) append(", выходной")
                        if (sickLeave) append(", больничный")
                        else if (filled) {
                            append(", ${DayWidgetData(date, day, 0.0).status}, ${day!!.totalHours} ч")
                        } else append(if (day == null) ", день не заполнен" else ", план")
                    })
                    cell.setOnClickPendingIntent(R.id.calendar_cell, pending(date))
                }
                row.addView(R.id.calendar_week, cell)
            }
            addView(R.id.calendar_weeks, row)
        }
    }
}
