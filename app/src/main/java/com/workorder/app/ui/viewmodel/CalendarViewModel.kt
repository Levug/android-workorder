package com.workorder.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workorder.app.WorkOrderApp
import com.workorder.app.data.model.WorkDay
import com.workorder.app.data.model.Settings
import com.workorder.app.data.repository.WorkOrderRepository
import com.workorder.app.data.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class CalendarUiState(
    val month: YearMonth,
    val days: Map<LocalDate, WorkDay> = emptyMap(),
    val workedDays: Int = 0,
    val workedHours: Double = 0.0,
    val settings: Settings = Settings()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    workOrderRepository: WorkOrderRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    private val month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<CalendarUiState> = month.flatMapLatest { m ->
        combine(
            workOrderRepository.observeMonth(m.format(monthFormatter)),
            settingsRepository.observe()
        ) { days, settings ->
            CalendarUiState(
                month = m,
                days = days.associateBy { LocalDate.parse(it.date) },
                workedDays = days.size,
                workedHours = days.sumOf { it.totalHours },
                settings = settings
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CalendarUiState(month = YearMonth.now())
    )

    fun previousMonth() { month.value = month.value.minusMonths(1) }

    fun nextMonth() { month.value = month.value.plusMonths(1) }

    fun goToCurrentMonth() { month.value = YearMonth.now() }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkOrderApp
                CalendarViewModel(
                    app.container.workOrderRepository,
                    app.container.settingsRepository
                )
            }
        }
    }
}
