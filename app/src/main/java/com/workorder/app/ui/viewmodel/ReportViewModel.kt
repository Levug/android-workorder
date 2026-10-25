package com.workorder.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.workorder.app.WorkOrderApp
import com.workorder.app.data.dao.OperationTotal
import com.workorder.app.data.model.BankStrategy
import com.workorder.app.data.model.fundHours
import com.workorder.app.data.model.isInHourFund
import com.workorder.app.data.repository.BankRepository
import com.workorder.app.data.repository.BankState
import com.workorder.app.data.repository.SettingsRepository
import com.workorder.app.data.repository.WorkOrderRepository
import com.workorder.app.domain.SalaryBreakdown
import com.workorder.app.domain.SalaryCalculator
import com.workorder.app.util.ReportExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    /** Норма месяца, по которой считается зарплата. */
    val normHours: Double = 0.0,
    /** Норма не задана вручную и посчитана по графику. */
    val normIsAuto: Boolean = true,
    val scheduleNormHours: Double = 0.0,
    val salary: SalaryBreakdown = SalaryBreakdown(),
    val forecastSalary: SalaryBreakdown = SalaryBreakdown(),
    val totals: List<OperationTotal> = emptyList(),
    val dailyNormHours: List<Pair<Int, Double>> = emptyList(),
    val bank: BankState? = null,
    /** Эксперимент «Банк»: выключен — вкладки нет, отчёт по фактическим операциям. */
    val bankEnabled: Boolean = true,
    /** Баланс отгулов за всё время (не зависит от месяца), ч. */
    val compTimeBalance: Double = 0.0,
    val isLoading: Boolean = true
) {
    val displayMonth: String
        get() = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale("ru")))
            .replaceFirstChar { it.titlecase(Locale("ru")) }

    val rateMissing: Boolean get() = hourlyRate <= 0.0
    val salaryReady: Boolean get() = !rateMissing && normHours > 0.0
}

/** Подготовленный к сохранению или отправке файл отчёта. */
data class ReportFile(val file: File, val mimeType: String)

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(
    private val workOrderRepository: WorkOrderRepository,
    private val settingsRepository: SettingsRepository,
    private val bankRepository: BankRepository,
    private val reportExporter: ReportExporter,
    private val month: MutableStateFlow<YearMonth> = MutableStateFlow(YearMonth.now())
) : ViewModel() {
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

    val uiState: StateFlow<ReportUiState> = month.flatMapLatest { m ->
        val ym = m.format(monthFormatter)
        combine(
            workOrderRepository.observeMonth(ym),
            bankRepository.observe(ym),
            settingsRepository.observe(),
            workOrderRepository.observeDailyProducedHours(ym),
            workOrderRepository.observeCompTimeBalance()
        ) { allDays, bank, settings, producedByDay, compTimeBalance ->
            val days = allDays.filter { it.isRecorded }
            val workedHours = days.sumOf { it.fundHours }
            val bankOn = settings.bankEnabled
            val adjustedTotals = bank.lines.mapNotNull { line ->
                val count = if (bankOn) line.adjustedQuantity else line.rawQuantity
                if (count == 0) null else OperationTotal(
                    operationId = line.operation.id,
                    name = line.operation.name,
                    durationHours = line.operation.durationHours,
                    grade = line.operation.grade,
                    sortOrder = line.operation.sortOrder,
                    totalCount = count
                )
            }.sortedBy { it.sortOrder }
            val produced = if (bankOn) bank.adjustedHours else bank.rawHours
            val scheduleNorm = SalaryCalculator.scheduledNormHours(m, settings, allDays)
            val norm = bank.plan.plannedHours.takeIf { it > 0.0 } ?: scheduleNorm
            ReportUiState(
                month = m,
                workedDays = days.count { it.isInHourFund },
                workedHours = workedHours,
                rawProducedHours = bank.rawHours,
                producedHours = produced,
                efficiencyPercent = if (workedHours > 0) produced / workedHours * 100 else null,
                hourlyRate = settings.hourlyRate,
                contractHourlyRate = settings.contractHourlyRate,
                defaultDayHours = settings.defaultDayHours,
                normHours = norm,
                normIsAuto = bank.plan.plannedHours <= 0.0,
                scheduleNormHours = scheduleNorm,
                // Планы (праздники, выходы за отгул) уточняют график до 15-го для аванса; в оплату идут только смены.
                salary = SalaryCalculator.calculate(allDays, settings, norm),
                forecastSalary = SalaryCalculator.forecast(m, allDays, settings, norm),
                totals = adjustedTotals,
                dailyNormHours = producedByDay.map { LocalDate.parse(it.date).dayOfMonth to it.hours },
                bank = bank.takeIf { bankOn },
                bankEnabled = bankOn,
                compTimeBalance = compTimeBalance,
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReportUiState(month = month.value)
    )

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    fun consumeMessage() { _message.value = null }

    private val _exportFile = MutableStateFlow<ReportFile?>(null)
    val exportFile: StateFlow<ReportFile?> = _exportFile.asStateFlow()

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }
    fun goToMonth(target: YearMonth) { month.value = target }

    private val currentYearMonth get() = month.value.format(monthFormatter)

    /** 0 — считать норму по графику. */
    fun setNormHours(hours: Double) {
        if (!hours.isFinite() || hours < 0) return
        viewModelScope.launch { bankRepository.updatePlan(currentYearMonth) { it.copy(plannedHours = hours) } }
    }

    /** Стратегия сохраняется сразу: кнопка «Рассчитать» всегда использует выбранную на экране. */
    fun setBankStrategy(strategy: BankStrategy) {
        viewModelScope.launch { bankRepository.updatePlan(currentYearMonth) { it.copy(strategy = strategy) } }
    }

    fun saveBankParameters(coefficient: Double, grade3: Double, grade4: Double, grade5: Double, grade6: Double) {
        viewModelScope.launch {
            bankRepository.updatePlan(currentYearMonth) {
                it.copy(
                    coefficient = coefficient,
                    grade3Percent = grade3, grade4Percent = grade4,
                    grade5Percent = grade5, grade6Percent = grade6
                )
            }
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

    fun setManualAllocation(operationId: Long, banked: Int) {
        val state = uiState.value.bank ?: return
        viewModelScope.launch { bankRepository.setManualAllocation(state, operationId, banked) }
    }

    fun resetBankMonth() {
        viewModelScope.launch { bankRepository.resetMonth(currentYearMonth) }
    }

    fun exportPdf() = export("application/pdf") { state ->
        reportExporter.exportToPdf(
            yearMonth = state.month.format(monthFormatter),
            totalHours = state.workedHours,
            totalProjectHours = state.producedHours,
            salary = state.salary.fullSalary,
            totals = state.totals
        )
    }

    fun exportCsv() = export("text/csv") { state ->
        reportExporter.exportToCsv(
            yearMonth = state.month.format(monthFormatter),
            totalHours = state.workedHours,
            totalProjectHours = state.producedHours,
            salary = state.salary.fullSalary,
            totals = state.totals
        )
    }

    private fun export(mimeType: String, block: (ReportUiState) -> File?) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) { block(uiState.value) }
            if (file == null) _message.value = "Не удалось подготовить отчёт"
            else _exportFile.value = ReportFile(file, mimeType)
        }
    }

    fun saveExport(uri: Uri) {
        val prepared = _exportFile.value ?: return
        _exportFile.value = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { reportExporter.copyTo(prepared.file, uri) }
                _message.value = "Отчёт сохранён"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _message.value = "Не удалось сохранить отчёт"
            }
        }
    }

    fun dismissExport() { _exportFile.value = null }

    fun showMessage(text: String) { _message.value = text }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as WorkOrderApp
                ReportViewModel(
                    workOrderRepository = app.container.workOrderRepository,
                    settingsRepository = app.container.settingsRepository,
                    bankRepository = app.container.bankRepository,
                    reportExporter = app.container.reportExporter,
                    month = app.container.selectedMonth
                )
            }
        }
    }
}
