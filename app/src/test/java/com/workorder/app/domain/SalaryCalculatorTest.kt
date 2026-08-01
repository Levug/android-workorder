package com.workorder.app.domain

import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.data.model.WorkSchedule
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class SalaryCalculatorTest {
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
}
