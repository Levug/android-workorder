package com.workorder.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.model.*
import com.workorder.app.data.repository.BackupRepository
import com.workorder.app.data.repository.SettingsRepository
import com.workorder.app.data.repository.WorkOrderRepository
import com.workorder.app.util.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: BackupRepository
    private val full = TransferKind.FULL_DATABASE
    private val fixture = BackupSnapshot(
        operations = listOf(Operation(5, "Сборка", 0.5, 5, 1), Operation(9, "Контроль", 0.25, 3, 0)),
        settings = Settings(hourlyRate = 300.0, workSchedule = WorkSchedule.TWO_TWO, interfaceScale = 1.15, daySpecializationEnabled = true),
        workDays = listOf(WorkDay(4, "2099-01-01", 12.0, "Заметка\nна две строки", specialization = "Сборка"), WorkDay(7, "2099-01-02", 8.0)),
        entries = listOf(OperationEntry(20, 4, 5, 17, 1234, "from-watch"), OperationEntry(21, 4, 5, -2, 1235)),
        monthlyPlans = listOf(MonthlyPlan("2099-01", 184.0)),
        bankAllocations = listOf(BankAllocation("2098-12", 5, 3), BankAllocation("2099-01", 5, -1))
    )

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        repository = BackupRepository(db)
        repository.import(fixture, full) {}
    }
    @After fun tearDown() { db.close() }

    @Test fun roundTripPreservesAllTablesAndWatchIdempotency() = runBlocking {
        val before = repository.snapshot()
        val encoded = BackupCodec.encode(before, full)
        val imported = BackupCodec.decode(encoded, full)
        var safety: BackupSnapshot? = null
        repository.import(imported, full) { safety = it }
        repository.import(imported, full) {}
        assertEquals(before, safety)
        assertEquals(before, repository.snapshot())
        val work = WorkOrderRepository(db, SettingsRepository(db.settingsDao()))
        assertEquals(20L, work.addSyncedEntry("2099-01-01", 5, 17, 1234, "from-watch"))
        assertEquals(before, repository.snapshot())
        assertIntegrity()
    }

    @Test fun invalidImportAndFailedSafetyCopyNeverChangeData() = runBlocking {
        val before = repository.snapshot()
        var called = false
        try {
            repository.import(fixture.copy(entries = listOf(fixture.entries[0].copy(workDayId = 99))), full) { called = true }
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) { }
        assertFalse(called)
        try {
            repository.import(BackupSnapshot(emptyList(), null), full) { throw java.io.IOException("Disk full") }
            fail("Expected safety-copy failure")
        } catch (_: java.io.IOException) { }
        assertEquals(before, repository.snapshot())
    }

    @Test fun failureAfterDeletionRollsBackWholeTransaction() = runBlocking {
        val before = repository.snapshot()
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER fail_import BEFORE INSERT ON work_days
            WHEN NEW.date = '2099-01-03' BEGIN SELECT RAISE(ABORT, 'test failure'); END""")
        try {
            repository.import(fixture.copy(workDays = listOf(fixture.workDays[0].copy(date = "2099-01-03"))), full) {}
            fail("Expected database failure")
        } catch (_: android.database.SQLException) { }
        assertEquals(before, repository.snapshot())
        assertIntegrity()
    }

    @Test fun configurationMergesByNameNotForeignIdAndKeepsHistory() = runBlocking {
        val before = repository.snapshot()
        val config = BackupSnapshot(listOf(
            Operation(5, "Другая операция", 0.1, 4),
            Operation(777, "Сборка", 0.75, 6)
        ), null)
        repository.import(config, TransferKind.CONFIGURATION) {}
        val after = repository.snapshot()
        assertEquals(before.entries, after.entries)
        assertEquals(before.workDays, after.workDays)
        assertEquals(before.bankAllocations, after.bankAllocations)
        assertEquals(before.settings, after.settings)
        assertEquals("Сборка", db.operationDao().getById(5)!!.name)
        assertEquals(0.75, db.operationDao().getById(5)!!.durationHours, 0.0)
        assertNotEquals(5L, db.operationDao().getByName("Другая операция")!!.id)
        repository.import(config, TransferKind.CONFIGURATION) {}
        assertEquals(3, repository.snapshot().operations.size)
        assertIntegrity()
    }

    @Test fun dailyGraphUsesProducedHoursIncludingCorrectionsNotAttendanceOrBank() = runBlocking {
        val hours = db.operationEntryDao().observeDailyProducedHours("2099-01").first()
        assertEquals(listOf(7.5, 0.0), hours.map { it.hours })
        db.operationDao().update(db.operationDao().getById(5)!!.copy(durationHours = 1.0))
        assertEquals(15.0, db.operationEntryDao().observeDailyProducedHours("2099-01").first()[0].hours, 0.0)
        assertTrue(db.operationEntryDao().observeDailyProducedHours("2099-02").first().isEmpty())
    }

    @Test fun emptyFullBackupClearsAllSixTables() = runBlocking {
        repository.import(BackupSnapshot(emptyList(), null), full) {}
        assertEquals(BackupSnapshot(emptyList(), null), repository.snapshot())
        assertIntegrity()
    }

    @Test fun managerSavesReadsSharesAndCreatesUsableSafetyCopy() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = ExportImportManager(context, repository)
        val file = manager.prepareExport(full)
        val shared = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val parsed = manager.readImport(shared, full)
        val output = java.io.File(context.cacheDir, "saved-backup-test.json")
        manager.saveExport(file, android.net.Uri.fromFile(output))
        assertEquals(file.readText(), output.readText())
        manager.importData(parsed)
        assertTrue(manager.hasSafetyCopy())
        assertEquals(repository.snapshot(), BackupCodec.decode(manager.prepareSafetyExport().readText(), full))
        assertTrue(output.delete())
    }

    @Test fun editingSpecializationAndHidingItPreservesJournal() = runBlocking {
        val before = repository.snapshot()
        val preferences = SettingsRepository(db.settingsDao())
        val work = WorkOrderRepository(db, preferences)
        work.setDaySpecialization("2099-01-01", "  Наладка  ")
        preferences.update { it.copy(daySpecializationEnabled = false) }
        val after = repository.snapshot()
        assertEquals("Наладка", after.workDays.first().specialization)
        assertEquals(before.entries, after.entries)
        assertEquals(before.workDays.first().totalHours, after.workDays.first().totalHours, 0.0)
        assertEquals(before.workDays.first().comment, after.workDays.first().comment)
        assertEquals(before.bankAllocations, after.bankAllocations)
        preferences.update { it.copy(daySpecializationEnabled = true) }
        assertEquals("Наладка", repository.snapshot().workDays.first().specialization)
        work.setDaySpecialization("2099-01-01", "")
        assertEquals("", repository.snapshot().workDays.first().specialization)
        assertEquals(before.entries, repository.snapshot().entries)
        assertIntegrity()
    }

    @Test fun concurrentSettingsEditsDoNotOverwriteEachOther() = runBlocking {
        val preferences = SettingsRepository(db.settingsDao())
        kotlinx.coroutines.coroutineScope {
            launch { preferences.update { it.copy(hourlyRate = 999.0) } }
            launch { preferences.update { it.copy(interfaceScale = 1.3) } }
        }
        val updated = preferences.observe().first()
        assertEquals(999.0, updated.hourlyRate, 0.0)
        assertEquals(1.3, updated.interfaceScale, 0.0)
    }

    @Test fun specializationOnlyDayIsNotAttendanceAndSurvivesBackup() = runBlocking {
        val work = WorkOrderRepository(db, SettingsRepository(db.settingsDao()))
        val before = repository.snapshot()
        work.setDaySpecialization("2099-01-03", "  Наладка  ")
        val plan = work.observeDay("2099-01-03").first()!!
        assertFalse(plan.isRecorded)
        assertEquals(0.0, plan.totalHours, 0.0)
        assertEquals("Наладка", plan.specialization)
        assertEquals(before.workDays, work.observeRecordedMonth("2099-01").first())
        assertEquals(listOf(7.5, 0.0), work.observeDailyProducedHours("2099-01").first().map { it.hours })
        val snapshot = repository.snapshot()
        repository.import(BackupCodec.decode(BackupCodec.encode(snapshot, full), full), full) {}
        assertEquals(snapshot, repository.snapshot())
        work.setDaySpecialization("2099-01-03", "")
        assertEquals(before.workDays, work.observeRecordedMonth("2099-01").first())
        assertEquals(before.entries, repository.snapshot().entries)
        assertIntegrity()
    }

    @Test fun addingPhoneOrWatchOperationPromotesPlanExactlyOnceAndKeepsText() = runBlocking {
        val preferences = SettingsRepository(db.settingsDao())
        preferences.update { it.copy(defaultDayHours = 9.5) }
        val work = WorkOrderRepository(db, preferences)
        for (watch in listOf(false, true)) {
            val date = if (watch) "2099-01-04" else "2099-01-03"
            work.setDaySpecialization(date, "Сборка")
            val id = work.observeDay(date).first()!!.id
            if (watch) {
                val entry = work.addSyncedEntry(date, 5, 17, 5555, "planned-day-watch")
                assertEquals(entry, work.addSyncedEntry(date, 5, 17, 5555, "planned-day-watch"))
            } else work.addEntry(date, 5, 17)
            val recorded = work.observeDay(date).first()!!
            assertEquals(id, recorded.id)
            assertTrue(recorded.isRecorded)
            assertEquals(9.5, recorded.totalHours, 0.0)
            assertEquals("Сборка", recorded.specialization)
            assertEquals(17, work.observeDayTotals(date).first().single().totalCount)
            work.setDayHours(date, 0.0)
            work.addEntry(date, 5, 1)
            assertTrue(work.observeDay(date).first()!!.isRecorded)
            assertEquals(0.0, work.observeDay(date).first()!!.totalHours, 0.0)
        }
        assertIntegrity()
    }

    @Test fun explicitHoursMarkPlanButFailedOperationDoesNot() = runBlocking {
        val work = WorkOrderRepository(db, SettingsRepository(db.settingsDao()))
        val date = "2099-01-03"
        work.setDaySpecialization(date, "Контроль")
        val plan = work.observeDay(date).first()!!
        try {
            work.addEntry(date, 999, 1)
            fail("Invalid foreign key must roll back promotion")
        } catch (_: android.database.SQLException) { }
        assertEquals(plan, work.observeDay(date).first())
        work.setDayHours(date, 11.0)
        assertEquals(plan.copy(totalHours = 11.0, isRecorded = true), work.observeDay(date).first())
        assertIntegrity()
    }

    private fun assertIntegrity() {
        db.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        db.openHelper.writableDatabase.query("PRAGMA integrity_check").use { assertTrue(it.moveToFirst()); assertEquals("ok", it.getString(0)) }
    }
}
