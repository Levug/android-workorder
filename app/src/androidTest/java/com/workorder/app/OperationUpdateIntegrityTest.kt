package com.workorder.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.OperationEntry
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.repository.OperationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationUpdateIntegrityTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun editingDurationKeepsExistingJournalEntries() = runBlocking {
        val operationRepository = OperationRepository(database.operationDao())
        val operationId = operationRepository.save(
            Operation(name = "Test operation", durationHours = 0.25)
        )
        val workDayId = database.workDayDao().insert(
            WorkDay(date = "2099-01-01", totalHours = 8.0)
        )
        database.operationEntryDao().insert(
            OperationEntry(
                workDayId = workDayId,
                operationId = operationId,
                quantity = 17,
                createdAt = 1L
            )
        )

        operationRepository.save(
            Operation(id = operationId, name = "Renamed operation", durationHours = 0.5)
        )

        val entries = database.operationEntryDao().observeEntriesForDay(workDayId).first()
        assertEquals(1, entries.size)
        assertEquals(17, entries.single().quantity)
        assertEquals("Renamed operation", entries.single().operationName)
        assertEquals(0.5, entries.single().durationHours, 0.0)
    }

    @Test
    fun repeatedWatchEventCannotCreateDuplicateJournalEntry() = runBlocking {
        val operationId = database.operationDao().insert(
            Operation(name = "Synced operation", durationHours = 0.25)
        )
        val workDayId = database.workDayDao().insert(
            WorkDay(date = "2099-01-02", totalHours = 8.0)
        )
        val event = OperationEntry(
            workDayId = workDayId,
            operationId = operationId,
            quantity = 10,
            createdAt = 2L,
            sourceEventId = "watch-event-1"
        )

        val firstId = database.operationEntryDao().insert(event)
        val duplicateId = database.operationEntryDao().insert(event)

        assertEquals(-1L, duplicateId)
        assertEquals(firstId, database.operationEntryDao().getIdBySourceEventId("watch-event-1"))
        assertEquals(1, database.operationEntryDao().observeEntriesForDay(workDayId).first().size)
    }
}
