package com.workorder.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.model.BankStrategy
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.OperationEntry
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.repository.BankRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankRepositoryTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java
        ).build()
    }

    @After fun tearDown() = db.close()

    @Test
    fun priorityKeepsHigherGradeInReport() = runBlocking {
        val low = db.operationDao().insert(Operation(name = "Grade 4", durationHours = 1.0, grade = 4))
        val high = db.operationDao().insert(Operation(name = "Grade 5", durationHours = 1.0, grade = 5))
        val day = db.workDayDao().insert(WorkDay(date = "2099-02-01", totalHours = 8.0))
        db.operationEntryDao().insert(OperationEntry(workDayId = day, operationId = low, quantity = 5, createdAt = 1))
        db.operationEntryDao().insert(OperationEntry(workDayId = day, operationId = high, quantity = 5, createdAt = 2))

        val repository = BankRepository(db)
        repository.updatePlan("2099-02", plannedHours = 5.0, coefficient = 1.0, strategy = BankStrategy.PRIORITY)
        repository.applyAutomatic(repository.observe("2099-02").first())

        val state = repository.observe("2099-02").first()
        assertEquals(0, state.lines.single { it.operation.id == low }.adjustedQuantity)
        assertEquals(5, state.lines.single { it.operation.id == high }.adjustedQuantity)
        assertEquals(5.0, state.adjustedHours, 0.0)
    }

    @Test
    fun priorityReturnsHigherGradeFromExistingBankFirst() = runBlocking {
        val low = db.operationDao().insert(Operation(name = "Grade 4", durationHours = 1.0, grade = 4))
        val high = db.operationDao().insert(Operation(name = "Grade 5", durationHours = 1.0, grade = 5))
        db.bankDao().insertAllocations(
            listOf(
                com.workorder.app.data.model.BankAllocation("2099-01", low, 3),
                com.workorder.app.data.model.BankAllocation("2099-01", high, 3)
            )
        )
        val repository = BankRepository(db)
        repository.updatePlan("2099-02", plannedHours = 3.0, coefficient = 1.0, strategy = BankStrategy.PRIORITY)
        repository.applyAutomatic(repository.observe("2099-02").first())

        val state = repository.observe("2099-02").first()
        assertEquals(3, state.lines.single { it.operation.id == high }.adjustedQuantity)
        assertEquals(0, state.lines.single { it.operation.id == low }.adjustedQuantity)
        assertEquals(3, state.lines.single { it.operation.id == low }.balanceAfter)
    }
}
