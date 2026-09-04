package com.workorder.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workorder.app.data.model.*

/** Used only inside a transaction by BackupRepository. Restore inserts must never ignore conflicts. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM operations ORDER BY sortOrder, id")
    suspend fun operations(): List<Operation>
    @Query("SELECT * FROM work_days ORDER BY date")
    suspend fun days(): List<WorkDay>
    @Query("SELECT * FROM operation_entries ORDER BY id")
    suspend fun entries(): List<OperationEntry>
    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settings(): Settings?
    @Query("SELECT * FROM monthly_plans ORDER BY yearMonth")
    suspend fun plans(): List<MonthlyPlan>
    @Query("SELECT * FROM bank_allocations ORDER BY yearMonth, operationId")
    suspend fun allocations(): List<BankAllocation>

    @Query("DELETE FROM operation_entries")
    suspend fun clearEntries()
    @Query("DELETE FROM bank_allocations")
    suspend fun clearAllocations()
    @Query("DELETE FROM monthly_plans")
    suspend fun clearPlans()
    @Query("DELETE FROM work_days")
    suspend fun clearDays()
    @Query("DELETE FROM operations")
    suspend fun clearOperations()
    @Query("DELETE FROM settings")
    suspend fun clearSettings()

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperations(items: List<Operation>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDays(items: List<WorkDay>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntries(items: List<OperationEntry>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlans(items: List<MonthlyPlan>)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllocations(items: List<BankAllocation>)
}
