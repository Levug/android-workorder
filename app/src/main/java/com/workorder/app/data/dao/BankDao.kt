package com.workorder.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workorder.app.data.model.BankAllocation
import com.workorder.app.data.model.MonthlyPlan
import kotlinx.coroutines.flow.Flow

data class BankBalance(
    val operationId: Long,
    val quantity: Int
)

@Dao
interface BankDao {
    @Query("SELECT * FROM monthly_plans WHERE yearMonth = :yearMonth")
    fun observePlan(yearMonth: String): Flow<MonthlyPlan?>

    @Query("SELECT * FROM monthly_plans WHERE yearMonth = :yearMonth")
    suspend fun getPlan(yearMonth: String): MonthlyPlan?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(plan: MonthlyPlan)

    @Query("SELECT * FROM bank_allocations WHERE yearMonth = :yearMonth")
    fun observeAllocations(yearMonth: String): Flow<List<BankAllocation>>

    @Query("SELECT * FROM bank_allocations WHERE yearMonth = :yearMonth")
    suspend fun getAllocations(yearMonth: String): List<BankAllocation>

    @Query(
        """
        SELECT operationId, COALESCE(SUM(bankedQuantity), 0) AS quantity
        FROM bank_allocations
        WHERE yearMonth < :yearMonth
        GROUP BY operationId
        HAVING COALESCE(SUM(bankedQuantity), 0) <> 0
        """
    )
    fun observeBalanceBefore(yearMonth: String): Flow<List<BankBalance>>

    @Query("DELETE FROM bank_allocations WHERE yearMonth = :yearMonth")
    suspend fun clearMonth(yearMonth: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllocations(allocations: List<BankAllocation>)
}
