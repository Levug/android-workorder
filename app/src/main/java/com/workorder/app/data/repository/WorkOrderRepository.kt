package com.workorder.app.data.repository

import androidx.room.withTransaction
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.dao.EntryWithOperation
import com.workorder.app.data.dao.OperationTotal
import com.workorder.app.data.model.OperationEntry
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.WorkDayType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

/**
 * Единая точка доступа к нарядам: рабочие дни + журнал партий.
 * Все чтения — реактивные Flow из Room, все записи — в транзакциях.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkOrderRepository(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository
) {

    private val workDayDao = db.workDayDao()
    private val entryDao = db.operationEntryDao()

    companion object {
        const val DEFAULT_DAY_HOURS = 8.0
    }

    private suspend fun defaultDayHours(): Double =
        settingsRepository.observe().first().defaultDayHours

    fun observeDay(date: String): Flow<WorkDay?> = workDayDao.observeByDate(date)

    fun observeMonth(yearMonth: String): Flow<List<WorkDay>> = workDayDao.observeByMonth(yearMonth)

    fun observeDayTotals(date: String): Flow<List<OperationTotal>> =
        workDayDao.observeByDate(date).flatMapLatest { day ->
            if (day == null) flowOf(emptyList()) else entryDao.observeDayTotals(day.id)
        }

    fun observeDayEntries(date: String): Flow<List<EntryWithOperation>> =
        workDayDao.observeByDate(date).flatMapLatest { day ->
            if (day == null) flowOf(emptyList()) else entryDao.observeEntriesForDay(day.id)
        }

    fun observeMonthTotals(yearMonth: String): Flow<List<OperationTotal>> =
        entryDao.observeMonthTotals(yearMonth)

    /**
     * Добавляет партию операций в наряд. Если наряда на эту дату ещё нет,
     * он создаётся автоматически со стандартной продолжительностью дня.
     * @return id созданной записи журнала (для «отменить»), 0 если quantity == 0.
     */
    suspend fun addEntry(date: String, operationId: Long, quantity: Int): Long {
        if (quantity == 0) return 0
        val defaultHours = defaultDayHours()
        return db.withTransaction {
            val dayId = getOrCreateDayId(date, defaultHours)
            entryDao.insert(
                OperationEntry(
                    workDayId = dayId,
                    operationId = operationId,
                    quantity = quantity,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Добавляет событие, пришедшее с внешнего устройства, ровно один раз.
     * Повторная доставка того же sourceEventId возвращает id уже сохранённой записи.
     */
    suspend fun addSyncedEntry(
        date: String,
        operationId: Long,
        quantity: Int,
        createdAt: Long,
        sourceEventId: String
    ): Long {
        require(quantity != 0) { "quantity must not be zero" }
        require(sourceEventId.isNotBlank()) { "sourceEventId must not be blank" }
        val defaultHours = defaultDayHours()
        return db.withTransaction {
            entryDao.getIdBySourceEventId(sourceEventId)?.let { return@withTransaction it }
            val dayId = getOrCreateDayId(date, defaultHours)
            val inserted = entryDao.insert(
                OperationEntry(
                    workDayId = dayId,
                    operationId = operationId,
                    quantity = quantity,
                    createdAt = createdAt,
                    sourceEventId = sourceEventId
                )
            )
            if (inserted != -1L) inserted
            else checkNotNull(entryDao.getIdBySourceEventId(sourceEventId))
        }
    }

    suspend fun removeEntry(entryId: Long) = entryDao.deleteById(entryId)

    suspend fun setDayHours(date: String, hours: Double) {
        db.withTransaction {
            val day = workDayDao.getByDate(date)
            if (day == null) {
                workDayDao.insert(WorkDay(date = date, totalHours = hours))
            } else {
                workDayDao.update(day.copy(totalHours = hours))
            }
        }
    }

    suspend fun setDayComment(date: String, comment: String) {
        val hours = defaultDayHours()
        db.withTransaction {
            val day = workDayDao.getByDate(date)
            if (day == null) {
                workDayDao.insert(WorkDay(date = date, totalHours = hours, comment = comment))
            } else {
                workDayDao.update(day.copy(comment = comment))
            }
        }
    }

    suspend fun setDayType(date: String, dayType: WorkDayType) {
        val hours = defaultDayHours()
        db.withTransaction {
            val day = workDayDao.getByDate(date)
            if (day == null) {
                workDayDao.insert(WorkDay(date = date, totalHours = hours, dayType = dayType))
            } else {
                workDayDao.update(day.copy(dayType = dayType))
            }
        }
    }

    /** Удаляет наряд вместе со всем журналом (каскадно). */
    suspend fun deleteDay(date: String) {
        db.withTransaction {
            workDayDao.getByDate(date)?.let { workDayDao.deleteById(it.id) }
        }
    }

    private suspend fun getOrCreateDayId(date: String, defaultHours: Double): Long {
        val existing = workDayDao.getByDate(date)
        if (existing != null) return existing.id
        val insertedId = workDayDao.insert(WorkDay(date = date, totalHours = defaultHours))
        // OnConflictStrategy.IGNORE возвращает -1, если день успел появиться параллельно
        return if (insertedId != -1L) insertedId else checkNotNull(workDayDao.getByDate(date)).id
    }
}
