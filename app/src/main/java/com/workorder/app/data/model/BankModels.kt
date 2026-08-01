package com.workorder.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

enum class BankStrategy { PROPORTIONAL, PRIORITY, GRADE_RATIO, MANUAL }

@Entity(tableName = "monthly_plans", primaryKeys = ["yearMonth"])
data class MonthlyPlan(
    val yearMonth: String,
    val plannedHours: Double = 0.0,
    val coefficient: Double = 1.15,
    val strategy: BankStrategy = BankStrategy.PROPORTIONAL,
    val grade3Percent: Double = 25.0,
    val grade4Percent: Double = 25.0,
    val grade5Percent: Double = 25.0,
    val grade6Percent: Double = 25.0
)

/**
 * Помесячный расчётный слой. bankedQuantity > 0 — убрать из отчёта в банк,
 * bankedQuantity < 0 — добавить из ранее накопленного банка в отчёт.
 */
@Entity(
    tableName = "bank_allocations",
    primaryKeys = ["yearMonth", "operationId"],
    foreignKeys = [
        ForeignKey(
            entity = Operation::class,
            parentColumns = ["id"],
            childColumns = ["operationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("operationId")]
)
data class BankAllocation(
    val yearMonth: String,
    val operationId: Long,
    val bankedQuantity: Int
)
