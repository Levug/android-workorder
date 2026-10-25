package com.workorder.app.util

import com.google.gson.JsonParser
import com.workorder.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val full = TransferKind.FULL_DATABASE
    private val sample = BackupSnapshot(
        operations = listOf(Operation(71, "Длинная операция \"А\" / Б", 0.375, 6, 3)),
        settings = Settings(hourlyRate = 423.5, workSchedule = WorkSchedule.TWO_TWO, shiftAnchorDate = "2026-09-03", interfaceScale = 1.15, daySpecializationEnabled = true),
        workDays = listOf(WorkDay(12, "2026-09-04", 11.5, "Многострочная\nзаметка", WorkDayType.HOLIDAY, "Наладка и сборка")),
        entries = listOf(OperationEntry(81, 12, 71, 17, 1788530000123L, "watch-uuid"), OperationEntry(82, 12, 71, -2, 1788530000999L)),
        monthlyPlans = listOf(MonthlyPlan("2026-09", 184.0, 1.15, BankStrategy.GRADE_RATIO, 40.0, 30.0, 20.0, 10.0)),
        bankAllocations = listOf(BankAllocation("2026-08", 71, 7), BankAllocation("2026-09", 71, -3))
    )

    @Test fun fullRoundTripIsLossless() {
        assertEquals(sample, BackupCodec.decode(BackupCodec.encode(sample, full), full))
    }

    @Test fun emptyDatabaseAndAbsentSettingsRoundTrip() {
        val empty = BackupSnapshot(emptyList(), null)
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty, full), full))
    }

    @Test fun legacyConfigurationAndBomAreSupported() {
        val json = "\uFEFF" + """{"operations":[{"id":4,"name":"Операция","durationHours":0.5}],"settings":{"hourlyRate":200}}"""
        val result = BackupCodec.decode(json, TransferKind.CONFIGURATION)
        assertEquals(3, result.operations.single().grade)
        assertEquals(8.0, result.settings!!.defaultDayHours, 0.0)
    }

    @Test fun configurationDoesNotContainHistory() {
        val json = BackupCodec.encode(sample, TransferKind.CONFIGURATION)
        val decoded = BackupCodec.decode(json, TransferKind.CONFIGURATION)
        assertEquals(sample.operations, decoded.operations)
        assertTrue(decoded.entries.isEmpty())
        assertFalse(JsonParser.parseString(json).asJsonObject.has("workDays"))
    }

    @Test fun incompleteFullBackupIsRejectedInsteadOfWipingMissingTables() {
        assertRejected { it.remove("entries") }
        assertRejected { it.remove("settings") }
        assertRejected { it.add("monthlyPlans", null) }
    }

    @Test fun rejectsWrongFormatAndFutureVersion() {
        assertRejected { it.addProperty("format", "unknown") }
        assertRejected { it.addProperty("formatVersion", 2) }
        assertRejected { it.addProperty("databaseVersion", 17) }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("""{"operations":[],"settings":null}""", full)
        }
    }

    @Test fun version34BackupReceivesDefaultsWithoutLosingHistory() {
        val root = JsonParser.parseString(BackupCodec.encode(sample, full)).asJsonObject
        root.addProperty("databaseVersion", 9)
        root.getAsJsonObject("settings").remove("interfaceScale")
        root.getAsJsonObject("settings").remove("daySpecializationEnabled")
        root.getAsJsonArray("workDays")[0].asJsonObject.remove("specialization")
        val restored = BackupCodec.decode(root.toString(), full)
        assertEquals(sample.entries, restored.entries)
        assertEquals(1.0, restored.settings!!.interfaceScale, 0.0)
        assertFalse(restored.settings!!.daySpecializationEnabled)
        assertEquals("", restored.workDays.single().specialization)
    }

    @Test fun newFieldsAreValidated() {
        assertRejected { it.getAsJsonArray("workDays")[0].asJsonObject.remove("isRecorded") }
        assertRejected { it.getAsJsonArray("workDays")[0].asJsonObject.addProperty("isRecorded", "false") }
        assertRejected { it.getAsJsonObject("settings").addProperty("interfaceScale", 5) }
        assertRejected { it.getAsJsonObject("settings").remove("interfaceScale") }
        assertRejected { it.getAsJsonObject("settings").remove("daySpecializationEnabled") }
        assertRejected { it.getAsJsonArray("workDays")[0].asJsonObject.remove("specialization") }
        assertRejected { it.getAsJsonArray("workDays")[0].asJsonObject.addProperty("specialization", "x".repeat(121)) }
    }

    @Test fun plannedSpecializationsRoundTripWithoutBecomingAttendance() {
        val planned = WorkDay(99, "2026-09-16", 0.0, specialization = "Наладка", isRecorded = false)
        val data = sample.copy(workDays = sample.workDays + planned)
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data, full), full))
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.encode(data.copy(workDays = sample.workDays + planned.copy(totalHours = 8.0)), full)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.encode(data.copy(entries = sample.entries + OperationEntry(99, 99, 71, 1, 123)), full)
        }
    }

    @Test fun version35BackupKeepsRecordedZeroHoursAndSpecialization() {
        val data = sample.copy(workDays = sample.workDays.map { it.copy(totalHours = 0.0) })
        val root = JsonParser.parseString(BackupCodec.encode(data, full)).asJsonObject
        root.addProperty("databaseVersion", 10)
        root.getAsJsonArray("workDays").forEach { it.asJsonObject.remove("isRecorded") }
        assertEquals(data, BackupCodec.decode(root.toString(), full))
    }

    @Test fun archiveAndQuickAmountsRoundTrip() {
        val data = sample.copy(
            operations = sample.operations + Operation(72, "Старая операция", 0.5, 4, 4, archived = true),
            settings = sample.settings!!.copy(quickAmounts = "2,4,8,16")
        )
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data, full), full))
        assertRejected { it.getAsJsonObject("settings").addProperty("quickAmounts", "1,2,3,4,5,6,7") }
        assertRejected { it.getAsJsonObject("settings").remove("quickAmounts") }
        assertRejected { it.getAsJsonArray("operations")[0].asJsonObject.remove("archived") }
    }

    @Test fun version36BackupGetsActiveOperationsAndDefaultQuickAmounts() {
        val root = JsonParser.parseString(BackupCodec.encode(sample, full)).asJsonObject
        root.addProperty("databaseVersion", 11)
        root.getAsJsonArray("operations").forEach { it.asJsonObject.remove("archived") }
        root.getAsJsonObject("settings").remove("quickAmounts")
        val restored = BackupCodec.decode(root.toString(), full)
        assertFalse(restored.operations.single().archived)
        assertEquals(DEFAULT_QUICK_AMOUNTS, restored.settings!!.quickAmounts)
        assertEquals(sample.entries, restored.entries)
    }

    @Test fun compTimeManualHoursAndCustomThemeRoundTrip() {
        val data = sample.copy(
            settings = sample.settings!!.copy(themePreset = CUSTOM_THEME_PRESET, customThemeColor = 0xFFE53935.toInt(),
                summaryQuickAddEnabled = false, compTimeInitialHours = 12.5, advanceFillEnabled = true,
                bankEnabled = false, daySwipeEnabled = false, calendarHeatEnabled = false, quickAddEnabled = false),
            workDays = sample.workDays + listOf(
                WorkDay(13, "2026-09-05", 8.0, dayType = WorkDayType.COMP_WORK),
                WorkDay(14, "2026-09-07", 8.0, dayType = WorkDayType.COMP_OFF),
                WorkDay(16, "2026-09-09", 4.0, compOffHours = 4.0),
                WorkDay(17, "2026-09-10", 0.0, dayType = WorkDayType.UNPAID, isRecorded = false),
                WorkDay(15, "2026-09-08", 10.0, dayType = WorkDayType.MANUAL, manualSingleHours = 7.0, manualDoubleHours = 3.0,
                    manualMultiplier = 1.75)
            )
        )
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data, full), full))
    }

    @Test fun version37BackupGetsDefaultsForNewFields() {
        val root = JsonParser.parseString(BackupCodec.encode(sample, full)).asJsonObject
        root.addProperty("databaseVersion", 12)
        listOf("customThemeColor", "summaryQuickAddEnabled", "compTimeInitialHours", "advanceFillEnabled")
            .forEach { root.getAsJsonObject("settings").remove(it) }
        root.getAsJsonArray("workDays").forEach { it.asJsonObject.remove("manualSingleHours"); it.asJsonObject.remove("manualDoubleHours") }
        val restored = BackupCodec.decode(root.toString(), full)
        assertEquals(sample, restored)
        assertTrue(restored.settings!!.summaryQuickAddEnabled)
        assertEquals(DEFAULT_CUSTOM_THEME_COLOR, restored.settings!!.customThemeColor)
    }

    @Test fun version38BackupGetsClassicAdvance() {
        val root = JsonParser.parseString(BackupCodec.encode(sample, full)).asJsonObject
        root.addProperty("databaseVersion", 13)
        root.getAsJsonObject("settings").remove("advanceFillEnabled")
        assertFalse(BackupCodec.decode(root.toString(), full).settings!!.advanceFillEnabled)
    }

    @Test fun version38And39BackupsGetDefaultTogglesAndMultiplier() {
        val root = JsonParser.parseString(BackupCodec.encode(sample, full)).asJsonObject
        root.addProperty("databaseVersion", 14)
        listOf("bankEnabled", "daySwipeEnabled", "calendarHeatEnabled", "quickAddEnabled").forEach { root.getAsJsonObject("settings").remove(it) }
        root.getAsJsonArray("workDays").forEach { it.asJsonObject.remove("manualMultiplier") }
        val restored = BackupCodec.decode(root.toString(), full)
        assertEquals(sample, restored)
        assertTrue(restored.settings!!.bankEnabled && restored.settings!!.calendarHeatEnabled)
        assertRejected { it.getAsJsonObject("settings").remove("bankEnabled") }
        assertRejected { it.getAsJsonArray("workDays")[0].asJsonObject.addProperty("manualMultiplier", 0.5) }
    }

    @Test fun version40BackupGetsNoPartialCompOff() {
        val root = JsonParser.parseString(BackupCodec.encode(sample, full)).asJsonObject
        root.addProperty("databaseVersion", 15)
        root.getAsJsonArray("workDays").forEach { it.asJsonObject.remove("compOffHours") }
        assertEquals(sample, BackupCodec.decode(root.toString(), full))
        assertRejected { it.getAsJsonArray("workDays")[0].asJsonObject.remove("compOffHours") }
        // Отгул на часть дня у праздника (sample — HOLIDAY) недопустим.
        assertRejected { it.getAsJsonArray("workDays")[0].asJsonObject.addProperty("compOffHours", 2.0) }
    }

    @Test fun manualHoursMustMatchDayHoursAndType() {
        assertRejected { it.getAsJsonObject("settings").remove("compTimeInitialHours") }
        assertRejected { it.getAsJsonObject("settings").remove("advanceFillEnabled") }
        assertRejected { it.getAsJsonObject("settings").addProperty("compTimeInitialHours", -1) }
        // Ручные часы у обычного дня
        assertRejected { it.getAsJsonArray("workDays")[0].asJsonObject.addProperty("manualDoubleHours", 2.0) }
        // Ручной день, где одинарные + двойные не равны часам дня
        assertRejected {
            val day = it.getAsJsonArray("workDays")[0].asJsonObject
            day.addProperty("dayType", "MANUAL")
            day.addProperty("manualSingleHours", 3.0)
            day.addProperty("manualDoubleHours", 3.0)
        }
    }

    @Test fun invalidNumbersDoNotSilentlyTruncateOrBecomeZero() {
        assertRejected { it.getAsJsonArray("entries")[0].asJsonObject.addProperty("quantity", 1.25) }
        assertRejected { it.getAsJsonArray("entries")[0].asJsonObject.addProperty("quantity", 2147483648L) }
        assertRejected { it.getAsJsonArray("operations")[0].asJsonObject.addProperty("durationHours", "NaN") }
        assertRejected { it.getAsJsonArray("operations")[0].asJsonObject.addProperty("durationHours", -2) }
        assertRejected { it.getAsJsonArray("operations")[0].asJsonObject.addProperty("durationHours", Double.POSITIVE_INFINITY) }
    }

    @Test fun invalidEnumsAndDatesAreRejected() {
        assertRejected { it.getAsJsonArray("workDays")[0].asJsonObject.addProperty("dayType", "OTHER") }
        assertRejected { it.getAsJsonArray("workDays")[0].asJsonObject.addProperty("date", "2026-02-30") }
        assertRejected { it.getAsJsonArray("operations")[0].asJsonObject.addProperty("grade", 9) }
    }

    @Test fun duplicateKeysAndBrokenForeignKeysAreRejected() {
        assertRejected { it.getAsJsonArray("operations").add(it.getAsJsonArray("operations")[0].deepCopy()) }
        assertRejected { it.getAsJsonArray("entries")[0].asJsonObject.addProperty("workDayId", 9999) }
        assertRejected { it.getAsJsonArray("bankAllocations")[0].asJsonObject.addProperty("operationId", 9999) }
        assertRejected { it.getAsJsonArray("entries")[1].asJsonObject.addProperty("sourceEventId", "watch-uuid") }
    }

    private fun assertRejected(change: (com.google.gson.JsonObject) -> Unit) {
        val root = JsonParser.parseString(BackupCodec.encode(sample, full)).asJsonObject
        change(root)
        assertThrows(Exception::class.java) { BackupCodec.decode(root.toString(), full) }
    }
}
