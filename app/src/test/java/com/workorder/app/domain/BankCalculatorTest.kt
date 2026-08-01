package com.workorder.app.domain

import com.workorder.app.data.model.BankStrategy
import com.workorder.app.data.model.MonthlyPlan
import com.workorder.app.data.model.Operation
import com.workorder.app.data.repository.BankOperationLine
import com.workorder.app.data.repository.BankState
import com.workorder.app.data.repository.calculateBankAllocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BankCalculatorTest {
    private val low = Operation(id = 1, name = "Grade 4", durationHours = 1.0, grade = 4)
    private val high = Operation(id = 2, name = "Grade 5", durationHours = 1.0, grade = 5)

    @Test
    fun priorityDepositsLowerGradeFirst() {
        val state = state(BankStrategy.PRIORITY, target = 5.0,
            lines = listOf(BankOperationLine(low, 5, 0, 0), BankOperationLine(high, 5, 0, 0)))
        val result = calculateBankAllocation(state)
        assertEquals(5, result[low.id])
        assertEquals(null, result[high.id])
    }

    @Test
    fun priorityWithdrawsHigherGradeFirst() {
        val state = state(BankStrategy.PRIORITY, target = 3.0,
            lines = listOf(BankOperationLine(low, 0, 0, 3), BankOperationLine(high, 0, 0, 3)))
        val result = calculateBankAllocation(state)
        assertEquals(-3, result[high.id])
        assertEquals(null, result[low.id])
    }

    @Test
    fun proportionalTouchesBothOperations() {
        val state = state(BankStrategy.PROPORTIONAL, target = 4.0,
            lines = listOf(BankOperationLine(low, 4, 0, 0), BankOperationLine(high, 4, 0, 0)))
        val result = calculateBankAllocation(state)
        assertEquals(4, result.values.sum())
        assertTrue((result[low.id] ?: 0) > 0)
        assertTrue((result[high.id] ?: 0) > 0)
    }

    @Test
    fun gradeRatioUsesConfiguredPercentages() {
        val state = state(
            BankStrategy.GRADE_RATIO,
            target = 5.0,
            lines = listOf(BankOperationLine(low, 5, 0, 0), BankOperationLine(high, 5, 0, 0)),
            percentages = listOf(0.0, 80.0, 20.0, 0.0)
        )
        val result = calculateBankAllocation(state)
        assertEquals(4, result[low.id])
        assertEquals(1, result[high.id])
    }

    @Test
    fun limitUsesActualAttendanceInsteadOfMonthlyNorm() {
        val state = BankState(
            plan = MonthlyPlan(
                yearMonth = "2026-08",
                plannedHours = 184.0,
                coefficient = 1.15
            ),
            lines = emptyList(),
            rawHours = 0.0,
            adjustedHours = 0.0,
            bankHoursBefore = 0.0,
            bankHoursAfter = 0.0,
            attendanceHours = 221.0
        )

        assertEquals(254.15, state.targetHours, 0.0001)
    }

    private fun state(
        strategy: BankStrategy,
        target: Double,
        lines: List<BankOperationLine>,
        percentages: List<Double> = listOf(25.0, 25.0, 25.0, 25.0)
    ): BankState =
        BankState(
            plan = MonthlyPlan(
                "2099-02",
                plannedHours = target,
                coefficient = 1.0,
                strategy = strategy,
                grade3Percent = percentages[0],
                grade4Percent = percentages[1],
                grade5Percent = percentages[2],
                grade6Percent = percentages[3]
            ),
            lines = lines,
            rawHours = lines.sumOf { it.rawHours },
            adjustedHours = lines.sumOf { it.adjustedHours },
            bankHoursBefore = lines.sumOf { it.balanceBefore * it.operation.durationHours },
            bankHoursAfter = lines.sumOf { it.balanceAfter * it.operation.durationHours },
            attendanceHours = target
        )
}
