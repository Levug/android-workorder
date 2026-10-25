package com.workorder.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.workorder.app.data.model.WorkDay
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkDayDao {
    @Query("SELECT * FROM work_days WHERE date = :date")
    fun observeByDate(date: String): Flow<WorkDay?>

    @Query("SELECT * FROM work_days WHERE date = :date")
    suspend fun getByDate(date: String): WorkDay?

    @Query("SELECT COUNT(*) FROM work_days WHERE id = :id")
    suspend fun countById(id: Long): Int

    @Query("SELECT * FROM work_days WHERE date LIKE :yearMonth || '-%' ORDER BY date ASC")
    fun observeByMonth(yearMonth: String): Flow<List<WorkDay>>

    /** Накоплено выходами за отгул минус списано отгулами (целыми днями и на часть дня), за всё время. */
    @Query(
        """
        SELECT COALESCE(SUM(CASE dayType WHEN 'COMP_WORK' THEN totalHours WHEN 'COMP_OFF' THEN -totalHours ELSE 0 END)
            - SUM(compOffHours), 0)
        FROM work_days WHERE isRecorded = 1
        """
    )
    fun observeCompTimeBalance(): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(workDay: WorkDay): Long

    @Update
    suspend fun update(workDay: WorkDay)

    @Query("DELETE FROM work_days WHERE id = :id")
    suspend fun deleteById(id: Long)
}
