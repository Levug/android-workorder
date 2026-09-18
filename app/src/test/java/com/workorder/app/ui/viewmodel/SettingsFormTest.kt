package com.workorder.app.ui.viewmodel

import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkSchedule
import org.junit.Assert.*
import org.junit.Test

class SettingsFormTest {
    @Test fun commaDecimalsAndOnlyEditedFieldsAreApplied() {
        val form = SettingsForm(Settings(), mapOf(SettingsForm.RATE to "1 234,50", SettingsForm.HOURS to "9,5"))
        val latest = Settings(contractHourlyRate = 222.0, interfaceScale = 1.3, daySpecializationEnabled = true)
        val saved = form.applyTo(latest)
        assertEquals(1234.5, saved.hourlyRate, 0.0)
        assertEquals(9.5, saved.defaultDayHours, 0.0)
        assertEquals(222.0, saved.contractHourlyRate, 0.0)
        assertEquals(1.3, saved.interfaceScale, 0.0)
        assertTrue(saved.daySpecializationEnabled)
    }

    @Test fun incompleteNegativeAndInfiniteInputCannotBeSaved() {
        listOf("", "-", "NaN", "Infinity", "-100").forEach {
            val form = SettingsForm(Settings(), mapOf(SettingsForm.RATE to it))
            assertTrue(form.dirty)
            assertFalse(form.valid)
            assertThrows(IllegalArgumentException::class.java) { form.applyTo(Settings()) }
        }
        assertFalse(SettingsForm(Settings(), mapOf(SettingsForm.HOURS to "0")).valid)
    }

    @Test fun scheduleAndAnchorAreSavedTogetherAndInvalidDatesAreRejected() {
        val form = SettingsForm(Settings(), mapOf(SettingsForm.SCHEDULE to "TWO_TWO", SettingsForm.ANCHOR to "2026-09-06"))
        val updated = form.applyTo(Settings())
        assertEquals(WorkSchedule.TWO_TWO, updated.workSchedule)
        assertEquals("2026-09-06", updated.shiftAnchorDate)
        assertFalse(form.copy(edits = form.edits + (SettingsForm.ANCHOR to "2026-02-30")).valid)
    }
}
