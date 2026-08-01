package com.workorder.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
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

@Dao
interface OperationEntryDao {

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
        WHERE wd.date LIKE :yearMonth || '-%'
        GROUP BY op.id
        HAVING SUM(oe.quantity) <> 0
        ORDER BY op.sortOrder ASC, op.name COLLATE NOCASE ASC
        """
    )
    fun observeMonthTotals(yearMonth: String): Flow<List<OperationTotal>>

    @Insert
    suspend fun insert(entry: OperationEntry): Long

    @Query("DELETE FROM operation_entries WHERE id = :entryId")
    suspend fun deleteById(entryId: Long)
}
