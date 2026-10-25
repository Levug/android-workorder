package com.workorder.app.ui.viewmodel

import android.content.SharedPreferences
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
import com.workorder.app.data.dao.RecentOperation
import com.workorder.app.data.model.DEFAULT_MANUAL_MULTIPLIER
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.OperationEntry
import com.workorder.app.data.model.Settings
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.data.model.quickAmountList
import com.workorder.app.data.repository.OperationRepository
import com.workorder.app.data.repository.SettingsRepository
import com.workorder.app.data.repository.WorkOrderRepository
import com.workorder.app.domain.SalaryCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Событие «партия добавлена» — для подтверждения с кнопкой отмены. */
data class BatchAddedEvent(
    val operationId: Long,
    val operationName: String,
    val quantity: Int,
    val entryId: Long
)

/** Событие «запись журнала удалена» — для снекбара «Вернуть». */
data class EntryRemovedEvent(
    val operationName: String,
    val entry: OperationEntry
)

/** Операция и размер партии для добавления в одно касание. */
data class QuickAction(val operation: Operation, val quantity: Int)

/** Первичная настройка: показывается на вкладке «Сегодня», пока не пройдена или не скрыта. */
data class SetupState(val rateDone: Boolean, val operationsDone: Boolean)

data class WorkDayUiState(
    val date: LocalDate,
    val isToday: Boolean = false,
    val dayExists: Boolean = false,
    val isRecorded: Boolean = false,
    val dayHours: Double = WorkOrderRepository.DEFAULT_DAY_HOURS,
    val defaultDayHours: Double = WorkOrderRepository.DEFAULT_DAY_HOURS,
    val comment: String = "",
    val specialization: String = "",
    val specializationEnabled: Boolean = false,
    val dayType: WorkDayType = WorkDayType.AUTO,
    /** Ручной режим: часы по одинарной и двойной ставке. */
    val manualSingleHours: Double = 0.0,
    val manualDoubleHours: Double = 0.0,
    val manualMultiplier: Double = DEFAULT_MANUAL_MULTIPLIER,
    /** Отгул на часть дня, ч. */
    val compOffHours: Double = 0.0,
    /** Эксперименты: свайп между днями и ряд «Быстро добавить». */
    val daySwipeEnabled: Boolean = true,
    val quickAddEnabled: Boolean = true,
    /** Баланс отгулов за всё время, ч. */
    val compTimeBalance: Double = 0.0,
    /** Кнопка «+N» у операций сводки (появляется после касания по операции). */
    val summaryQuickAddEnabled: Boolean = true,
    /** Выходной по графику (без учёта ручного типа дня). */
    val scheduledOff: Boolean = false,
    val totals: List<OperationTotal> = emptyList(),
    val entries: List<EntryWithOperation> = emptyList(),
    val totalUnits: Int = 0,
    val producedHours: Double = 0.0,
    val quickAmounts: List<Int> = Settings().quickAmountList,
    /** Последний размер партии для каждой операции. */
    val lastQuantities: Map<Long, Int> = emptyMap(),
    /** Недавние операции: первая — «повторить последнее». */
    val recent: List<QuickAction> = emptyList(),
    val setup: SetupState? = null,
    /** Подсказку «удерживайте для выбора количества» уже видели — больше не показываем. */
    val quickHintSeen: Boolean = true,
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
    savedStateHandle: SavedStateHandle,
    private val preferences: SharedPreferences? = null
) : ViewModel() {

    private val dateString: String =
        savedStateHandle.get<String>("date")?.takeIf { it.isNotBlank() }
            ?: LocalDate.now().toString()

    val date: LocalDate = LocalDate.parse(dateString)

    /** Каталог операций для выбора: без архивных. */
    val operations: StateFlow<List<Operation>> = operationRepository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private data class UiFlags(val setupHidden: Boolean, val quickHintSeen: Boolean)

    private val flags = MutableStateFlow(
        UiFlags(
            setupHidden = preferences?.getBoolean(KEY_SETUP_HIDDEN, false) ?: true,
            quickHintSeen = preferences?.getBoolean(KEY_QUICK_HINT_SEEN, false) ?: true
        )
    )

    private data class DayData(
        val day: WorkDay?,
        val totals: List<OperationTotal>,
        val entries: List<EntryWithOperation>
    )

    private data class Context(
        val settings: Settings,
        val operations: List<Operation>,
        val recent: List<RecentOperation>,
        val flags: UiFlags,
        val compTimeBalance: Double
    )

    val uiState: StateFlow<WorkDayUiState> = combine(
        combine(
            workOrderRepository.observeDay(dateString),
            workOrderRepository.observeDayTotals(dateString),
            workOrderRepository.observeDayEntries(dateString),
            ::DayData
        ),
        combine(
            settingsRepository.observe(),
            operations,
            workOrderRepository.observeRecentOperations(),
            flags,
            workOrderRepository.observeCompTimeBalance(),
            ::Context
        )
    ) { data, context ->
        val day = data.day
        val settings = context.settings
        val byId = context.operations.associateBy { it.id }
        val recent = context.recent.mapNotNull { r -> byId[r.operationId]?.let { QuickAction(it, r.lastQuantity) } }
        val isToday = date == LocalDate.now()
        WorkDayUiState(
            date = date,
            isToday = isToday,
            dayExists = day != null,
            isRecorded = day?.isRecorded == true,
            dayHours = day?.totalHours ?: settings.defaultDayHours,
            defaultDayHours = settings.defaultDayHours,
            comment = day?.comment.orEmpty(),
            specialization = day?.specialization.orEmpty(),
            specializationEnabled = settings.daySpecializationEnabled,
            dayType = day?.dayType ?: WorkDayType.AUTO,
            manualSingleHours = day?.manualSingleHours ?: 0.0,
            manualDoubleHours = day?.manualDoubleHours ?: 0.0,
            manualMultiplier = day?.manualMultiplier ?: DEFAULT_MANUAL_MULTIPLIER,
            compOffHours = day?.compOffHours ?: 0.0,
            daySwipeEnabled = settings.daySwipeEnabled,
            quickAddEnabled = settings.quickAddEnabled,
            compTimeBalance = context.compTimeBalance,
            summaryQuickAddEnabled = settings.summaryQuickAddEnabled,
            scheduledOff = !SalaryCalculator.isScheduledWorkDay(date, settings),
            totals = data.totals,
            entries = data.entries,
            totalUnits = data.totals.sumOf { it.totalCount },
            producedHours = data.totals.sumOf { it.totalHours },
            quickAmounts = settings.quickAmountList,
            lastQuantities = context.recent.associate { it.operationId to it.lastQuantity },
            recent = recent,
            setup = SetupState(
                rateDone = settings.hourlyRate > 0,
                operationsDone = context.operations.isNotEmpty()
            ).takeIf { isToday && !context.flags.setupHidden && !(it.rateDone && it.operationsDone) },
            quickHintSeen = context.flags.quickHintSeen,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        // Заголовок «Сегодня» известен сразу, до первых данных из базы.
        initialValue = WorkDayUiState(date = date, isToday = date == LocalDate.now())
    )

    private val _batchAdded = MutableStateFlow<BatchAddedEvent?>(null)
    val batchAdded: StateFlow<BatchAddedEvent?> = _batchAdded.asStateFlow()

    private val _entryRemoved = MutableStateFlow<EntryRemovedEvent?>(null)
    val entryRemoved: StateFlow<EntryRemovedEvent?> = _entryRemoved.asStateFlow()

    /** Добавить партию: quantity может быть и отрицательным (корректировка). */
    fun addBatch(operationId: Long, quantity: Int) {
        if (quantity == 0) return
        viewModelScope.launch {
            val entryId = workOrderRepository.addEntry(dateString, operationId, quantity)
            val name = operations.value.find { it.id == operationId }?.name ?: ""
            _batchAdded.value = BatchAddedEvent(operationId, name, quantity, entryId)
        }
    }

    /** Отмена только что добавленной партии. */
    fun undoBatch(entryId: Long) {
        viewModelScope.launch { workOrderRepository.removeEntry(entryId) }
    }

    fun consumeBatchAddedEvent() {
        _batchAdded.value = null
    }

    fun removeEntry(entry: EntryWithOperation) {
        viewModelScope.launch {
            workOrderRepository.removeEntry(entry.id)?.let {
                _entryRemoved.value = EntryRemovedEvent(entry.operationName, it)
            }
        }
    }

    fun restoreEntry(event: EntryRemovedEvent) {
        viewModelScope.launch {
            if (!workOrderRepository.restoreEntry(event.entry)) {
                _error.value = "Не удалось вернуть запись: наряд уже удалён."
            }
        }
    }

    fun consumeEntryRemovedEvent() {
        _entryRemoved.value = null
    }

    /** [compOffHours] — отгул на часть дня; null — не менять. */
    fun setHours(hours: Double, compOffHours: Double? = null) {
        viewModelScope.launch {
            try {
                workOrderRepository.setDayHours(dateString, hours, compOffHours)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _error.value = e.message ?: "Некорректное количество часов"
            }
        }
    }

    fun setComment(comment: String) {
        viewModelScope.launch { workOrderRepository.setDayComment(dateString, comment) }
    }

    fun setSpecialization(text: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workOrderRepository.setDaySpecialization(dateString, text)
                onSaved()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Не удалось сохранить специализацию. Попробуйте ещё раз."
            }
        }
    }

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun consumeError() { _error.value = null }

    fun setDayType(type: WorkDayType) {
        viewModelScope.launch { workOrderRepository.setDayType(dateString, type) }
    }

    fun setManualHours(singleHours: Double, doubleHours: Double, multiplier: Double) {
        viewModelScope.launch {
            try {
                workOrderRepository.setManualHours(dateString, singleHours, doubleHours, multiplier)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _error.value = e.message ?: "Некорректное количество часов"
            }
        }
    }

    fun deleteDay(onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            workOrderRepository.deleteDay(dateString)
            onDeleted()
        }
    }

    fun hideSetup() {
        preferences?.edit()?.putBoolean(KEY_SETUP_HIDDEN, true)?.apply()
        flags.value = flags.value.copy(setupHidden = true)
    }

    /** Первое долгое нажатие на быструю кнопку — подсказка больше не нужна. */
    fun markQuickHintSeen() {
        if (flags.value.quickHintSeen) return
        preferences?.edit()?.putBoolean(KEY_QUICK_HINT_SEEN, true)?.apply()
        flags.value = flags.value.copy(quickHintSeen = true)
    }

    companion object {
        private const val KEY_SETUP_HIDDEN = "setupHidden"
        private const val KEY_QUICK_HINT_SEEN = "quickHintSeen"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkOrderApp
                WorkDayViewModel(
                    workOrderRepository = app.container.workOrderRepository,
                    operationRepository = app.container.operationRepository,
                    settingsRepository = app.container.settingsRepository,
                    savedStateHandle = createSavedStateHandle(),
                    preferences = app.container.uiPreferences
                )
            }
        }
    }
}
