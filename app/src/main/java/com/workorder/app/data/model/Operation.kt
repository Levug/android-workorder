package com.workorder.app.data.model

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
    val sortOrder: Int = 0
)
