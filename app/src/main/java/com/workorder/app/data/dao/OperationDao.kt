package com.workorder.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.workorder.app.data.model.Operation
import kotlinx.coroutines.flow.Flow

@Dao
interface OperationDao {
    @Query("SELECT * FROM operations ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Operation>>

    @Query("SELECT * FROM operations ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun getAllOrdered(): List<Operation>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM operations")
    suspend fun nextSortOrder(): Int

    @Query("SELECT * FROM operations WHERE id = :id")
    suspend fun getById(id: Long): Operation?

    @Query("SELECT * FROM operations WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): Operation?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(operation: Operation): Long

    @Update
    suspend fun update(operation: Operation)

    @Update
    suspend fun updateAll(operations: List<Operation>)

    @Delete
    suspend fun delete(operation: Operation)

}
