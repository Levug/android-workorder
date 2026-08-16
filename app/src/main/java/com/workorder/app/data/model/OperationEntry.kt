package com.workorder.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Запись журнала: партия операций, добавленная в наряд в конкретный момент времени.
 * Итоговое количество операции за день — сумма quantity всех её записей.
 * Отрицательное quantity — корректировка (списание).
 */
@Entity(
    tableName = "operation_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorkDay::class,
            parentColumns = ["id"],
            childColumns = ["workDayId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Operation::class,
            parentColumns = ["id"],
            childColumns = ["operationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("workDayId"),
        Index("operationId"),
        Index("createdAt"),
        Index(value = ["sourceEventId"], unique = true)
    ]
)
data class OperationEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workDayId: Long,
    val operationId: Long,
    val quantity: Int,
    val createdAt: Long,
    /** Уникальный идентификатор внешнего события, например добавления с часов. */
    val sourceEventId: String? = null
)
