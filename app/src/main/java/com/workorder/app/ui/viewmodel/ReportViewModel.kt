package com.workorder.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workorder.app.WorkOrderApp
import com.workorder.app.data.dao.OperationTotal
import com.workorder.app.data.model.BankStrategy
import com.workorder.app.data.repository.BankRepository
import com.workorder.app.data.repository.BankState
import com.workorder.app.data.repository.SettingsRepository
import com.workorder.app.data.repository.WorkOrderRepository
import com.workorder.app.domain.SalaryBreakdown
import com.workorder.app.domain.SalaryCalculator
import com.workorder.app.util.ReportExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReportUiState(
    val month: YearMonth,
    val workedDays: Int = 0,
    val workedHours: Double = 0.0,
    val rawProducedHours: Double = 0.0,
    val producedHours: Double = 0.0,
    val efficiencyPercent: Double? = null,
    val hourlyRate: Double = 0.0,
    val contractHourlyRate: Double = 0.0,
    val defaultDayHours: Double = 8.0,
    val salary: SalaryBreakdown = SalaryBreakdown(),
    val forecastSalary: SalaryBreakdown = SalaryBreakdown(),
    val totals: List<OperationTotal> = emptyList(),
    val dailyHours: List<Pair<Int, Double>> = emptyList(),
    val bank: BankState? = null,
    val isLoading: Boolean = true
) {
    val displayMonth: String
        get() = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru")))
            .replaceFirstChar { it.titlecase(Locale("ru")) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(
    private val workOrderRepository: WorkOrderRepository,
    private val settingsRepository: SettingsRepository,
    private val bankRepository: BankRepository,
    private val reportExporter: ReportExporter
) : ViewModel() {
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    private val month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<ReportUiState> = month.flatMapLatest { m ->
        val ym = m.format(monthFormatter)
        combine(
            workOrderRepository.observeMonth(ym),
            bankRepository.observe(ym),
            settingsRepository.observe()
        ) { days, bank, settings ->
            val workedHours = days.sumOf { it.totalHours }
            val adjustedTotals = bank.lines.mapNotNull { line ->
                if (line.adjustedQuantity == 0) null else OperationTotal(
                    operationId = line.operation.id,
                    name = line.operation.name,
                    durationHours = line.operation.durationHours,
                    grade = line.operation.grade,
                    sortOrder = line.operation.sortOrder,
                    totalCount = line.adjustedQuantity
                )
            }.sortedBy { it.sortOrder }
            val salary = SalaryCalculator.calculate(days, settings, bank.plan.plannedHours)
            val forecastSalary = SalaryCalculator.forecast(m, days, settings, bank.plan.plannedHours)
            ReportUiState(
                month = m,
                workedDays = days.size,
                workedHours = workedHours,
                rawProducedHours = bank.rawHours,
                producedHours = bank.adjustedHours,
                efficiencyPercent = if (workedHours > 0) bank.adjustedHours / workedHours * 100 else null,
                hourlyRate = settings.hourlyRate,
                contractHourlyRate = settings.contractHourlyRate,
                defaultDayHours = settings.defaultDayHours,
                salary = salary,
                forecastSalary = forecastSalary,
                totals = adjustedTotals,
                dailyHours = days.map { LocalDate.parse(it.date).dayOfMonth to it.totalHours },
                bank = bank,
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportUiState(month = YearMonth.now())
    )

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }

    fun saveBankPlan(
        plannedHours: Double,
        coefficient: Double,
        strategy: BankStrategy,
        grade3Percent: Double,
        grade4Percent: Double,
        grade5Percent: Double,
        grade6Percent: Double
    ) {
        val ym = month.value.format(monthFormatter)
        viewModelScope.launch {
            bankRepository.updatePlan(
                ym, plannedHours, coefficient, strategy,
                grade3Percent, grade4Percent, grade5Percent, grade6Percent
            )
        }
    }

    fun applyAutomaticBank() {
        uiState.value.bank?.let { state ->
            viewModelScope.launch { bankRepository.applyAutomatic(state) }
        }
    }

    fun changeManualAllocation(operationId: Long, delta: Int) {
        val state = uiState.value.bank ?: return
        val line = state.lines.firstOrNull { it.operation.id == operationId } ?: return
        viewModelScope.launch {
            bankRepository.setManualAllocation(state, operationId, line.bankedQuantity + delta)
        }
    }

    fun resetBankMonth() {
        viewModelScope.launch { bankRepository.resetMonth(month.value.format(monthFormatter)) }
    }

    suspend fun exportToPdf(): File? = withContext(Dispatchers.IO) {
        val state = uiState.value
        reportExporter.exportToPdf(
            yearMonth = state.month.format(monthFormatter),
            totalHours = state.workedHours,
            totalProjectHours = state.producedHours,
            salary = state.salary.fullSalary,
            totals = state.totals
        )
    }

    suspend fun exportToCsv(): File? = withContext(Dispatchers.IO) {
        val state = uiState.value
        reportExporter.exportToCsv(
            yearMonth = state.month.format(monthFormatter),
            totalHours = state.workedHours,
            totalProjectHours = state.producedHours,
            salary = state.salary.fullSalary,
            totals = state.totals
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkOrderApp
                ReportViewModel(
                    workOrderRepository = app.container.workOrderRepository,
                    settingsRepository = app.container.settingsRepository,
                    bankRepository = app.container.bankRepository,
                    reportExporter = app.container.reportExporter
                )
            }
        }
    }
}
