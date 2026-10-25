package com.workorder.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.data.repository.OperationRepository
import com.workorder.app.data.repository.SettingsRepository
import com.workorder.app.data.repository.WorkOrderRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayPlanningTest {
    private lateinit var db: AppDatabase
    private lateinit var work: WorkOrderRepository
    private lateinit var operations: OperationRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.endsWith(".qa"))
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        work = WorkOrderRepository(db, SettingsRepository(db.settingsDao()))
        operations = OperationRepository(db.operationDao())
    }

    @After fun tearDown() = db.close()

    @Test fun commentAndDayTypeOnEmptyDayDoNotRecordAShift() = runBlocking {
        work.setDayComment("2099-05-04", "Взять инструмент")
        work.setDayType("2099-05-05", WorkDayType.HOLIDAY)
        work.setDayType("2099-05-06", WorkDayType.AUTO)
        val days = db.workDayDao().observeByMonth("2099-05").first().associateBy { it.date }
        assertFalse(days.getValue("2099-05-04").isRecorded)
        assertEquals(0.0, days.getValue("2099-05-04").totalHours, 0.0)
        assertFalse(days.getValue("2099-05-05").isRecorded)
        assertNull(days["2099-05-06"])
        assertTrue(work.observeRecordedMonth("2099-05").first().isEmpty())

        // Пустой план не остаётся в базе, а ручные часы превращают план в смену с сохранением заметки.
        work.setDayComment("2099-05-04", "")
        assertNull(db.workDayDao().getByDate("2099-05-04"))
        work.setDayHours("2099-05-05", 0.0)
        val recorded = db.workDayDao().getByDate("2099-05-05")!!
        assertTrue(recorded.isRecorded)
        assertEquals(WorkDayType.HOLIDAY, recorded.dayType)
    }

    @Test fun compTimeBalanceCountsCompWorkMinusCompOff() = runBlocking {
        SettingsRepository(db.settingsDao()).update { it.copy(compTimeInitialHours = 4.0, defaultDayHours = 8.0) }
        // Выход за отгул на пустом дне — только план, баланс не меняется, пока нет часов.
        work.setDayType("2099-05-02", WorkDayType.COMP_WORK)
        assertEquals(4.0, work.observeCompTimeBalance().first(), 0.0)
        work.setDayHours("2099-05-02", 11.0)
        assertEquals(15.0, work.observeCompTimeBalance().first(), 0.0)

        // Отгул сразу отмечает оплачиваемую смену стандартной длины и списывает её из баланса.
        work.setDayType("2099-05-05", WorkDayType.COMP_OFF)
        val off = db.workDayDao().getByDate("2099-05-05")!!
        assertTrue(off.isRecorded)
        assertEquals(8.0, off.totalHours, 0.0)
        assertEquals(7.0, work.observeCompTimeBalance().first(), 0.0)

        work.setDayType("2099-05-05", WorkDayType.AUTO)
        assertEquals(15.0, work.observeCompTimeBalance().first(), 0.0)

        // Отгул на часть дня: отработано 4 ч + 4 ч из баланса.
        work.setDayHours("2099-05-06", 4.0, compOffHours = 4.0)
        assertEquals(11.0, work.observeCompTimeBalance().first(), 0.0)
        work.setDayType("2099-05-06", WorkDayType.HOLIDAY)
        assertEquals(0.0, db.workDayDao().getByDate("2099-05-06")!!.compOffHours, 0.0)
        assertEquals(15.0, work.observeCompTimeBalance().first(), 0.0)
    }

    @Test fun manualHoursRecordShiftAndAreClearedWhenLeavingManualMode() = runBlocking {
        work.setDayComment("2099-05-06", "Аврал")
        work.setManualHours("2099-05-06", 6.0, 3.5, 1.5)
        val manual = db.workDayDao().getByDate("2099-05-06")!!
        assertTrue(manual.isRecorded)
        assertEquals(WorkDayType.MANUAL, manual.dayType)
        assertEquals(9.5, manual.totalHours, 0.0)
        assertEquals("Аврал", manual.comment)
        assertEquals(1.5, manual.manualMultiplier, 0.0)

        // Новые часы без диалога: двойные сохраняются, остаток — одинарные.
        work.setDayHours("2099-05-06", 5.0)
        val shortened = db.workDayDao().getByDate("2099-05-06")!!
        assertEquals(1.5, shortened.manualSingleHours, 0.0)
        assertEquals(3.5, shortened.manualDoubleHours, 0.0)

        work.setDayType("2099-05-06", WorkDayType.HOLIDAY)
        val holiday = db.workDayDao().getByDate("2099-05-06")!!
        assertEquals(0.0, holiday.manualSingleHours + holiday.manualDoubleHours, 0.0)
        assertEquals(2.0, holiday.manualMultiplier, 0.0)
        assertEquals(5.0, holiday.totalHours, 0.0)
        assertThrows(IllegalArgumentException::class.java) { runBlocking { work.setManualHours("2099-05-07", 20.0, 5.0) } }
    }

    @Test fun removedEntryIsRestoredWithOriginalIdAndTime() = runBlocking {
        val operationId = operations.save(Operation(name = "Сборка", durationHours = 0.5))
        val entryId = work.addEntry("2099-05-04", operationId, 17)
        val removed = work.removeEntry(entryId)!!
        assertTrue(work.observeDayEntries("2099-05-04").first().isEmpty())
        assertTrue(work.restoreEntry(removed))
        val restored = work.observeDayEntries("2099-05-04").first().single()
        assertEquals(entryId, restored.id)
        assertEquals(removed.createdAt, restored.createdAt)
        assertEquals(17, restored.quantity)
    }

    @Test fun archivedOperationKeepsHistoryButLeavesPickerAndRecent() = runBlocking {
        val kept = operations.save(Operation(name = "Сборка", durationHours = 0.5))
        val old = operations.save(Operation(name = "Старая", durationHours = 0.25))
        work.addEntry("2099-05-04", old, 5)
        kotlinx.coroutines.delay(10) // разное время записи — однозначный порядок «недавних»
        work.addEntry("2099-05-04", kept, 17)
        assertEquals(listOf(kept, old), work.observeRecentOperations().first().map { it.operationId })
        assertEquals(17, work.observeRecentOperations().first().first().lastQuantity)

        operations.setArchived(old, true)
        operations.save(Operation(id = old, name = "Старая (переименована)", durationHours = 0.25))
        assertTrue(operations.getById(old)!!.archived)
        assertEquals(listOf(kept), operations.observeActive().first().map { it.id })
        assertEquals(listOf(kept), work.observeRecentOperations().first().map { it.operationId })
        assertEquals(2, work.observeDayTotals("2099-05-04").first().size)
        assertEquals(1, work.operationUsage(old).entries)
    }
}
