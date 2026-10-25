package com.workorder.app.ui.viewmodel

import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkSchedule
import com.workorder.app.data.model.parseQuickAmounts
import java.time.LocalDate

/** 400.0 → «400», 8.5 → «8,5»: без технического «.0» в полях ввода. */
private fun Double.plain(): String =
    if (this % 1.0 == 0.0 && kotlin.math.abs(this) < 1e15) toLong().toString() else toString().replace('.', ',')

/** Text drafts preserve incomplete input until the user explicitly saves the form. */
data class SettingsForm(val stored: Settings, val edits: Map<String, String>) {
    fun text(field: String): String = edits[field] ?: when (field) {
        RATE -> stored.hourlyRate.plain()
        CONTRACT -> stored.contractHourlyRate.plain()
        HOURS -> stored.defaultDayHours.plain()
        ANCHOR -> stored.shiftAnchorDate
        SCHEDULE -> stored.workSchedule.name
        QUICK -> stored.quickAmounts.replace(",", ", ")
        COMP -> stored.compTimeInitialHours.plain()
        else -> kotlin.error("Unknown settings field")
    }
    val schedule get() = WorkSchedule.valueOf(text(SCHEDULE))
    fun number(field: String) = text(field).replace(',', '.').filterNot(Char::isWhitespace).toDoubleOrNull()
    fun error(field: String): String? = when (field) {
        RATE, CONTRACT -> if (number(field)?.let { it.isFinite() && it >= 0 } == true) null else "Введите ставку от 0"
        HOURS -> if (number(field)?.let { it.isFinite() && it > 0 } == true) null else "Введите число больше 0"
        ANCHOR -> if (schedule != WorkSchedule.TWO_TWO || anchorDate != null) null else "Выберите дату"
        QUICK -> if (parseQuickAmounts(text(QUICK)) != null) null else "От 1 до 6 разных чисел, например 1, 5, 10, 17"
        COMP -> if (number(field)?.let { it.isFinite() && it >= 0 } == true) null else "Введите часы от 0"
        else -> null
    }
    val anchorDate: LocalDate? get() = runCatching {
        LocalDate.parse(text(ANCHOR)).takeIf { it.toString() == text(ANCHOR) }
    }.getOrNull()
    val valid get() = listOf(RATE, CONTRACT, HOURS, ANCHOR, QUICK, COMP).all { error(it) == null }
    val dirty get() = edits.isNotEmpty()

    /** Черновик как настройки — для предпросмотра графика до сохранения. */
    val preview: Settings get() = stored.copy(
        workSchedule = schedule,
        shiftAnchorDate = anchorDate?.toString() ?: stored.shiftAnchorDate
    )

    fun applyTo(current: Settings): Settings {
        require(valid) { "Исправьте отмеченные поля" }
        return current.copy(
            hourlyRate = if (RATE in edits) number(RATE)!! else current.hourlyRate,
            contractHourlyRate = if (CONTRACT in edits) number(CONTRACT)!! else current.contractHourlyRate,
            defaultDayHours = if (HOURS in edits) number(HOURS)!! else current.defaultDayHours,
            workSchedule = if (SCHEDULE in edits) schedule else current.workSchedule,
            shiftAnchorDate = if (ANCHOR in edits && schedule == WorkSchedule.TWO_TWO) text(ANCHOR) else current.shiftAnchorDate,
            quickAmounts = if (QUICK in edits) parseQuickAmounts(text(QUICK))!!.joinToString(",") else current.quickAmounts,
            compTimeInitialHours = if (COMP in edits) number(COMP)!! else current.compTimeInitialHours
        )
    }

    companion object {
        const val RATE = "rate"
        const val CONTRACT = "contract"
        const val HOURS = "hours"
        const val ANCHOR = "anchor"
        const val SCHEDULE = "schedule"
        const val QUICK = "quick"
        const val COMP = "compTime"
    }
}
