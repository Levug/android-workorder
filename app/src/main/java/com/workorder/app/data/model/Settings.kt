package com.workorder.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class WorkSchedule { FIVE_TWO, TWO_TWO }

@Entity(tableName = "settings")
data class Settings(
    @PrimaryKey
    val id: Int = 1,
    val hourlyRate: Double = 0.0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val themePreset: String = "DEFAULT",
    val dynamicColor: Boolean = false,
    /** Продолжительность смены, подставляемая при создании наряда. */
    val defaultDayHours: Double = 8.0,
    /** Договорная ставка используется только для аванса. */
    val contractHourlyRate: Double = 0.0,
    val workSchedule: WorkSchedule = WorkSchedule.FIVE_TWO,
    /** Первая рабочая смена цикла 2/2, ISO yyyy-MM-dd. */
    val shiftAnchorDate: String = "2026-01-01"
)
