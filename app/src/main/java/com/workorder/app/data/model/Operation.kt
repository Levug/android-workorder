package com.workorder.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "operations")
data class Operation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val durationHours: Double,
    /** Квалификационный разряд операции: от 3 до 6. */
    val grade: Int = 3,
    /** Пользовательский порядок в каталоге, нарядах и отчётах. */
    val sortOrder: Int = 0,
    /** Архивная операция скрыта из выбора и с часов, но её история и отчёты сохраняются. */
    @ColumnInfo(defaultValue = "0")
    val archived: Boolean = false
)
