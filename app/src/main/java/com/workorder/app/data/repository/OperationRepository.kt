package com.workorder.app.data.repository

import com.workorder.app.data.dao.OperationDao
import com.workorder.app.data.model.Operation
import kotlinx.coroutines.flow.Flow

class OperationRepository(private val operationDao: OperationDao) {

    fun observeAll(): Flow<List<Operation>> = operationDao.observeAll()

    suspend fun getById(id: Long): Operation? = operationDao.getById(id)

    suspend fun getByName(name: String): Operation? = operationDao.getByName(name.trim())

    suspend fun save(operation: Operation): Long {
        return if (operation.id == 0L) {
            operationDao.insert(operation.copy(sortOrder = operationDao.nextSortOrder()))
        } else {
            val current = operationDao.getById(operation.id)
            operationDao.update(operation.copy(sortOrder = current?.sortOrder ?: operation.sortOrder))
            operation.id
        }
    }

    suspend fun move(operationId: Long, direction: Int) {
        val ordered = operationDao.getAllOrdered().toMutableList()
        val from = ordered.indexOfFirst { it.id == operationId }
        val to = from + direction
        if (from !in ordered.indices || to !in ordered.indices) return
        val item = ordered.removeAt(from)
        ordered.add(to, item)
        operationDao.updateAll(ordered.mapIndexed { index, operation -> operation.copy(sortOrder = index) })
    }

    suspend fun applyPreferredOrder(operationIds: List<Long>) {
        val priority = operationIds.withIndex().associate { it.value to it.index }
        val ordered = operationDao.getAllOrdered().sortedWith(
            compareBy<Operation> { priority[it.id] ?: Int.MAX_VALUE }
                .thenBy { it.sortOrder }
        )
        operationDao.updateAll(ordered.mapIndexed { index, operation -> operation.copy(sortOrder = index) })
    }

    suspend fun delete(operation: Operation) = operationDao.delete(operation)

}
