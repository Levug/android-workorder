package com.workorder.app.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workorder.app.WorkOrderApp
import com.workorder.app.data.dao.EntryWithOperation
import com.workorder.app.data.dao.OperationTotal
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.data.repository.OperationRepository
import com.workorder.app.data.repository.SettingsRepository
import com.workorder.app.data.repository.WorkOrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Событие «партия добавлена» — для снекбара с кнопкой отмены. */
data class BatchAddedEvent(
    val operationName: String,
    val quantity: Int,
    val entryId: Long
)

data class WorkDayUiState(
    val date: LocalDate,
    val isToday: Boolean = false,
    val dayExists: Boolean = false,
    val dayHours: Double = WorkOrderRepository.DEFAULT_DAY_HOURS,
    val comment: String = "",
    val dayType: WorkDayType = WorkDayType.AUTO,
    val totals: List<OperationTotal> = emptyList(),
    val entries: List<EntryWithOperation> = emptyList(),
    val totalUnits: Int = 0,
    val producedHours: Double = 0.0,
    val isLoading: Boolean = true
)

/**
 * Экран наряда за день. Дата берётся из аргумента навигации "date",
 * при его отсутствии (вкладка «Сегодня») — текущая дата.
 */
class WorkDayViewModel(
    private val workOrderRepository: WorkOrderRepository,
    operationRepository: OperationRepository,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val dateString: String =
        savedStateHandle.get<String>("date")?.takeIf { it.isNotBlank() }
            ?: LocalDate.now().toString()

    val date: LocalDate = LocalDate.parse(dateString)

    val uiState: StateFlow<WorkDayUiState> = combine(
        workOrderRepository.observeDay(dateString),
        workOrderRepository.observeDayTotals(dateString),
        workOrderRepository.observeDayEntries(dateString),
        settingsRepository.observe()
    ) { day, totals, entries, settings ->
        WorkDayUiState(
            date = date,
            isToday = date == LocalDate.now(),
            dayExists = day != null,
            dayHours = day?.totalHours ?: settings.defaultDayHours,
            comment = day?.comment.orEmpty(),
            dayType = day?.dayType ?: WorkDayType.AUTO,
            totals = totals,
            entries = entries,
            totalUnits = totals.sumOf { it.totalCount },
            producedHours = totals.sumOf { it.totalHours },
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkDayUiState(date = LocalDate.parse(dateString))
    )

    /** Каталог операций для выбора. */
    val operations: StateFlow<List<Operation>> = operationRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _batchAdded = MutableStateFlow<BatchAddedEvent?>(null)
    val batchAdded: StateFlow<BatchAddedEvent?> = _batchAdded.asStateFlow()

    /** Добавить партию: quantity может быть и отрицательным (корректировка). */
    fun addBatch(operationId: Long, quantity: Int) {
        if (quantity == 0) return
        viewModelScope.launch {
            val entryId = workOrderRepository.addEntry(dateString, operationId, quantity)
            val name = operations.value.find { it.id == operationId }?.name ?: ""
            _batchAdded.value = BatchAddedEvent(name, quantity, entryId)
        }
    }

    /** Отмена только что добавленной партии (из снекбара). */
    fun undoBatch(entryId: Long) {
        viewModelScope.launch { workOrderRepository.removeEntry(entryId) }
    }

    fun consumeBatchAddedEvent() {
        _batchAdded.value = null
    }

    fun removeEntry(entryId: Long) {
        viewModelScope.launch { workOrderRepository.removeEntry(entryId) }
    }

    fun setHours(hours: Double) {
        viewModelScope.launch { workOrderRepository.setDayHours(dateString, hours) }
    }

    fun setComment(comment: String) {
        viewModelScope.launch { workOrderRepository.setDayComment(dateString, comment) }
    }

    fun setDayType(type: WorkDayType) {
        viewModelScope.launch { workOrderRepository.setDayType(dateString, type) }
    }

    fun deleteDay(onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            workOrderRepository.deleteDay(dateString)
            onDeleted()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkOrderApp
                WorkDayViewModel(
                    workOrderRepository = app.container.workOrderRepository,
                    operationRepository = app.container.operationRepository,
                    settingsRepository = app.container.settingsRepository,
                    savedStateHandle = createSavedStateHandle()
                )
            }
        }
    }
}
