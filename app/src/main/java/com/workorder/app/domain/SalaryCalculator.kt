package com.workorder.app.domain

import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.data.model.WorkSchedule
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class SalaryBreakdown(
    val advanceHours: Double = 0.0,
    val advance: Double = 0.0,
    val regularHours: Double = 0.0,
    val fixedDoubleHours: Double = 0.0,
    val delaySingleHours: Double = 0.0,
    val delayDoubleHours: Double = 0.0,
    val fullSalary: Double = 0.0,
    val remainingSalary: Double = 0.0
)

object SalaryCalculator {
    /**
     * Прогноз на весь месяц: уже заполненные дни используются без изменений,
     * а отсутствующие рабочие смены подставляются по графику со стандартной длительностью.
     */
    fun forecast(
        yearMonth: YearMonth,
        days: List<WorkDay>,
        settings: Settings,
        plannedHours: Double
    ): SalaryBreakdown {
        val enteredDays = days.mapNotNull { day ->
            runCatching { LocalDate.parse(day.date) }.getOrNull()
                ?.takeIf { YearMonth.from(it) == yearMonth }
                ?.let { it to day }
        }.toMap()

        val projectedDays = (1..yearMonth.lengthOfMonth()).mapNotNull { dayOfMonth ->
            val date = yearMonth.atDay(dayOfMonth)
            enteredDays[date] ?: if (isScheduledWorkDay(date, settings)) {
                WorkDay(
                    date = date.toString(),
                    totalHours = settings.defaultDayHours,
                    dayType = WorkDayType.AUTO
                )
            } else null
        }

        return calculate(projectedDays, settings, plannedHours)
    }

    fun calculate(days: List<WorkDay>, settings: Settings, plannedHours: Double): SalaryBreakdown {
        if (plannedHours <= 0.0) {
            val advanceHours = calculateAdvanceHours(days, settings)
            return SalaryBreakdown(
                advanceHours = advanceHours,
                advance = advanceHours * settings.contractHourlyRate
            )
        }
        val classified = days.map { day ->
            val date = LocalDate.parse(day.date)
            val scheduled = isRegularDay(day, date, settings)
            Triple(day, date, scheduled)
        }

        val advanceHours = calculateAdvanceHours(days, settings)
        val fixedDouble = classified.filter { !it.third }.sumOf { it.first.totalHours }
        val regularDays = classified.filter { it.third }
        val scheduledBase = regularDays.sumOf { minOf(it.first.totalHours, settings.defaultDayHours) }
        val delays = regularDays.sumOf { (it.first.totalHours - settings.defaultDayHours).coerceAtLeast(0.0) }
        val delaySingle = minOf(delays, (plannedHours - scheduledBase).coerceAtLeast(0.0))
        val delayDouble = (delays - delaySingle).coerceAtLeast(0.0)
        val regular = minOf(plannedHours.coerceAtLeast(0.0), scheduledBase + delaySingle)
        val full = settings.hourlyRate * (regular + 2.0 * (fixedDouble + delayDouble))
        val advance = advanceHours * settings.contractHourlyRate

        return SalaryBreakdown(
            advanceHours = advanceHours,
            advance = advance,
            regularHours = regular,
            fixedDoubleHours = fixedDouble,
            delaySingleHours = delaySingle,
            delayDoubleHours = delayDouble,
            fullSalary = full,
            remainingSalary = full - advance
        )
    }

    fun isScheduledWorkDay(date: LocalDate, settings: Settings): Boolean = when (settings.workSchedule) {
        WorkSchedule.FIVE_TWO -> date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        WorkSchedule.TWO_TWO -> {
            val anchor = runCatching { LocalDate.parse(settings.shiftAnchorDate) }.getOrElse { LocalDate.of(2026, 1, 1) }
            Math.floorMod(ChronoUnit.DAYS.between(anchor, date).toInt(), 4) < 2
        }
    }

    private fun calculateAdvanceHours(days: List<WorkDay>, settings: Settings): Double =
        days.sumOf { day ->
            val date = LocalDate.parse(day.date)
            if (date.dayOfMonth <= 15 && isRegularDay(day, date, settings)) {
                minOf(day.totalHours, settings.defaultDayHours)
            } else 0.0
        }

    private fun isRegularDay(day: WorkDay, date: LocalDate, settings: Settings): Boolean =
        when (day.dayType) {
            WorkDayType.HOLIDAY -> false
            WorkDayType.REGULAR -> true
            WorkDayType.AUTO -> isScheduledWorkDay(date, settings)
        }
}
