package com.workorder.app.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

enum class WorkDayType { AUTO, REGULAR, HOLIDAY }

/**
 * Рабочий день (наряд). Дата хранится строкой в формате ISO (yyyy-MM-dd),
 * уникальна: на одну дату — один наряд.
 */
@Entity(
    tableName = "work_days",
    indices = [Index(value = ["date"], unique = true)]
)
data class WorkDay(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: String,
    val totalHours: Double,
    val comment: String = "",
    /** AUTO — определяется графиком; REGULAR — принудительно обычный; HOLIDAY — всегда ×2. */
    val dayType: WorkDayType = WorkDayType.AUTO,
    @ColumnInfo(defaultValue = "''")
    val specialization: String = "",
    /** False: calendar planning only, with no attendance or operation entries. */
    @ColumnInfo(defaultValue = "1")
    val isRecorded: Boolean = true
)
