package com.workorder.app.data.repository

import androidx.room.withTransaction
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.dao.BankBalance
import com.workorder.app.data.dao.OperationTotal
import com.workorder.app.data.model.BankAllocation
import com.workorder.app.data.model.BankStrategy
import com.workorder.app.data.model.MonthlyPlan
import com.workorder.app.data.model.Operation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class BankOperationLine(
    val operation: Operation,
    val rawQuantity: Int,
    val bankedQuantity: Int,
    val balanceBefore: Int
) {
    val adjustedQuantity: Int get() = rawQuantity - bankedQuantity
    val balanceAfter: Int get() = balanceBefore + bankedQuantity
    val rawHours: Double get() = rawQuantity * operation.durationHours
    val adjustedHours: Double get() = adjustedQuantity * operation.durationHours
}

data class BankState(
    val plan: MonthlyPlan,
    val lines: List<BankOperationLine>,
    val rawHours: Double,
    val adjustedHours: Double,
    val bankHoursBefore: Double,
    val bankHoursAfter: Double,
    val attendanceHours: Double
) {
    val targetHours: Double get() = attendanceHours * plan.coefficient
}

class BankRepository(private val db: AppDatabase) {
    private val bankDao = db.bankDao()
    private val operationDao = db.operationDao()
    private val entryDao = db.operationEntryDao()
    private val workDayDao = db.workDayDao()

    fun observe(yearMonth: String): Flow<BankState> = combine(
        combine(
            bankDao.observePlan(yearMonth),
            bankDao.observeAllocations(yearMonth),
            bankDao.observeBalanceBefore(yearMonth),
            entryDao.observeMonthTotals(yearMonth),
            operationDao.observeAll()
        ) { planOrNull, allocations, balances, rawTotals, operations ->
            val plan = planOrNull ?: MonthlyPlan(yearMonth)
            buildState(plan, operations, rawTotals, allocations, balances)
        },
        workDayDao.observeByMonth(yearMonth)
    ) { state, days ->
        state.copy(attendanceHours = days.sumOf { it.totalHours })
    }

    suspend fun updatePlan(
        yearMonth: String,
        plannedHours: Double,
        coefficient: Double,
        strategy: BankStrategy,
        grade3Percent: Double = 25.0,
        grade4Percent: Double = 25.0,
        grade5Percent: Double = 25.0,
        grade6Percent: Double = 25.0
    ) {
        bankDao.upsertPlan(
            MonthlyPlan(
                yearMonth = yearMonth,
                plannedHours = plannedHours.coerceAtLeast(0.0),
                coefficient = coefficient.coerceAtLeast(0.0),
                strategy = strategy,
                grade3Percent = grade3Percent.coerceAtLeast(0.0),
                grade4Percent = grade4Percent.coerceAtLeast(0.0),
                grade5Percent = grade5Percent.coerceAtLeast(0.0),
                grade6Percent = grade6Percent.coerceAtLeast(0.0)
            )
        )
    }

    suspend fun applyAutomatic(state: BankState) {
        if (state.plan.strategy == BankStrategy.MANUAL) return
        replaceMonth(state.plan.yearMonth, calculateBankAllocation(state))
    }

    suspend fun setManualAllocation(state: BankState, operationId: Long, value: Int) {
        val line = state.lines.firstOrNull { it.operation.id == operationId } ?: return
        val safe = value.coerceIn(-line.balanceBefore, line.rawQuantity)
        val values = state.lines.associate { it.operation.id to it.bankedQuantity }.toMutableMap()
        values[operationId] = safe
        replaceMonth(state.plan.yearMonth, values)
    }

    suspend fun resetMonth(yearMonth: String) = bankDao.clearMonth(yearMonth)

    private suspend fun replaceMonth(yearMonth: String, values: Map<Long, Int>) {
        db.withTransaction {
            bankDao.clearMonth(yearMonth)
            val rows = values.filterValues { it != 0 }.map { (operationId, quantity) ->
                BankAllocation(yearMonth, operationId, quantity)
            }
            if (rows.isNotEmpty()) bankDao.insertAllocations(rows)
        }
    }

    private fun buildState(
        plan: MonthlyPlan,
        operations: List<Operation>,
        rawTotals: List<OperationTotal>,
        allocations: List<BankAllocation>,
        balances: List<BankBalance>
    ): BankState {
        val raw = rawTotals.associateBy { it.operationId }
        val allocated = allocations.associate { it.operationId to it.bankedQuantity }
        val balance = balances.associate { it.operationId to it.quantity }
        val relevantIds = raw.keys + allocated.keys + balance.keys
        val lines = operations
            .filter { it.id in relevantIds }
            .map { op ->
                BankOperationLine(
                    operation = op,
                    rawQuantity = raw[op.id]?.totalCount ?: 0,
                    bankedQuantity = allocated[op.id] ?: 0,
                    balanceBefore = (balance[op.id] ?: 0).coerceAtLeast(0)
                )
            }
            .sortedWith(compareBy<BankOperationLine> { it.operation.sortOrder }.thenBy { it.operation.name })
        return BankState(
            plan = plan,
            lines = lines,
            rawHours = lines.sumOf { it.rawHours },
            adjustedHours = lines.sumOf { it.adjustedHours },
            bankHoursBefore = lines.sumOf { it.balanceBefore * it.operation.durationHours },
            bankHoursAfter = lines.sumOf { it.balanceAfter * it.operation.durationHours },
            attendanceHours = 0.0
        )
    }

}

/** Чистый детерминированный расчёт: не читает и не изменяет БД. */
internal fun calculateBankAllocation(state: BankState): Map<Long, Int> {
    val target = state.targetHours
    if (target <= 0.0 || state.lines.isEmpty()) return emptyMap()
    val result = state.lines.associate { it.operation.id to 0 }.toMutableMap()
    var adjusted = state.rawHours

    if (adjusted > target) {
        val candidates = state.lines.filter { it.rawQuantity > 0 }
        if (state.plan.strategy == BankStrategy.GRADE_RATIO) {
            val totalToMove = adjusted - target
            val percentages = mapOf(
                3 to state.plan.grade3Percent,
                4 to state.plan.grade4Percent,
                5 to state.plan.grade5Percent,
                6 to state.plan.grade6Percent
            )
            val percentSum = percentages.values.sum().takeIf { it > 0 } ?: 100.0
            for (grade in 3..6) {
                val desired = totalToMove * (percentages[grade] ?: 0.0) / percentSum
                var moved = 0.0
                val gradeLines = candidates.filter { it.operation.grade == grade }
                while (moved < desired && adjusted > target) {
                    val line = gradeLines
                        .filter { (result[it.operation.id] ?: 0) < it.rawQuantity }
                        .minByOrNull { (result[it.operation.id] ?: 0).toDouble() / it.rawQuantity }
                        ?: break
                    result[line.operation.id] = (result[line.operation.id] ?: 0) + 1
                    moved += line.operation.durationHours
                    adjusted -= line.operation.durationHours
                }
            }
            while (adjusted > target) {
                val line = candidates
                    .filter { (result[it.operation.id] ?: 0) < it.rawQuantity }
                    .minByOrNull { (result[it.operation.id] ?: 0).toDouble() / it.rawQuantity }
                    ?: break
                result[line.operation.id] = (result[line.operation.id] ?: 0) + 1
                adjusted -= line.operation.durationHours
            }
        } else if (state.plan.strategy == BankStrategy.PRIORITY) {
            for (line in candidates.sortedBy { it.operation.grade }) {
                if (adjusted <= target) break
                val needed = kotlin.math.ceil((adjusted - target) / line.operation.durationHours).toInt()
                val take = needed.coerceIn(0, line.rawQuantity)
                result[line.operation.id] = take
                adjusted -= take * line.operation.durationHours
            }
        } else {
            while (adjusted > target) {
                val line = candidates
                    .filter { (result[it.operation.id] ?: 0) < it.rawQuantity }
                    .minByOrNull { (result[it.operation.id] ?: 0).toDouble() / it.rawQuantity }
                    ?: break
                result[line.operation.id] = (result[line.operation.id] ?: 0) + 1
                adjusted -= line.operation.durationHours
            }
        }
    } else if (adjusted < target) {
        val candidates = state.lines.filter { it.balanceBefore > 0 }
        if (state.plan.strategy == BankStrategy.GRADE_RATIO) {
            val totalToMove = target - adjusted
            val percentages = mapOf(
                3 to state.plan.grade3Percent,
                4 to state.plan.grade4Percent,
                5 to state.plan.grade5Percent,
                6 to state.plan.grade6Percent
            )
            val percentSum = percentages.values.sum().takeIf { it > 0 } ?: 100.0
            for (grade in 3..6) {
                val desired = totalToMove * (percentages[grade] ?: 0.0) / percentSum
                var moved = 0.0
                val gradeLines = candidates.filter { it.operation.grade == grade }
                while (moved < desired && adjusted < target) {
                    val line = gradeLines
                        .filter { -(result[it.operation.id] ?: 0) < it.balanceBefore }
                        .minByOrNull { -(result[it.operation.id] ?: 0).toDouble() / it.balanceBefore }
                        ?: break
                    result[line.operation.id] = (result[line.operation.id] ?: 0) - 1
                    moved += line.operation.durationHours
                    adjusted += line.operation.durationHours
                }
            }
            while (adjusted < target) {
                val line = candidates
                    .filter { -(result[it.operation.id] ?: 0) < it.balanceBefore }
                    .minByOrNull { -(result[it.operation.id] ?: 0).toDouble() / it.balanceBefore }
                    ?: break
                result[line.operation.id] = (result[line.operation.id] ?: 0) - 1
                adjusted += line.operation.durationHours
            }
        } else if (state.plan.strategy == BankStrategy.PRIORITY) {
            for (line in candidates.sortedByDescending { it.operation.grade }) {
                if (adjusted >= target) break
                val needed = kotlin.math.ceil((target - adjusted) / line.operation.durationHours).toInt()
                val take = needed.coerceIn(0, line.balanceBefore)
                result[line.operation.id] = -take
                adjusted += take * line.operation.durationHours
            }
        } else {
            while (adjusted < target) {
                val line = candidates
                    .filter { -(result[it.operation.id] ?: 0) < it.balanceBefore }
                    .minByOrNull { -(result[it.operation.id] ?: 0).toDouble() / it.balanceBefore }
                    ?: break
                result[line.operation.id] = (result[line.operation.id] ?: 0) - 1
                adjusted += line.operation.durationHours
            }
        }
    }
    return result.filterValues { it != 0 }
}
