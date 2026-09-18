package com.workorder.app.ui.viewmodel

import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkSchedule
import java.time.LocalDate

/** Text drafts preserve incomplete input until the user explicitly saves the form. */
data class SettingsForm(val stored: Settings, val edits: Map<String, String>) {
    fun text(field: String): String = edits[field] ?: when (field) {
        RATE -> stored.hourlyRate.toString()
        CONTRACT -> stored.contractHourlyRate.toString()
        HOURS -> stored.defaultDayHours.toString()
        ANCHOR -> stored.shiftAnchorDate
        SCHEDULE -> stored.workSchedule.name
        else -> kotlin.error("Unknown settings field")
    }
    val schedule get() = WorkSchedule.valueOf(text(SCHEDULE))
    fun number(field: String) = text(field).replace(',', '.').filterNot(Char::isWhitespace).toDoubleOrNull()
    fun error(field: String): String? = when (field) {
        RATE, CONTRACT -> if (number(field)?.let { it.isFinite() && it >= 0 } == true) null else "Введите ставку от 0"
        HOURS -> if (number(field)?.let { it.isFinite() && it > 0 } == true) null else "Введите число больше 0"
        ANCHOR -> if (schedule != WorkSchedule.TWO_TWO || runCatching {
            LocalDate.parse(text(ANCHOR)).toString() == text(ANCHOR)
        }.getOrDefault(false)) null else "Дата в формате ГГГГ-ММ-ДД"
        else -> null
    }
    val valid get() = listOf(RATE, CONTRACT, HOURS, ANCHOR).all { error(it) == null }
    val dirty get() = edits.isNotEmpty()

    fun applyTo(current: Settings): Settings {
        require(valid) { "Исправьте отмеченные поля" }
        return current.copy(
            hourlyRate = if (RATE in edits) number(RATE)!! else current.hourlyRate,
            contractHourlyRate = if (CONTRACT in edits) number(CONTRACT)!! else current.contractHourlyRate,
            defaultDayHours = if (HOURS in edits) number(HOURS)!! else current.defaultDayHours,
            workSchedule = if (SCHEDULE in edits) schedule else current.workSchedule,
            shiftAnchorDate = if (ANCHOR in edits && schedule == WorkSchedule.TWO_TWO) text(ANCHOR) else current.shiftAnchorDate
        )
    }

    companion object {
        const val RATE = "rate"
        const val CONTRACT = "contract"
        const val HOURS = "hours"
        const val ANCHOR = "anchor"
        const val SCHEDULE = "schedule"
    }
}
