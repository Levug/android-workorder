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
    /** Эксперимент: часы переработок и выходов, добавленные в аванс вместо пропущенных рабочих дней. */
    val advanceExtraHours: Double = 0.0,
    val advance: Double = 0.0,
    val regularHours: Double = 0.0,
    val fixedDoubleHours: Double = 0.0,
    val delaySingleHours: Double = 0.0,
    val delayDoubleHours: Double = 0.0,
    /** Ручной режим: часы по обычной и по повышенной ставке, как их указал пользователь. */
    val manualSingleHours: Double = 0.0,
    val manualDoubleHours: Double = 0.0,
    /** Повышенные часы ручного режима, умноженные на свои коэффициенты (к оплате по ставке). */
    val manualRaisedPaidHours: Double = 0.0,
    /** В месяце встречаются разные коэффициенты ручного режима. */
    val manualMixedMultipliers: Boolean = false,
    /** Выходы за отгул: в этом месяце не оплачиваются, а пополняют баланс отгулов. */
    val compWorkHours: Double = 0.0,
    /** Отгулы (целыми днями и на часть дня): уже входят в обычные часы, показываются отдельно для расшифровки. */
    val compOffHours: Double = 0.0,
    /** Дни БС (без сохранения содержания) в месяце, включая запланированные. */
    val unpaidDays: Int = 0,
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
        val byDate = days.mapNotNull { day ->
            runCatching { LocalDate.parse(day.date) }.getOrNull()
                ?.takeIf { YearMonth.from(it) == yearMonth }
                ?.let { it to day }
        }
        val enteredDays = byDate.filter { it.second.isRecorded }.toMap()
        // Планы без смены могут заранее пометить праздник или рабочий выходной.
        val plannedTypes = byDate.filter { !it.second.isRecorded }.associate { it.first to it.second.dayType }

        val projectedDays = (1..yearMonth.lengthOfMonth()).mapNotNull { dayOfMonth ->
            val date = yearMonth.atDay(dayOfMonth)
            enteredDays[date] ?: if (isPlannedWorkDay(date, plannedTypes[date], settings)) {
                WorkDay(
                    date = date.toString(),
                    totalHours = settings.defaultDayHours,
                    dayType = if (plannedTypes[date] == WorkDayType.REGULAR) WorkDayType.REGULAR else WorkDayType.AUTO
                )
            } else null
        }

        // Планы праздников и выходов за отгул остаются в списке: по ним считается график до 15-го для аванса.
        val projectedDates = projectedDays.map { it.date }.toSet()
        val otherPlans = byDate.map { it.second }.filter { !it.isRecorded && it.date !in projectedDates }
        return calculate(projectedDays + otherPlans, settings, plannedHours)
    }

    /**
     * Норма часов месяца по графику: рабочие дни × часы смены.
     * Типы дней («Рабочий», «Праздник») из нарядов и планов уточняют график.
     */
    fun scheduledNormHours(yearMonth: YearMonth, settings: Settings, days: List<WorkDay> = emptyList()): Double {
        val types = days.mapNotNull { day ->
            runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it to day.dayType }
        }.toMap()
        val workDays = (1..yearMonth.lengthOfMonth()).count { dayOfMonth ->
            val date = yearMonth.atDay(dayOfMonth)
            isPlannedWorkDay(date, types[date], settings)
        }
        return workDays * settings.defaultDayHours
    }

    /**
     * Выход за отгул не оплачивается сразу, поэтому не входит в норму; отгул оплачивается как рабочий день.
     * БС — день без сохранения содержания: не рабочий для нормы, прогноза и потолка аванса.
     */
    private fun isPlannedWorkDay(date: LocalDate, type: WorkDayType?, settings: Settings): Boolean = when (type) {
        WorkDayType.HOLIDAY, WorkDayType.COMP_WORK, WorkDayType.UNPAID -> false
        WorkDayType.REGULAR, WorkDayType.COMP_OFF -> true
        else -> isScheduledWorkDay(date, settings)
    }

    fun calculate(days: List<WorkDay>, settings: Settings, plannedHours: Double): SalaryBreakdown {
        if (plannedHours <= 0.0) {
            val advance = calculateAdvance(days, settings)
            return SalaryBreakdown(
                advanceHours = advance.hours,
                advanceExtraHours = advance.extraHours,
                advance = advance.hours * settings.contractHourlyRate
            )
        }
        val recorded = days.filter { it.isRecorded }
        val manual = recorded.filter { it.dayType == WorkDayType.MANUAL }
        val classified = recorded
            .filter { it.dayType != WorkDayType.MANUAL && it.dayType != WorkDayType.COMP_WORK }
            .map { day ->
                val date = LocalDate.parse(day.date)
                val scheduled = isRegularDay(day, date, settings)
                Triple(day, date, scheduled)
            }

        val advanceHours = calculateAdvance(days, settings)
        val fixedDouble = classified.filter { !it.third }.sumOf { it.first.totalHours }
        val regularDays = classified.filter { it.third }
        // Отгул на часть дня — обычные часы: переработка считается только от отработанных часов.
        val scheduledBase = regularDays.sumOf { minOf(it.first.totalHours, settings.defaultDayHours) } +
            classified.sumOf { it.first.compOffHours }
        val delays = regularDays.sumOf { (it.first.totalHours - settings.defaultDayHours).coerceAtLeast(0.0) }
        val manualSingle = manual.sumOf { it.manualSingleHours }
        val manualDouble = manual.sumOf { it.manualDoubleHours }
        val manualRaisedPaid = manual.sumOf { it.manualDoubleHours * it.manualMultiplier }
        // Ручные одинарные часы тоже расходуют норму: задержки до нормы считаются после них.
        val delaySingle = minOf(delays, (plannedHours - scheduledBase - manualSingle).coerceAtLeast(0.0))
        val delayDouble = (delays - delaySingle).coerceAtLeast(0.0)
        val regular = minOf(plannedHours.coerceAtLeast(0.0), scheduledBase + delaySingle)
        val full = settings.hourlyRate * (regular + manualSingle + 2.0 * (fixedDouble + delayDouble) + manualRaisedPaid)
        val advance = advanceHours.hours * settings.contractHourlyRate

        return SalaryBreakdown(
            advanceHours = advanceHours.hours,
            advanceExtraHours = advanceHours.extraHours,
            advance = advance,
            regularHours = regular,
            fixedDoubleHours = fixedDouble,
            delaySingleHours = delaySingle,
            delayDoubleHours = delayDouble,
            manualSingleHours = manualSingle,
            manualDoubleHours = manualDouble,
            manualRaisedPaidHours = manualRaisedPaid,
            manualMixedMultipliers = manual.filter { it.manualDoubleHours > 0 }.map { it.manualMultiplier }.distinct().size > 1,
            compWorkHours = recorded.filter { it.dayType == WorkDayType.COMP_WORK }.sumOf { it.totalHours },
            compOffHours = recorded.filter { it.dayType == WorkDayType.COMP_OFF }.sumOf { it.totalHours } +
                recorded.sumOf { it.compOffHours },
            unpaidDays = days.count { it.dayType == WorkDayType.UNPAID },
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

    private data class AdvanceHours(val hours: Double, val extraHours: Double)

    /**
     * Аванс — часы смен до 15-го числа включительно: на обычных днях не больше смены, без задержек и выходных.
     * Эксперимент [Settings.advanceFillEnabled]: если рабочие дни пропущены, их место занимают переработки
     * и выходы в выходные за те же числа, но не больше часов рабочих дней по графику за 1–15 число.
     * Выход за отгул в аванс не идёт — он не оплачивается.
     */
    private fun calculateAdvance(days: List<WorkDay>, settings: Settings): AdvanceHours {
        val firstHalf = days.filter { it.isRecorded }.mapNotNull { day ->
            LocalDate.parse(day.date).takeIf { it.dayOfMonth <= 15 }?.let { day to it }
        }
        val regular = firstHalf.sumOf { (day, date) ->
            when {
                day.dayType == WorkDayType.MANUAL -> minOf(day.manualSingleHours, settings.defaultDayHours)
                isRegularDay(day, date, settings) -> minOf(day.totalHours + day.compOffHours, settings.defaultDayHours)
                else -> 0.0
            }
        }
        if (!settings.advanceFillEnabled) return AdvanceHours(regular, 0.0)
        val limit = firstHalfScheduledHours(days, settings) ?: return AdvanceHours(regular, 0.0)
        val worked = firstHalf.sumOf { (day, _) ->
            when (day.dayType) {
                WorkDayType.COMP_WORK -> 0.0
                WorkDayType.MANUAL -> day.manualSingleHours + day.manualDoubleHours
                else -> day.totalHours + day.compOffHours
            }
        }
        val filled = maxOf(regular, minOf(limit, worked))
        return AdvanceHours(filled, filled - regular)
    }

    /** Часы рабочих дней по графику с 1 по 15 число; типы дней из нарядов и планов уточняют график. */
    private fun firstHalfScheduledHours(days: List<WorkDay>, settings: Settings): Double? {
        val dated = days.mapNotNull { day ->
            runCatching { LocalDate.parse(day.date) }.getOrNull()?.let { it to day.dayType }
        }
        val month = dated.firstOrNull()?.let { YearMonth.from(it.first) } ?: return null
        val types = dated.filter { YearMonth.from(it.first) == month }.toMap()
        val workDays = (1..15).count { dayOfMonth ->
            val date = month.atDay(dayOfMonth)
            isPlannedWorkDay(date, types[date], settings)
        }
        return workDays * settings.defaultDayHours
    }

    private fun isRegularDay(day: WorkDay, date: LocalDate, settings: Settings): Boolean =
        when (day.dayType) {
            WorkDayType.HOLIDAY, WorkDayType.COMP_WORK -> false
            // Часы, отмеченные в день БС, оплачиваются как обычные: день не рабочий только для нормы.
            WorkDayType.REGULAR, WorkDayType.COMP_OFF, WorkDayType.UNPAID -> true
            WorkDayType.AUTO, WorkDayType.MANUAL -> isScheduledWorkDay(date, settings)
        }
}
