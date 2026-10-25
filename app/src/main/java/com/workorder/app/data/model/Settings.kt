package com.workorder.app.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class WorkSchedule { FIVE_TWO, TWO_TWO }

/** Быстрые кнопки добавления партии: совпадают с кнопками на часах. */
const val DEFAULT_QUICK_AMOUNTS = "1,5,10,17"

/** Сколько быстрых партий можно задать (столько же кнопок на часах). */
const val MAX_QUICK_AMOUNTS = 6

/** Пресет «своя тема»: цвета строятся из [Settings.customThemeColor]. */
const val CUSTOM_THEME_PRESET = "CUSTOM"

/** Исходный цвет своей темы по умолчанию — основной цвет «Сапфира», 0xFF415F91. */
const val DEFAULT_CUSTOM_THEME_COLOR = -12492911

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
    val shiftAnchorDate: String = "2026-01-01",
    @ColumnInfo(defaultValue = "1.0")
    val interfaceScale: Double = 1.0,
    @ColumnInfo(defaultValue = "0")
    val daySpecializationEnabled: Boolean = false,
    /** Размеры быстрых партий через запятую, от 1 до 6 чисел. */
    @ColumnInfo(defaultValue = "'1,5,10,17'")
    val quickAmounts: String = DEFAULT_QUICK_AMOUNTS,
    /** ARGB исходного цвета своей темы (themePreset = CUSTOM). */
    @ColumnInfo(defaultValue = "-12492911")
    val customThemeColor: Int = DEFAULT_CUSTOM_THEME_COLOR,
    /** Кнопка «+N» у операций в сводке дня появляется после касания по операции. */
    @ColumnInfo(defaultValue = "1")
    val summaryQuickAddEnabled: Boolean = true,
    /** Отгулы, накопленные до начала учёта в приложении, ч. */
    @ColumnInfo(defaultValue = "0")
    val compTimeInitialHours: Double = 0.0,
    /** Эксперимент: пропущенные до 15-го рабочие дни в авансе восполняются переработками и выходами. */
    @ColumnInfo(defaultValue = "0")
    val advanceFillEnabled: Boolean = false,
    /** Банк нормо-часов: вкладка «Банк» и перенос операций в отчёте. Выключен — отчёт по факту. */
    @ColumnInfo(defaultValue = "1")
    val bankEnabled: Boolean = true,
    /** Переход между днями свайпом на экране наряда (стрелки работают всегда). */
    @ColumnInfo(defaultValue = "1")
    val daySwipeEnabled: Boolean = true,
    /** Цвет плитки календаря по числу часов; выключено — отмеченные дни одним цветом. */
    @ColumnInfo(defaultValue = "1")
    val calendarHeatEnabled: Boolean = true,
    /** Ряд «Быстро добавить» на экране наряда. */
    @ColumnInfo(defaultValue = "1")
    val quickAddEnabled: Boolean = true
)

/** «1, 5, 10, 17» → [1, 5, 10, 17]; null, если чисел нет, больше 6, повторяются или вне 1…9999. */
fun parseQuickAmounts(text: String): List<Int>? {
    val parts = text.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty() || parts.size > MAX_QUICK_AMOUNTS) return null
    val values = parts.map { it.toIntOrNull() ?: return null }
    if (values.any { it !in 1..9999 } || values.toSet().size != values.size) return null
    return values
}

val Settings.quickAmountList: List<Int>
    get() = parseQuickAmounts(quickAmounts) ?: parseQuickAmounts(DEFAULT_QUICK_AMOUNTS)!!
