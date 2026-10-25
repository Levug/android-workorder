package com.workorder.app.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AUTO — по графику; REGULAR — принудительно обычный; HOLIDAY — всегда ×2;
 * COMP_WORK — выход за отгул: часы не оплачиваются, а копятся в балансе отгулов;
 * COMP_OFF — отгул за счёт баланса: часы дня оплачиваются как обычные и списываются из баланса;
 * MANUAL — часы по одинарной и двойной ставке указаны вручную;
 * UNPAID — БС, день без сохранения содержания: не оплачивается и не входит в норму месяца.
 */
enum class WorkDayType { AUTO, REGULAR, HOLIDAY, COMP_WORK, COMP_OFF, MANUAL, UNPAID }

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
    val dayType: WorkDayType = WorkDayType.AUTO,
    @ColumnInfo(defaultValue = "''")
    val specialization: String = "",
    /** False: calendar planning only, with no attendance or operation entries. */
    @ColumnInfo(defaultValue = "1")
    val isRecorded: Boolean = true,
    /** Только для MANUAL: totalHours = manualSingleHours + manualDoubleHours. */
    @ColumnInfo(defaultValue = "0")
    val manualSingleHours: Double = 0.0,
    /** Часы по повышенной ставке; коэффициент — [manualMultiplier]. */
    @ColumnInfo(defaultValue = "0")
    val manualDoubleHours: Double = 0.0,
    /** Коэффициент повышенной ставки ручного дня: по умолчанию ×2, можно задать свой. */
    @ColumnInfo(defaultValue = "2")
    val manualMultiplier: Double = DEFAULT_MANUAL_MULTIPLIER,
    /**
     * Отгул на часть дня: часы из баланса, добавленные к отработанным [totalHours] (только «По графику» и «Рабочий»).
     * Оплачиваются как обычные, входят в часы месяца и списываются из баланса отгулов.
     */
    @ColumnInfo(defaultValue = "0")
    val compOffHours: Double = 0.0
)

/** Типы дня, к которым можно добавить отгул на часть дня. */
val WorkDayType.allowsPartialCompOff: Boolean
    get() = this == WorkDayType.AUTO || this == WorkDayType.REGULAR

const val DEFAULT_MANUAL_MULTIPLIER = 2.0

/** Допустимый свой коэффициент повышенной ставки. */
val MANUAL_MULTIPLIER_RANGE = 1.0..10.0

/**
 * Часы дня в фонде месяца (часы за месяц, норма, выработка, лимит банка):
 * выход за отгул уходит в баланс отгулов и в фонд не входит, а отгул оплачивается и входит.
 */
val WorkDay.fundHours: Double
    get() = if (!isRecorded || dayType == WorkDayType.COMP_WORK) 0.0 else totalHours + compOffHours

/** Смена, которая входит в фонд месяца (не план и не выход за отгул). */
val WorkDay.isInHourFund: Boolean
    get() = isRecorded && dayType != WorkDayType.COMP_WORK
