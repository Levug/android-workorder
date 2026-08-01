package com.workorder.app.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workorder.app.data.model.BankStrategy
import com.workorder.app.data.repository.BankState
import com.workorder.app.ui.component.*
import com.workorder.app.ui.viewmodel.ReportUiState
import com.workorder.app.ui.viewmodel.ReportViewModel
import com.workorder.app.util.formatHours
import com.workorder.app.util.formatNumber
import com.workorder.app.util.shareFile
import kotlinx.coroutines.launch
import java.io.File

private enum class ReportTab { SUMMARY, CHARTS, BANK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: ReportViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(ReportTab.SUMMARY) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun exported(file: File?, mime: String) {
        scope.launch {
            if (file == null) {
                snackbar.showSnackbar("Ошибка экспорта")
            } else if (snackbar.showSnackbar("Сохранено: ${file.name}", "Поделиться") == SnackbarResult.ActionPerformed) {
                shareFile(context, file, mime)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = {
                Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Предыдущий месяц")
                    }
                    Text(uiState.displayMonth, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий месяц")
                    }
                }
            })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                listOf("Сводка", "Графики", "Банк β").forEachIndexed { index, label ->
                    val tab = ReportTab.entries[index]
                    SegmentedButton(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index, 3)
                    ) { Text(label) }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (selectedTab) {
                ReportTab.SUMMARY -> SummaryReportTab(
                    uiState,
                    onExportPdf = { scope.launch { exported(viewModel.exportToPdf(), "application/pdf") } },
                    onExportCsv = { scope.launch { exported(viewModel.exportToCsv(), "text/csv") } }
                )
                ReportTab.CHARTS -> ChartsReportTab(uiState)
                ReportTab.BANK -> BankReportTab(
                    uiState = uiState,
                    onSavePlan = viewModel::saveBankPlan,
                    onApply = viewModel::applyAutomaticBank,
                    onReset = viewModel::resetBankMonth,
                    onManualChange = viewModel::changeManualAllocation
                )
            }
        }
    }
}

@Composable
private fun SummaryReportTab(uiState: ReportUiState, onExportPdf: () -> Unit, onExportCsv: () -> Unit) {
    val salaryReady = (uiState.bank?.plan?.plannedHours ?: 0.0) > 0.0
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (salaryReady) {
                        Text("Полная зарплата по заполненным дням", style = MaterialTheme.typography.labelLarge)
                        Text("${uiState.salary.fullSalary.formatNumber()} ₽", style = MaterialTheme.typography.headlineLarge)
                        HorizontalDivider()
                        SalaryRow("Аванс", uiState.salary.advance)
                        SalaryRow("Вторая часть", uiState.salary.remainingSalary)
                    } else {
                        Text("Расчёт зарплаты не настроен", style = MaterialTheme.typography.titleLarge)
                        Text("Укажите норму рабочих часов месяца во вкладке «Банк β»")
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Предпросмотр зарплаты за месяц", style = MaterialTheme.typography.titleMedium)
                    if (salaryReady) {
                        Text(
                            "${uiState.forecastSalary.fullSalary.formatNumber()} ₽",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        SalaryRow("Ожидаемый аванс", uiState.forecastSalary.advance)
                        SalaryRow("Ожидаемая вторая часть", uiState.forecastSalary.remainingSalary)
                        Text(
                            "Отмеченные переработки и выходы уже учтены. Остальные рабочие дни считаются по графику по ${uiState.defaultDayHours.formatNumber()} ч.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    } else {
                        Text("Для прогноза укажите месячную норму часов во вкладке «Банк β»")
                    }
                }
            }
        }
        item {
            SectionCard {
                Text("Расчёт по заполненным дням", style = MaterialTheme.typography.titleMedium)
                if (salaryReady) {
                    Text("Обычные: ${uiState.salary.regularHours.formatNumber()} ч × ${uiState.hourlyRate.formatNumber()} ₽")
                    Text("Выходные/праздники: ${uiState.salary.fixedDoubleHours.formatNumber()} ч × 2")
                    Text("Задержки ×1: ${uiState.salary.delaySingleHours.formatNumber()} ч")
                    Text("Задержки ×2: ${uiState.salary.delayDoubleHours.formatNumber()} ч")
                    Text(
                        "Аванс: ${uiState.salary.advanceHours.formatNumber()} ч × ${uiState.contractHourlyRate.formatNumber()} ₽",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Нужна месячная норма часов", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            SectionCard {
                Row(Modifier.fillMaxWidth()) {
                    StatCell(uiState.workedDays.toString(), "смен")
                    StatCell(uiState.workedHours.formatNumber(), "часов")
                    StatCell(uiState.producedHours.formatNumber(), "нормо-часов")
                    StatCell(uiState.efficiencyPercent?.let { "${it.formatNumber()}%" } ?: "–", "выработка")
                }
                uiState.bank?.let {
                    Text(
                        "Факт операций: ${it.rawHours.formatNumber()} нч · после банка: ${it.adjustedHours.formatNumber()} нч",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onExportPdf, Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Description, null); Spacer(Modifier.width(6.dp)); Text("PDF")
                }
                OutlinedButton(onExportCsv, Modifier.weight(1f)) {
                    Icon(Icons.Outlined.TableChart, null); Spacer(Modifier.width(6.dp)); Text("CSV")
                }
            }
        }
        item { Text("Операции после банка", style = MaterialTheme.typography.titleMedium) }
        if (uiState.totals.isEmpty()) {
            item { EmptyState(Icons.Outlined.Insights, "Нет операций", "Добавьте операции или настройте банк") }
        } else {
            item {
                SectionCard {
                    uiState.totals.forEach { total ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(total.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${total.grade} разряд · ${total.durationHours.formatNumber()} нч × ${total.totalCount} = ${total.totalHours.formatNumber()} нч",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("${total.totalCount} шт", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalaryRow(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text("${amount.formatNumber()} ₽", style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankReportTab(
    uiState: ReportUiState,
    onSavePlan: (Double, Double, BankStrategy, Double, Double, Double, Double) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onManualChange: (Long, Int) -> Unit
) {
    val bank = uiState.bank ?: return
    var normText by remember(bank.plan.plannedHours) { mutableStateOf(bank.plan.plannedHours.toString().removeSuffix(".0")) }
    var coefficientText by remember(bank.plan.coefficient) { mutableStateOf(bank.plan.coefficient.toString()) }
    var strategy by remember(bank.plan.strategy) { mutableStateOf(bank.plan.strategy) }
    var grade3Text by remember(bank.plan.grade3Percent) { mutableStateOf(bank.plan.grade3Percent.toString().removeSuffix(".0")) }
    var grade4Text by remember(bank.plan.grade4Percent) { mutableStateOf(bank.plan.grade4Percent.toString().removeSuffix(".0")) }
    var grade5Text by remember(bank.plan.grade5Percent) { mutableStateOf(bank.plan.grade5Percent.toString().removeSuffix(".0")) }
    var grade6Text by remember(bank.plan.grade6Percent) { mutableStateOf(bank.plan.grade6Percent.toString().removeSuffix(".0")) }
    val norm = normText.replace(',', '.').toDoubleOrNull()
    val coefficient = coefficientText.replace(',', '.').toDoubleOrNull()
    val gradeValues = listOf(grade3Text, grade4Text, grade5Text, grade6Text)
        .map { it.replace(',', '.').toDoubleOrNull() }
    val gradeSum = gradeValues.filterNotNull().sum()
    val gradesValid = gradeValues.all { it != null && it >= 0 } && kotlin.math.abs(gradeSum - 100.0) < 0.01

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccountBalance, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Экспериментальный банк", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Наряды не изменяются. Здесь хранится только помесячное расчётное распределение.")
                }
            }
        }
        item {
            SectionCard {
                OutlinedTextField(
                    value = normText,
                    onValueChange = { normText = it },
                    label = { Text("Норма рабочих часов в месяце") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = coefficientText,
                    onValueChange = { coefficientText = it },
                    label = { Text("Коэффициент лимита") },
                    supportingText = { Text("Лимит = фактические часы присутствия × коэффициент") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                BankStrategy.entries.chunked(2).forEach { options ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        options.forEach { value ->
                            FilterChip(
                                selected = strategy == value,
                                onClick = { strategy = value },
                                label = { Text(when (value) {
                                    BankStrategy.PROPORTIONAL -> "Пропорционально"
                                    BankStrategy.PRIORITY -> "Приоритет"
                                    BankStrategy.GRADE_RATIO -> "По разрядам"
                                    BankStrategy.MANUAL -> "Вручную"
                                }) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (strategy == BankStrategy.GRADE_RATIO) {
                    Text(
                        "Желаемая доля нормо-часов каждого разряда в переводе. Сумма должна быть 100%.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradePercentField(3, grade3Text, { grade3Text = it }, Modifier.weight(1f))
                        GradePercentField(4, grade4Text, { grade4Text = it }, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradePercentField(5, grade5Text, { grade5Text = it }, Modifier.weight(1f))
                        GradePercentField(6, grade6Text, { grade6Text = it }, Modifier.weight(1f))
                    }
                    Text(
                        "Сумма: ${gradeSum.formatNumber()}%",
                        color = if (gradesValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Button(
                    onClick = {
                        if (norm != null && coefficient != null && gradeValues.all { it != null }) {
                            onSavePlan(
                                norm, coefficient, strategy,
                                gradeValues[0]!!, gradeValues[1]!!, gradeValues[2]!!, gradeValues[3]!!
                            )
                        }
                    },
                    enabled = norm != null && norm >= 0 && coefficient != null && coefficient >= 0 &&
                        (strategy != BankStrategy.GRADE_RATIO || gradesValid),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить параметры") }
            }
        }
        item {
            SectionCard {
                Text("Лимит: ${bank.targetHours.formatNumber()} нч", style = MaterialTheme.typography.titleMedium)
                Text("Основа лимита: ${bank.attendanceHours.formatNumber()} ч присутствия × ${bank.plan.coefficient.formatNumber()}")
                Text("Фактически выполнено: ${bank.rawHours.formatNumber()} нч")
                Text("В отчёте: ${bank.adjustedHours.formatNumber()} нч")
                Text("Банк до месяца: ${bank.bankHoursBefore.formatNumber()} нч")
                Text("Банк после месяца: ${bank.bankHoursAfter.formatNumber()} нч")
                if (strategy != BankStrategy.MANUAL) {
                    Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Рассчитать и применить") }
                }
                TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Сбросить распределение месяца") }
            }
        }
        item { Text("Распределение операций", style = MaterialTheme.typography.titleMedium) }
        bank.lines.forEach { line ->
            item(key = line.operation.id) {
                SectionCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(line.operation.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${line.operation.grade} разряд · факт ${line.rawQuantity} · отчёт ${line.adjustedQuantity} · банк ${line.balanceAfter}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (strategy == BankStrategy.MANUAL) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { onManualChange(line.operation.id, -1) },
                                enabled = line.balanceAfter > 0,
                                modifier = Modifier.weight(1f)
                            ) { Text("+1 в наряд") }
                            OutlinedButton(
                                onClick = { onManualChange(line.operation.id, 1) },
                                enabled = line.adjustedQuantity > 0,
                                modifier = Modifier.weight(1f)
                            ) { Text("+1 в банк") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradePercentField(
    grade: Int,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("$grade разряд, %") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun ChartsReportTab(uiState: ReportUiState) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SectionCard { Text("Часы по дням", style = MaterialTheme.typography.titleMedium); MonthColumnChart(uiState.dailyHours, uiState.month.lengthOfMonth()) } }
        if (uiState.totals.isNotEmpty()) {
            item {
                val byGrade = uiState.totals
                    .groupBy { it.grade }
                    .mapValues { (_, values) -> values.sumOf { it.totalHours } }
                    .toSortedMap()
                SectionCard {
                    Text("Соотношение разрядов", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "По нормо-часам в отчёте после банка",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    DonutChart(
                        data = byGrade.map { (grade, hours) -> "$grade разряд" to hours },
                        centerTitle = uiState.producedHours.formatNumber(),
                        centerSubtitle = "нормо-часов"
                    )
                }
            }
            item {
                SectionCard {
                    Text("Нормо-часы после банка", style = MaterialTheme.typography.titleMedium)
                    HorizontalBarChart(uiState.totals.map { Triple(it.name, it.totalHours, it.totalHours.formatHours()) })
                }
            }
            item {
                SectionCard {
                    Text("Доля операций", style = MaterialTheme.typography.titleMedium)
                    DonutChart(uiState.totals.map { it.name to it.totalHours }, uiState.producedHours.formatNumber(), "нормо-часов")
                }
            }
        }
    }
}
