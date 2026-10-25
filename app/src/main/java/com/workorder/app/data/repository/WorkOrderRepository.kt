package com.workorder.app.data.repository

import androidx.room.withTransaction
import com.workorder.app.data.AppDatabase
import com.workorder.app.data.dao.EntryWithOperation
import com.workorder.app.data.dao.OperationTotal
import com.workorder.app.data.dao.OperationUsage
import com.workorder.app.data.dao.RecentOperation
import com.workorder.app.data.model.DEFAULT_MANUAL_MULTIPLIER
import com.workorder.app.data.model.MANUAL_MULTIPLIER_RANGE
import com.workorder.app.data.model.OperationEntry
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.data.model.allowsPartialCompOff
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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

    fun observeRecordedMonth(yearMonth: String): Flow<List<WorkDay>> =
        observeMonth(yearMonth).map { days -> days.filter { it.isRecorded } }

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

    fun observeDailyProducedHours(yearMonth: String) = entryDao.observeDailyProducedHours(yearMonth)

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

    fun observeRecentOperations(limit: Int = 50): Flow<List<RecentOperation>> =
        entryDao.observeRecentOperations(limit)

    suspend fun operationUsage(operationId: Long): OperationUsage = entryDao.usage(operationId)

    /** Удаляет запись и возвращает её, чтобы удаление можно было отменить. */
    suspend fun removeEntry(entryId: Long): OperationEntry? = db.withTransaction {
        entryDao.getById(entryId)?.also { entryDao.deleteById(entryId) }
    }

    /** Возвращает удалённую запись с прежними id и временем, если её наряд ещё существует. */
    suspend fun restoreEntry(entry: OperationEntry): Boolean = db.withTransaction {
        if (workDayDao.countById(entry.workDayId) == 0) return@withTransaction false
        entryDao.insert(entry) != -1L
    }

    /**
     * Отработанные часы дня. [compOffHours] — отгул на часть дня (null — не менять): например, отработано 4 ч
     * и ещё 4 ч взято отгулом. Отгул на часть дня возможен только у дней «По графику» и «Рабочий».
     */
    suspend fun setDayHours(date: String, hours: Double, compOffHours: Double? = null) {
        require(hours.isFinite() && hours >= 0) { "Некорректное количество часов" }
        require(compOffHours == null || (compOffHours.isFinite() && compOffHours >= 0)) { "Некорректные часы отгула" }
        require(hours + (compOffHours ?: 0.0) <= 24.0) { "В сутках не больше 24 часов" }
        db.withTransaction {
            val day = workDayDao.getByDate(date)
            if (day != null && compOffHours != null && compOffHours > 0) {
                require(day.dayType.allowsPartialCompOff) { "Отгул на часть дня — только для дней «По графику» и «Рабочий»" }
            }
            if (day == null) {
                workDayDao.insert(WorkDay(date = date, totalHours = hours, compOffHours = compOffHours ?: 0.0))
            } else if (day.dayType == WorkDayType.MANUAL) {
                // Ручной режим: двойные часы сохраняются, пока помещаются, остальное — одинарные.
                val double = minOf(day.manualDoubleHours, hours)
                workDayDao.update(
                    day.copy(totalHours = hours, isRecorded = true, manualSingleHours = hours - double, manualDoubleHours = double)
                )
            } else {
                workDayDao.update(day.copy(totalHours = hours, isRecorded = true, compOffHours = compOffHours ?: day.compOffHours))
            }
        }
    }

    /**
     * Ручной режим: часы по обычной и по повышенной ставке задаются явно и вместе отмечают смену.
     * Коэффициент повышенной ставки — свой, по умолчанию ×2.
     */
    suspend fun setManualHours(
        date: String,
        singleHours: Double,
        doubleHours: Double,
        multiplier: Double = DEFAULT_MANUAL_MULTIPLIER
    ) {
        require(singleHours.isFinite() && singleHours >= 0 && doubleHours.isFinite() && doubleHours >= 0) {
            "Некорректное количество часов"
        }
        require(singleHours + doubleHours <= 24.0) { "В сутках не больше 24 часов" }
        require(multiplier.isFinite() && multiplier in MANUAL_MULTIPLIER_RANGE) { "Коэффициент: от 1 до 10" }
        db.withTransaction {
            val manual = WorkDay(
                date = date,
                totalHours = singleHours + doubleHours,
                dayType = WorkDayType.MANUAL,
                manualSingleHours = singleHours,
                manualDoubleHours = doubleHours,
                manualMultiplier = multiplier
            )
            val day = workDayDao.getByDate(date)
            if (day == null) workDayDao.insert(manual)
            else workDayDao.update(manual.copy(id = day.id, comment = day.comment, specialization = day.specialization))
        }
    }

    /** Баланс отгулов в часах: начальный из настроек + выходы за отгул − взятые отгулы. */
    fun observeCompTimeBalance(): Flow<Double> =
        combine(workDayDao.observeCompTimeBalance(), settingsRepository.observe()) { balance, settings ->
            balance + settings.compTimeInitialHours
        }

    /**
     * Комментарий, тип дня и специализация на пустом дне создают только план:
     * без часов и без отметки смены, поэтому не попадают в отчёты и зарплату.
     */
    suspend fun setDayComment(date: String, comment: String) {
        val text = comment.trim()
        db.withTransaction {
            val day = workDayDao.getByDate(date)
            if (day == null) {
                if (text.isNotEmpty()) workDayDao.insert(plan(date).copy(comment = text))
            } else {
                saveOrDropPlan(day.copy(comment = text))
            }
        }
    }

    suspend fun setDaySpecialization(date: String, specialization: String) {
        require(specialization.length <= 120) { "Специализация: не более 120 символов" }
        val text = specialization.trim()
        db.withTransaction {
            val day = workDayDao.getByDate(date)
            if (day == null) {
                if (text.isNotEmpty()) workDayDao.insert(plan(date).copy(specialization = text))
            } else {
                saveOrDropPlan(day.copy(specialization = text))
            }
        }
    }

    /**
     * Отгул — оплачиваемый день без работы, поэтому он сразу отмечает смену со стандартными часами.
     * Остальные типы на пустом дне остаются планом. Ручной режим задаётся через [setManualHours].
     */
    suspend fun setDayType(date: String, dayType: WorkDayType) {
        require(dayType != WorkDayType.MANUAL) { "Ручной режим задаётся вместе с часами" }
        val defaultHours = defaultDayHours()
        db.withTransaction {
            val day = workDayDao.getByDate(date)
            if (day == null) {
                when {
                    dayType == WorkDayType.COMP_OFF ->
                        workDayDao.insert(WorkDay(date = date, totalHours = defaultHours, dayType = dayType))
                    dayType != WorkDayType.AUTO -> workDayDao.insert(plan(date).copy(dayType = dayType))
                    else -> Unit
                }
            } else {
                val changed = day.copy(
                    dayType = dayType,
                    manualSingleHours = 0.0,
                    manualDoubleHours = 0.0,
                    manualMultiplier = DEFAULT_MANUAL_MULTIPLIER,
                    compOffHours = if (dayType.allowsPartialCompOff) day.compOffHours else 0.0
                )
                saveOrDropPlan(
                    if (dayType == WorkDayType.COMP_OFF && !day.isRecorded) {
                        changed.copy(totalHours = defaultHours, isRecorded = true)
                    } else changed
                )
            }
        }
    }

    private fun plan(date: String) = WorkDay(date = date, totalHours = 0.0, isRecorded = false)

    /** План, в котором не осталось ни текста, ни особого типа дня, не нужен. */
    private suspend fun saveOrDropPlan(day: WorkDay) {
        val empty = !day.isRecorded && day.comment.isBlank() && day.specialization.isBlank() &&
            day.dayType == WorkDayType.AUTO
        if (empty) workDayDao.deleteById(day.id) else workDayDao.update(day)
    }

    /** Удаляет наряд вместе со всем журналом (каскадно). */
    suspend fun deleteDay(date: String) {
        db.withTransaction {
            workDayDao.getByDate(date)?.let { workDayDao.deleteById(it.id) }
        }
    }

    private suspend fun getOrCreateDayId(date: String, defaultHours: Double): Long {
        val existing = workDayDao.getByDate(date)
        if (existing != null) {
            if (!existing.isRecorded) {
                workDayDao.update(existing.copy(totalHours = defaultHours, isRecorded = true))
            }
            return existing.id
        }
        val insertedId = workDayDao.insert(WorkDay(date = date, totalHours = defaultHours))
        // OnConflictStrategy.IGNORE возвращает -1, если день успел появиться параллельно
        return if (insertedId != -1L) insertedId else checkNotNull(workDayDao.getByDate(date)).id
    }
}
