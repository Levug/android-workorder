package com.workorder.app.domain

import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.data.model.WorkSchedule
import com.workorder.app.data.model.fundHours
import com.workorder.app.data.model.isInHourFund
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class SalaryCalculatorTest {
    @Test fun planningDoesNotChangeSalaryOrForecastButRecordedZeroDoes() {
        val month = YearMonth.of(2026, 7)
        val preferences = Settings(hourlyRate = 100.0, contractHourlyRate = 50.0)
        val plan = WorkDay(date = "2026-07-01", totalHours = 0.0, specialization = "Наладка", isRecorded = false)
        assertEquals(SalaryCalculator.calculate(emptyList(), preferences, 184.0),
            SalaryCalculator.calculate(listOf(plan), preferences, 184.0))
        val originalForecast = SalaryCalculator.forecast(month, emptyList(), preferences, 184.0)
        assertEquals(originalForecast, SalaryCalculator.forecast(month, listOf(plan), preferences, 184.0))
        val explicitZero = SalaryCalculator.forecast(month, listOf(plan.copy(isRecorded = true)), preferences, 184.0)
        assertEquals(originalForecast.fullSalary - 800.0, explicitZero.fullSalary, 0.0)
    }

    private val settings = Settings(
        hourlyRate = 100.0,
        contractHourlyRate = 50.0,
        defaultDayHours = 8.0
    )

    @Test
    fun missingMonthlyNormDoesNotProduceFakeFullSalary() {
        val result = SalaryCalculator.calculate(
            days = listOf(WorkDay(date = "2026-07-01", totalHours = 10.0, dayType = WorkDayType.REGULAR)),
            settings = settings,
            plannedHours = 0.0
        )
        assertEquals(0.0, result.fullSalary, 0.0)
        assertEquals(400.0, result.advance, 0.0)
    }

    @Test
    fun advanceExcludesDelaysAndDaysOffSchedule() {
        val result = SalaryCalculator.calculate(
            days = listOf(
                WorkDay(date = "2026-07-01", totalHours = 10.0, dayType = WorkDayType.REGULAR),
                WorkDay(date = "2026-07-04", totalHours = 8.0, dayType = WorkDayType.AUTO)
            ),
            settings = settings,
            plannedHours = 100.0
        )
        assertEquals(8.0, result.advanceHours, 0.0)
        assertEquals(400.0, result.advance, 0.0)
    }

    @Test
    fun twoTwoScheduleRepeatsTwoWorkTwoOffFromAnchor() {
        val twoTwo = settings.copy(
            workSchedule = WorkSchedule.TWO_TWO,
            shiftAnchorDate = "2026-07-01"
        )
        assertEquals(true, SalaryCalculator.isScheduledWorkDay(LocalDate.parse("2026-07-01"), twoTwo))
        assertEquals(true, SalaryCalculator.isScheduledWorkDay(LocalDate.parse("2026-07-02"), twoTwo))
        assertEquals(false, SalaryCalculator.isScheduledWorkDay(LocalDate.parse("2026-07-03"), twoTwo))
        assertEquals(false, SalaryCalculator.isScheduledWorkDay(LocalDate.parse("2026-07-04"), twoTwo))
        assertEquals(true, SalaryCalculator.isScheduledWorkDay(LocalDate.parse("2026-07-05"), twoTwo))
    }

    @Test
    fun delayFillsMissingNormAtSingleRate() {
        val result = SalaryCalculator.calculate(
            days = listOf(WorkDay(date = "2026-07-01", totalHours = 10.0, dayType = WorkDayType.REGULAR)),
            settings = settings,
            plannedHours = 100.0
        )
        assertEquals(2.0, result.delaySingleHours, 0.0)
        assertEquals(0.0, result.delayDoubleHours, 0.0)
        assertEquals(1_000.0, result.fullSalary, 0.0)
    }

    @Test
    fun delayAfterCompletedNormIsPaidDouble() {
        val result = SalaryCalculator.calculate(
            days = listOf(WorkDay(date = "2026-07-01", totalHours = 10.0, dayType = WorkDayType.REGULAR)),
            settings = settings,
            plannedHours = 8.0
        )
        assertEquals(8.0, result.regularHours, 0.0)
        assertEquals(2.0, result.delayDoubleHours, 0.0)
        assertEquals(1_200.0, result.fullSalary, 0.0)
    }

    @Test
    fun holidayIsAlwaysPaidDouble() {
        val result = SalaryCalculator.calculate(
            days = listOf(WorkDay(date = "2026-07-01", totalHours = 8.0, dayType = WorkDayType.HOLIDAY)),
            settings = settings,
            plannedHours = 100.0
        )
        assertEquals(8.0, result.fixedDoubleHours, 0.0)
        assertEquals(1_600.0, result.fullSalary, 0.0)
    }

    @Test
    fun oneHundredTenHoursWithTenHourDelaysPaysAsOneHundredTwenty() {
        val tenHourShiftSettings = settings.copy(defaultDayHours = 10.0)
        val days = (1..10).map { day ->
            WorkDay(
                date = "2026-07-${day.toString().padStart(2, '0')}",
                totalHours = 11.0,
                dayType = WorkDayType.REGULAR
            )
        }
        val result = SalaryCalculator.calculate(days, tenHourShiftSettings, plannedHours = 100.0)
        assertEquals(100.0, result.regularHours, 0.0)
        assertEquals(10.0, result.delayDoubleHours, 0.0)
        assertEquals(12_000.0, result.fullSalary, 0.0)
    }

    @Test
    fun forecastFillsMissingWeekdayShiftsFromSchedule() {
        val result = SalaryCalculator.forecast(
            yearMonth = YearMonth.of(2026, 7),
            days = emptyList(),
            settings = settings,
            plannedHours = 184.0
        )

        assertEquals(184.0, result.regularHours, 0.0)
        assertEquals(18_400.0, result.fullSalary, 0.0)
        assertEquals(4_400.0, result.advance, 0.0)
    }

    @Test
    fun forecastUsesEnteredOvertimeInsteadOfDefaultShift() {
        val result = SalaryCalculator.forecast(
            yearMonth = YearMonth.of(2026, 7),
            days = listOf(
                WorkDay(date = "2026-07-01", totalHours = 10.0, dayType = WorkDayType.AUTO)
            ),
            settings = settings,
            plannedHours = 184.0
        )

        assertEquals(2.0, result.delayDoubleHours, 0.0)
        assertEquals(18_800.0, result.fullSalary, 0.0)
    }

    @Test
    fun scheduleNormCountsWorkDaysAndRespectsMarkedDayTypes() {
        val july = YearMonth.of(2026, 7) // 23 будних дня
        assertEquals(184.0, SalaryCalculator.scheduledNormHours(july, settings), 0.0)
        val marked = listOf(
            WorkDay(date = "2026-07-01", totalHours = 0.0, dayType = WorkDayType.HOLIDAY, isRecorded = false),
            WorkDay(date = "2026-07-04", totalHours = 8.0, dayType = WorkDayType.REGULAR)
        )
        assertEquals(184.0, SalaryCalculator.scheduledNormHours(july, settings, marked), 0.0)
        assertEquals(176.0, SalaryCalculator.scheduledNormHours(july, settings, marked.take(1)), 0.0)
    }

    @Test
    fun plannedHolidayIsSkippedAndPlannedWorkingWeekendIsForecast() {
        val base = SalaryCalculator.forecast(YearMonth.of(2026, 7), emptyList(), settings, 184.0)
        val holiday = WorkDay(date = "2026-07-01", totalHours = 0.0, dayType = WorkDayType.HOLIDAY, isRecorded = false)
        val withHoliday = SalaryCalculator.forecast(YearMonth.of(2026, 7), listOf(holiday), settings, 184.0)
        assertEquals(base.regularHours - 8.0, withHoliday.regularHours, 0.0)
        val workingSaturday = WorkDay(date = "2026-07-04", totalHours = 0.0, dayType = WorkDayType.REGULAR, isRecorded = false)
        val withSaturday = SalaryCalculator.forecast(YearMonth.of(2026, 7), listOf(workingSaturday), settings, 192.0)
        assertEquals(192.0, withSaturday.regularHours, 0.0)
        assertEquals(0.0, withSaturday.fixedDoubleHours, 0.0)
    }

    @Test
    fun forecastAddsEnteredWeekendAtDoubleRate() {
        val result = SalaryCalculator.forecast(
            yearMonth = YearMonth.of(2026, 7),
            days = listOf(
                WorkDay(date = "2026-07-04", totalHours = 8.0, dayType = WorkDayType.AUTO)
            ),
            settings = settings,
            plannedHours = 184.0
        )

        assertEquals(8.0, result.fixedDoubleHours, 0.0)
        assertEquals(20_000.0, result.fullSalary, 0.0)
    }

    @Test
    fun compWorkIsUnpaidAndCompOffIsPaidAsRegularDay() {
        val result = SalaryCalculator.calculate(
            days = listOf(
                // Суббота: выход за отгул не оплачивается ×2, а уходит в баланс.
                WorkDay(date = "2026-07-04", totalHours = 8.0, dayType = WorkDayType.COMP_WORK),
                // Отгул в среду: оплачен как обычная смена и входит в аванс.
                WorkDay(date = "2026-07-08", totalHours = 8.0, dayType = WorkDayType.COMP_OFF)
            ),
            settings = settings,
            plannedHours = 100.0
        )
        assertEquals(0.0, result.fixedDoubleHours, 0.0)
        assertEquals(8.0, result.regularHours, 0.0)
        assertEquals(8.0, result.compWorkHours, 0.0)
        assertEquals(8.0, result.compOffHours, 0.0)
        assertEquals(800.0, result.fullSalary, 0.0)
        assertEquals(8.0, result.advanceHours, 0.0)
    }

    @Test
    fun compOffOnScheduledDayOffIsStillSingleRate() {
        val result = SalaryCalculator.calculate(
            days = listOf(WorkDay(date = "2026-07-04", totalHours = 8.0, dayType = WorkDayType.COMP_OFF)),
            settings = settings,
            plannedHours = 100.0
        )
        assertEquals(0.0, result.fixedDoubleHours, 0.0)
        assertEquals(800.0, result.fullSalary, 0.0)
    }

    @Test
    fun manualHoursArePaidExactlyAsEntered() {
        val result = SalaryCalculator.calculate(
            days = listOf(
                WorkDay(date = "2026-07-04", totalHours = 10.0, dayType = WorkDayType.MANUAL,
                    manualSingleHours = 6.0, manualDoubleHours = 4.0)
            ),
            settings = settings,
            plannedHours = 100.0
        )
        assertEquals(6.0, result.manualSingleHours, 0.0)
        assertEquals(4.0, result.manualDoubleHours, 0.0)
        assertEquals(0.0, result.fixedDoubleHours, 0.0)
        assertEquals(0.0, result.regularHours, 0.0)
        assertEquals(100.0 * (6.0 + 2 * 4.0), result.fullSalary, 0.0)
        // Аванс: ручной день 4-го числа даёт свои одинарные часы, не больше смены.
        assertEquals(6.0, result.advanceHours, 0.0)
    }

    @Test
    fun manualSingleHoursUseUpMonthlyNormBeforeDelays() {
        val result = SalaryCalculator.calculate(
            days = listOf(
                WorkDay(date = "2026-07-01", totalHours = 10.0, dayType = WorkDayType.REGULAR),
                WorkDay(date = "2026-07-02", totalHours = 8.0, dayType = WorkDayType.MANUAL, manualSingleHours = 8.0)
            ),
            settings = settings,
            plannedHours = 16.0
        )
        // Норма 16 ч уже закрыта: 8 ч смены + 8 ч ручных одинарных, поэтому задержка 2 ч — двойная.
        assertEquals(0.0, result.delaySingleHours, 0.0)
        assertEquals(2.0, result.delayDoubleHours, 0.0)
        assertEquals(100.0 * (8.0 + 8.0 + 2 * 2.0), result.fullSalary, 0.0)
    }

    @Test
    fun plannedCompWorkIsNotForecastAndLeavesNorm() {
        val month = YearMonth.of(2026, 7)
        val plan = WorkDay(date = "2026-07-08", totalHours = 0.0, dayType = WorkDayType.COMP_WORK, isRecorded = false)
        val base = SalaryCalculator.forecast(month, emptyList(), settings, 184.0)
        val withPlan = SalaryCalculator.forecast(month, listOf(plan), settings, 184.0)
        assertEquals(base.regularHours - 8.0, withPlan.regularHours, 0.0)
        assertEquals(
            SalaryCalculator.scheduledNormHours(month, settings) - 8.0,
            SalaryCalculator.scheduledNormHours(month, settings, listOf(plan)),
            0.0
        )
    }

    /** Июль 2026: 1-е — среда, рабочих дней с 1 по 15 число — 11, то есть 88 ч. */
    private fun julyFirstHalf(skip: Set<Int>): List<WorkDay> =
        listOf(1, 2, 3, 6, 7, 8, 9, 10, 13, 14, 15).filter { it !in skip }.map { day ->
            WorkDay(date = "2026-07-${day.toString().padStart(2, '0')}", totalHours = 8.0)
        }

    @Test
    fun advanceFillReplacesMissedWorkdaysWithOvertimeAndWeekends() {
        val days = julyFirstHalf(skip = setOf(14, 15)).map {
            if (it.date == "2026-07-01") it.copy(totalHours = 10.0) else it
        } + WorkDay(date = "2026-07-04", totalHours = 8.0) +
            WorkDay(date = "2026-07-11", totalHours = 6.0, dayType = WorkDayType.COMP_WORK)

        val classic = SalaryCalculator.calculate(days, settings, 184.0)
        assertEquals(72.0, classic.advanceHours, 0.0)
        assertEquals(0.0, classic.advanceExtraHours, 0.0)

        // 72 ч смен + 2 ч задержки + 8 ч субботы = 82 ч; выход за отгул не оплачивается и не идёт в аванс.
        val filled = SalaryCalculator.calculate(days, settings.copy(advanceFillEnabled = true), 184.0)
        assertEquals(82.0, filled.advanceHours, 0.0)
        assertEquals(10.0, filled.advanceExtraHours, 0.0)
        assertEquals(82.0 * 50.0, filled.advance, 0.0)
        // Полная зарплата от эксперимента не меняется — меняется только деление на аванс и вторую часть.
        assertEquals(classic.fullSalary, filled.fullSalary, 0.0)
    }

    @Test
    fun advanceFillNeverExceedsScheduledFirstHalf() {
        val days = julyFirstHalf(skip = setOf(15)) +
            WorkDay(date = "2026-07-04", totalHours = 12.0) +
            WorkDay(date = "2026-07-05", totalHours = 12.0)
        val filled = SalaryCalculator.calculate(days, settings.copy(advanceFillEnabled = true), 184.0)
        assertEquals(88.0, filled.advanceHours, 0.0)

        // Праздник в плане на 15-е убирает рабочий день из графика — и из потолка аванса.
        val holiday = WorkDay(date = "2026-07-15", totalHours = 0.0, dayType = WorkDayType.HOLIDAY, isRecorded = false)
        val withHoliday = SalaryCalculator.calculate(days + holiday, settings.copy(advanceFillEnabled = true), 184.0)
        assertEquals(80.0, withHoliday.advanceHours, 0.0)
    }

    @Test
    fun advanceFillWithoutAbsencesChangesNothing() {
        val days = julyFirstHalf(skip = emptySet()) + WorkDay(date = "2026-07-04", totalHours = 8.0)
        val classic = SalaryCalculator.calculate(days, settings, 184.0)
        val filled = SalaryCalculator.calculate(days, settings.copy(advanceFillEnabled = true), 184.0)
        assertEquals(88.0, classic.advanceHours, 0.0)
        assertEquals(classic, filled)
    }

    @Test
    fun hourFundCountsCompOffButNotCompWork() {
        val work = WorkDay(date = "2026-07-04", totalHours = 8.0, dayType = WorkDayType.COMP_WORK)
        val off = WorkDay(date = "2026-07-08", totalHours = 8.0, dayType = WorkDayType.COMP_OFF)
        val plan = WorkDay(date = "2026-07-09", totalHours = 0.0, dayType = WorkDayType.HOLIDAY, isRecorded = false)
        assertEquals(0.0, work.fundHours, 0.0)
        assertEquals(8.0, off.fundHours, 0.0)
        assertEquals(0.0, plan.fundHours, 0.0)
        assertEquals(listOf(false, true, false), listOf(work, off, plan).map { it.isInHourFund })
    }

    @Test
    fun manualDayUsesItsOwnMultiplier() {
        val result = SalaryCalculator.calculate(
            days = listOf(
                WorkDay(date = "2026-07-04", totalHours = 10.0, dayType = WorkDayType.MANUAL,
                    manualSingleHours = 6.0, manualDoubleHours = 4.0, manualMultiplier = 1.5),
                WorkDay(date = "2026-07-11", totalHours = 2.0, dayType = WorkDayType.MANUAL,
                    manualDoubleHours = 2.0, manualMultiplier = 3.0)
            ),
            settings = settings,
            plannedHours = 100.0
        )
        assertEquals(6.0, result.manualDoubleHours, 0.0)
        assertEquals(4.0 * 1.5 + 2.0 * 3.0, result.manualRaisedPaidHours, 0.0)
        assertEquals(true, result.manualMixedMultipliers)
        assertEquals(100.0 * (6.0 + 12.0), result.fullSalary, 0.0)
    }

    @Test
    fun partialCompOffIsPaidAsRegularWithoutCreatingOvertime() {
        // Отработано 4 ч и ещё 4 ч взято отгулом: полная смена по обычной ставке, без задержек.
        val day = WorkDay(date = "2026-07-08", totalHours = 4.0, compOffHours = 4.0)
        val result = SalaryCalculator.calculate(listOf(day), settings, 100.0)
        assertEquals(8.0, result.regularHours, 0.0)
        assertEquals(0.0, result.delaySingleHours + result.delayDoubleHours, 0.0)
        assertEquals(4.0, result.compOffHours, 0.0)
        assertEquals(800.0, result.fullSalary, 0.0)
        assertEquals(8.0, result.advanceHours, 0.0)
        assertEquals(8.0, day.fundHours, 0.0)
    }

    @Test
    fun unpaidDayIsNotPaidAndLeavesNormAndForecast() {
        val month = YearMonth.of(2026, 7)
        val unpaid = WorkDay(date = "2026-07-08", totalHours = 0.0, dayType = WorkDayType.UNPAID, isRecorded = false)
        assertEquals(
            SalaryCalculator.scheduledNormHours(month, settings) - 8.0,
            SalaryCalculator.scheduledNormHours(month, settings, listOf(unpaid)),
            0.0
        )
        val base = SalaryCalculator.forecast(month, emptyList(), settings, 184.0)
        val withUnpaid = SalaryCalculator.forecast(month, listOf(unpaid), settings, 184.0)
        assertEquals(base.regularHours - 8.0, withUnpaid.regularHours, 0.0)
        assertEquals(1, SalaryCalculator.calculate(listOf(unpaid), settings, 184.0).unpaidDays)
        assertEquals(0.0, unpaid.fundHours, 0.0)
    }
}
