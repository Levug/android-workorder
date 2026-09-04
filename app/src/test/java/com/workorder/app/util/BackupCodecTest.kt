package com.workorder.app.util

import com.google.gson.JsonParser
import com.workorder.app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val full = TransferKind.FULL_DATABASE
    private val sample = BackupSnapshot(
        operations = listOf(Operation(71, "Длинная операция \"А\" / Б", 0.375, 6, 3)),
        settings = Settings(hourlyRate = 423.5, workSchedule = WorkSchedule.TWO_TWO, shiftAnchorDate = "2026-09-03"),
        workDays = listOf(WorkDay(12, "2026-09-04", 11.5, "Многострочная\nзаметка", WorkDayType.HOLIDAY)),
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
        assertRejected { it.addProperty("databaseVersion", 10) }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode("""{"operations":[],"settings":null}""", full)
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
