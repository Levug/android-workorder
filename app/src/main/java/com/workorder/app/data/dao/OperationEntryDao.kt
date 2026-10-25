package com.workorder.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.workorder.app.data.model.OperationEntry
import kotlinx.coroutines.flow.Flow

/** Итог по операции: суммарное количество за период. */
data class OperationTotal(
    val operationId: Long,
    val name: String,
    val durationHours: Double,
    val grade: Int,
    val sortOrder: Int,
    val totalCount: Int
) {
    val totalHours: Double get() = durationHours * totalCount
}

/** Запись журнала вместе с данными операции. */
data class EntryWithOperation(
    val id: Long,
    val quantity: Int,
    val createdAt: Long,
    val operationId: Long,
    val operationName: String,
    val durationHours: Double
) {
    val hours: Double get() = durationHours * quantity
}

data class DailyProducedHours(val date: String, val hours: Double)

/** Недавно использованная операция и размер её последней партии. */
data class RecentOperation(val operationId: Long, val lastQuantity: Int, val lastUsedAt: Long)

/** Сколько записей журнала и нарядов затронет удаление операции. */
data class OperationUsage(val entries: Int, val days: Int)

@Dao
interface OperationEntryDao {

    // SQLite берёт quantity из той же строки, где достигается MAX(createdAt).
    @Query(
        """
        SELECT oe.operationId AS operationId, oe.quantity AS lastQuantity, MAX(oe.createdAt) AS lastUsedAt
        FROM operation_entries oe
        INNER JOIN operations op ON op.id = oe.operationId
        WHERE oe.quantity > 0 AND op.archived = 0
        GROUP BY oe.operationId
        ORDER BY lastUsedAt DESC
        LIMIT :limit
        """
    )
    fun observeRecentOperations(limit: Int): Flow<List<RecentOperation>>

    @Query("SELECT COUNT(*) AS entries, COUNT(DISTINCT workDayId) AS days FROM operation_entries WHERE operationId = :operationId")
    suspend fun usage(operationId: Long): OperationUsage

    @Query("SELECT * FROM operation_entries WHERE id = :id")
    suspend fun getById(id: Long): OperationEntry?

    @Query("""
        SELECT wd.date AS date, COALESCE(SUM(oe.quantity * op.durationHours), 0.0) AS hours
        FROM work_days wd
        LEFT JOIN operation_entries oe ON oe.workDayId = wd.id
        LEFT JOIN operations op ON op.id = oe.operationId
        WHERE wd.date LIKE :yearMonth || '-%' AND wd.isRecorded = 1
        GROUP BY wd.date ORDER BY wd.date
    """)
    fun observeDailyProducedHours(yearMonth: String): Flow<List<DailyProducedHours>>

    @Query(
        """
        SELECT oe.id AS id, oe.quantity AS quantity, oe.createdAt AS createdAt,
               op.id AS operationId, op.name AS operationName, op.durationHours AS durationHours
        FROM operation_entries oe
        INNER JOIN operations op ON op.id = oe.operationId
        WHERE oe.workDayId = :workDayId
        ORDER BY oe.createdAt DESC, oe.id DESC
        """
    )
    fun observeEntriesForDay(workDayId: Long): Flow<List<EntryWithOperation>>

    @Query(
        """
        SELECT op.id AS operationId, op.name AS name, op.durationHours AS durationHours,
               op.grade AS grade, op.sortOrder AS sortOrder,
               SUM(oe.quantity) AS totalCount
        FROM operation_entries oe
        INNER JOIN operations op ON op.id = oe.operationId
        WHERE oe.workDayId = :workDayId
        GROUP BY op.id
        HAVING SUM(oe.quantity) <> 0
        ORDER BY op.sortOrder ASC, op.name COLLATE NOCASE ASC
        """
    )
    fun observeDayTotals(workDayId: Long): Flow<List<OperationTotal>>

    @Query(
        """
        SELECT op.id AS operationId, op.name AS name, op.durationHours AS durationHours,
               op.grade AS grade, op.sortOrder AS sortOrder,
               SUM(oe.quantity) AS totalCount
        FROM operation_entries oe
        INNER JOIN operations op ON op.id = oe.operationId
        INNER JOIN work_days wd ON wd.id = oe.workDayId
        WHERE wd.date LIKE :yearMonth || '-%' AND wd.isRecorded = 1
        GROUP BY op.id
        HAVING SUM(oe.quantity) <> 0
        ORDER BY op.sortOrder ASC, op.name COLLATE NOCASE ASC
        """
    )
    fun observeMonthTotals(yearMonth: String): Flow<List<OperationTotal>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: OperationEntry): Long

    @Query("SELECT id FROM operation_entries WHERE sourceEventId = :sourceEventId LIMIT 1")
    suspend fun getIdBySourceEventId(sourceEventId: String): Long?

    @Query("DELETE FROM operation_entries WHERE id = :entryId")
    suspend fun deleteById(entryId: Long)
}
