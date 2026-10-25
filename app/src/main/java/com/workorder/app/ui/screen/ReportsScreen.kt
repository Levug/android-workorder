@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.workorder.app.ui.screen

import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.FlowRow
import com.workorder.app.util.noOrphans
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workorder.app.data.model.BankStrategy
import com.workorder.app.data.repository.BankOperationLine
import com.workorder.app.ui.component.*
import com.workorder.app.ui.viewmodel.ReportUiState
import com.workorder.app.ui.viewmodel.ReportViewModel
import com.workorder.app.util.formatHours
import com.workorder.app.util.formatNumber
import com.workorder.app.util.formatShort
import java.time.LocalDate

private enum class ReportTab { SUMMARY, CHARTS, BANK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportViewModel,
    onOpenDay: (LocalDate) -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val exportFile by viewModel.exportFile.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(ReportTab.SUMMARY) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    var showNormDialog by rememberSaveable { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it, withDismissAction = true)
            viewModel.consumeMessage()
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
                    Row(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = "Выбрать месяц") { showMonthPicker = true }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(uiState.displayMonth, style = MaterialTheme.typography.titleMedium)
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Следующий месяц")
                    }
                }
            }, actions = {
                // Экспорт PDF/CSV — в меню, чтобы не занимать место в сводке.
                Box {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(Icons.Outlined.Share, contentDescription = "Поделиться отчётом")
                    }
                    DropdownMenu(expanded = showExportMenu, onDismissRequest = { showExportMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Отчёт PDF") },
                            leadingIcon = { Icon(Icons.Outlined.Description, null) },
                            onClick = { showExportMenu = false; viewModel.exportPdf() }
                        )
                        DropdownMenuItem(
                            text = { Text("Таблица CSV") },
                            leadingIcon = { Icon(Icons.Outlined.TableChart, null) },
                            onClick = { showExportMenu = false; viewModel.exportCsv() }
                        )
                    }
                }
            })
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Банк можно выключить в экспериментах — тогда его вкладки нет.
            val tabs = if (uiState.bankEnabled) ReportTab.entries else listOf(ReportTab.SUMMARY, ReportTab.CHARTS)
            val currentTab = selectedTab.takeIf { it in tabs } ?: ReportTab.SUMMARY
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                tabs.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = currentTab == tab,
                        onClick = { selectedTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index, tabs.size)
                    ) {
                        Text(
                            when (tab) {
                                ReportTab.SUMMARY -> "Сводка"
                                ReportTab.CHARTS -> "Графики"
                                ReportTab.BANK -> "Банк β"
                            },
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // Без return из inline-Column: ранний выход ломает группы Compose 1.5 (краш SlotTable).
            if (uiState.isLoading) LoadingBox()
            else when (currentTab) {
                ReportTab.SUMMARY -> SummaryReportTab(
                    uiState,
                    onEditNorm = { showNormDialog = true },
                    onOpenSettings = onOpenSettings
                )
                ReportTab.CHARTS -> ChartsReportTab(uiState, onOpenDay)
                ReportTab.BANK -> BankReportTab(
                    uiState = uiState,
                    onStrategy = viewModel::setBankStrategy,
                    onSaveParameters = viewModel::saveBankParameters,
                    onApply = viewModel::applyAutomaticBank,
                    onReset = viewModel::resetBankMonth,
                    onManualChange = viewModel::changeManualAllocation,
                    onManualSet = viewModel::setManualAllocation
                )
            }
        }
    }

    if (showMonthPicker) {
        MonthSelectDialog(
            initial = uiState.month,
            onPick = { month ->
                showMonthPicker = false
                viewModel.goToMonth(month)
            },
            onDismiss = { showMonthPicker = false }
        )
    }

    if (showNormDialog) {
        NormDialog(
            current = uiState.normHours,
            isAuto = uiState.normIsAuto,
            scheduleNorm = uiState.scheduleNormHours,
            onSave = { showNormDialog = false; viewModel.setNormHours(it) },
            onDismiss = { showNormDialog = false }
        )
    }

    exportFile?.let { prepared ->
        FileExportDialog(
            file = prepared.file,
            mimeType = prepared.mimeType,
            title = "Отчёт готов",
            description = "Сохраните файл в нужную папку или отправьте в другое приложение.",
            onSave = viewModel::saveExport,
            onError = { viewModel.dismissExport(); viewModel.showMessage("Не удалось открыть системное окно") },
            onDismiss = viewModel::dismissExport
        )
    }
}

@Composable
private fun SummaryReportTab(
    uiState: ReportUiState,
    onEditNorm: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var breakdownOpen by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SalaryHeroCard(uiState, onOpenSettings) }
        item { NormCard(uiState, onEditNorm) }
        item { MonthStatsCard(uiState) }
        if (uiState.salaryReady) {
            item { BreakdownCard(uiState, expanded = breakdownOpen, onToggle = { breakdownOpen = !breakdownOpen }) }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Операции за месяц", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (uiState.totals.isNotEmpty()) Text(
                    "нормо-часы",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (uiState.totals.isEmpty()) {
            item { EmptyState(Icons.Outlined.Insights, "Нет операций", "В этом месяце операции ещё не вносились") }
        } else {
            item {
                SectionCard {
                    uiState.totals.forEachIndexed { index, total ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(total.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${total.totalCount} шт · ${total.grade} разряд · ${total.durationHours.formatNumber()} нч".noOrphans(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                total.totalHours.formatNumber(),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Одна главная цифра: заработано по отмеченным дням, а на той же полосе — прогноз на месяц.
 * Аванс и вторая часть — рядом, под каждой суммой её прогноз.
 */
@Composable
private fun SalaryHeroCard(uiState: ReportUiState, onOpenSettings: () -> Unit) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = onContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (uiState.rateMissing) {
                Text("Укажите ставку", style = MaterialTheme.typography.titleLarge)
                Text("Без часовой ставки зарплата не считается.")
                Button(onClick = onOpenSettings) { Text("Открыть настройки") }
            } else {
                val salary = uiState.salary
                val forecast = uiState.forecastSalary.takeIf { uiState.salaryReady }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Заработано за ${uiState.month.format(monthNameFormatter)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (forecast != null) InfoButton(
                        "Прогноз на месяц",
                        "Отмеченные смены, переработки и выходы уже учтены. Остальные рабочие дни по графику считаются " +
                            "по ${uiState.defaultDayHours.formatNumber()} ч. Дни, заранее отмеченные как «Праздник ×2», в прогноз не входят.",
                        Modifier.size(32.dp)
                    )
                }
                AnimatedValue("${salary.fullSalary.formatNumber()} ₽", MaterialTheme.typography.headlineLarge)
                if (forecast != null && forecast.fullSalary > 0) {
                    LinearProgressIndicator(
                        progress = { (salary.fullSalary / forecast.fullSalary).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = onContainer.copy(alpha = 0.15f)
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text("по отмеченным дням", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        Text("прогноз ${forecast.fullSalary.formatNumber()} ₽", style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, softWrap = false)
                    }
                }
                HorizontalDivider(color = onContainer.copy(alpha = 0.15f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SalaryPart("Аванс", salary.advance, forecast?.advance, Modifier.weight(1f))
                    SalaryPart("Вторая часть", salary.remainingSalary, forecast?.remainingSalary, Modifier.weight(1f))
                }
            }
        }
    }
}

private val monthNameFormatter = java.time.format.DateTimeFormatter.ofPattern("LLLL", java.util.Locale("ru"))

@Composable
private fun SalaryPart(title: String, amount: Double, forecast: Double?, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        // Суммы не переносятся: при крупном шрифте сумма рвалась на цифры.
        Text("${amount.formatNumber()} ₽", style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
        if (forecast != null) Text(
            "прогноз ${forecast.formatNumber()} ₽",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun MonthStatsCard(uiState: ReportUiState) {
    SectionCard {
        val cells = listOf(
            Triple(uiState.workedDays.toString(), "смен", MaterialTheme.colorScheme.onSurface),
            Triple(uiState.workedHours.formatNumber(), "часов", MaterialTheme.colorScheme.primary),
            Triple(uiState.producedHours.formatNumber(), "нормо-ч", MaterialTheme.colorScheme.onSurface),
            Triple(uiState.efficiencyPercent?.let { "${it.roundToInt()}%" } ?: "–", "выработка", MaterialTheme.colorScheme.onSurface)
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Четыре в ряд, пока помещаются; при крупном шрифте — два ряда по два.
            val perRow = if (maxWidth / LocalDensity.current.fontScale >= 280.dp) 4 else 2
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                cells.chunked(perRow).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { (value, label, color) -> StatCell(value, label, valueColor = color) }
                    }
                }
            }
        }
        uiState.bank?.let {
            if (it.rawHours != it.adjustedHours) Text(
                "Факт операций: ${it.rawHours.formatNumber()} нч · после банка: ${it.adjustedHours.formatNumber()} нч".noOrphans(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Норма — одной тонкой строкой; пояснение — в диалоге «Изменить». */
@Composable
private fun NormCard(uiState: ReportUiState, onEdit: () -> Unit) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildString {
                    append("Норма ${uiState.normHours.formatNumber()} ч")
                    append(if (uiState.normIsAuto) " · по графику" else " · задана вручную")
                }.noOrphans(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onEdit) { Text("Изменить", maxLines = 1) }
        }
        if (uiState.normHours > 0) {
            LinearProgressIndicator(
                progress = { (uiState.workedHours / uiState.normHours).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape)
            )
            Text(
                ("Отработано ${uiState.workedHours.formatNumber()} ч" +
                    if (uiState.workedHours > uiState.normHours) " · сверх нормы ${(uiState.workedHours - uiState.normHours).formatNumber()} ч"
                    else " из ${uiState.normHours.formatNumber()} ч")
                    .noOrphans(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BreakdownCard(uiState: ReportUiState, expanded: Boolean, onToggle: () -> Unit) {
    val salary = uiState.salary
    val rate = uiState.hourlyRate
    SectionCard(modifier = Modifier.clickable(onClickLabel = if (expanded) "Свернуть" else "Развернуть", onClick = onToggle)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Из чего складывается", style = MaterialTheme.typography.titleSmall)
                if (!expanded) Text(
                    breakdownSummary(uiState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Без раннего return из лямбды: в компиляторе Compose 1.5.10 он ломал SlotTable.
        if (expanded) {
            BreakdownRow("Обычные часы", "${salary.regularHours.formatNumber()} ч × ${rate.formatNumber()} ₽", salary.regularHours * rate)
            if (salary.delaySingleHours > 0) Text(
                "в том числе задержки до нормы: ${salary.delaySingleHours.formatNumber()} ч".noOrphans(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (salary.fixedDoubleHours > 0) {
                BreakdownRow("Выходные и праздники", "${salary.fixedDoubleHours.formatNumber()} ч × 2 × ${rate.formatNumber()} ₽", salary.fixedDoubleHours * 2 * rate)
            }
            if (salary.delayDoubleHours > 0) {
                BreakdownRow("Задержки сверх нормы", "${salary.delayDoubleHours.formatNumber()} ч × 2 × ${rate.formatNumber()} ₽", salary.delayDoubleHours * 2 * rate)
            }
            if (salary.manualSingleHours > 0) {
                BreakdownRow("Ручной режим ×1", "${salary.manualSingleHours.formatNumber()} ч × ${rate.formatNumber()} ₽", salary.manualSingleHours * rate)
            }
            if (salary.manualDoubleHours > 0) {
                // Свой коэффициент у каждого ручного дня: при разных — средний по часам.
                val k = salary.manualRaisedPaidHours / salary.manualDoubleHours
                BreakdownRow(
                    "Ручной режим, повышенные",
                    "${salary.manualDoubleHours.formatNumber()} ч × ${if (salary.manualMixedMultipliers) "в среднем " else ""}${k.formatNumber()} × ${rate.formatNumber()} ₽",
                    salary.manualRaisedPaidHours * rate
                )
            }
            if (salary.compOffHours > 0) Text(
                "Отгулы: ${salary.compOffHours.formatNumber()} ч — оплачены в составе обычных часов".noOrphans(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (salary.unpaidDays > 0) Text(
            "Дни БС: ${salary.unpaidDays} — без оплаты, норма месяца меньше на эти дни".noOrphans(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (salary.compWorkHours > 0) Text(
                "Выходы за отгул: ${salary.compWorkHours.formatNumber()} ч — не оплачиваются, ушли в баланс отгулов".noOrphans(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiState.compTimeBalance != 0.0 || salary.compWorkHours > 0 || salary.compOffHours > 0) Text(
                "Баланс отгулов за всё время: ${uiState.compTimeBalance.formatNumber()} ч".noOrphans(),
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.compTimeBalance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            BreakdownRow("Итого", null, salary.fullSalary, bold = true)
            BreakdownRow(
                "Аванс",
                "${salary.advanceHours.formatNumber()} ч × ${uiState.contractHourlyRate.formatNumber()} ₽ (договорная)",
                salary.advance
            )
            if (salary.advanceExtraHours > 0) Text(
                "в том числе переработки и выходные вместо пропущенных дней: ${salary.advanceExtraHours.formatNumber()} ч".noOrphans(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BreakdownRow("Вторая часть", "итого − аванс", salary.remainingSalary)
        }
    }
}

/** Что входит в зарплату месяца — подпись свёрнутой расшифровки. */
private fun breakdownSummary(uiState: ReportUiState): String {
    val salary = uiState.salary
    return buildList {
        add("обычные часы")
        if (salary.fixedDoubleHours > 0 || salary.delayDoubleHours > 0) add("×2")
        if (salary.manualSingleHours > 0 || salary.manualDoubleHours > 0) add("ручной режим")
        if (salary.compOffHours > 0 || salary.compWorkHours > 0) add("отгулы")
        add("аванс")
    }.joinToString(", ").replaceFirstChar { it.uppercase() }
}

@Composable
private fun BreakdownRow(title: String, formula: String?, amount: Double, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title.noOrphans(), fontWeight = if (bold) FontWeight.SemiBold else null)
            if (formula != null) Text(formula.noOrphans(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "${amount.formatNumber()} ₽",
            style = if (bold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.SemiBold else null,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
private fun NormDialog(
    current: Double,
    isAuto: Boolean,
    scheduleNorm: Double,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(if (isAuto) "" else current.toString().removeSuffix(".0").replace('.', ',')) }
    val parsed = text.replace(',', '.').filterNot(Char::isWhitespace).toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Норма месяца") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Часов в месяце") },
                    placeholder = { Text(scheduleNorm.formatNumber()) },
                    suffix = { Text("ч") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    supportingText = {
                        Text(
                            ("По графику: ${scheduleNorm.formatNumber()} ч. Оставьте поле пустым, чтобы считать по графику. " +
                                "Норма делит задержки на оплачиваемые ×1 и ×2. Праздники производственного календаря " +
                                "отметьте в наряде как «Праздник ×2».").noOrphans()
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(if (text.isBlank()) 0.0 else parsed!!) },
                enabled = text.isBlank() || (parsed != null && parsed.isFinite() && parsed > 0)
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BankReportTab(
    uiState: ReportUiState,
    onStrategy: (BankStrategy) -> Unit,
    onSaveParameters: (Double, Double, Double, Double, Double) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onManualChange: (Long, Int) -> Unit,
    onManualSet: (Long, Int) -> Unit
) {
    val bank = uiState.bank ?: return
    val plan = bank.plan
    var coefficientText by remember(plan.coefficient) { mutableStateOf(plan.coefficient.toString()) }
    var grade3Text by remember(plan.grade3Percent) { mutableStateOf(plan.grade3Percent.toString().removeSuffix(".0")) }
    var grade4Text by remember(plan.grade4Percent) { mutableStateOf(plan.grade4Percent.toString().removeSuffix(".0")) }
    var grade5Text by remember(plan.grade5Percent) { mutableStateOf(plan.grade5Percent.toString().removeSuffix(".0")) }
    var grade6Text by remember(plan.grade6Percent) { mutableStateOf(plan.grade6Percent.toString().removeSuffix(".0")) }
    var confirmReset by remember { mutableStateOf(false) }
    var manualFor by remember { mutableStateOf<BankOperationLine?>(null) }
    val coefficient = coefficientText.replace(',', '.').toDoubleOrNull()
    val gradeValues = listOf(grade3Text, grade4Text, grade5Text, grade6Text).map { it.replace(',', '.').toDoubleOrNull() }
    val gradeSum = gradeValues.filterNotNull().sum()
    val gradesValid = gradeValues.all { it != null && it >= 0 } && kotlin.math.abs(gradeSum - 100.0) < 0.01
    val coefficientValid = coefficient != null && coefficient >= 0
    val dirty = coefficient != plan.coefficient ||
        gradeValues != listOf(plan.grade3Percent, plan.grade4Percent, plan.grade5Percent, plan.grade6Percent)
    val paramsValid = coefficientValid && (plan.strategy != BankStrategy.GRADE_RATIO || gradesValid) && gradeValues.all { it != null }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AccountBalance, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Банк (эксперимент)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        InfoButton(
                            "Как работает банк",
                            "Лимит месяца = фактические часы присутствия × коэффициент. Если нормо-часов больше лимита, " +
                                "часть операций откладывается в банк; если меньше — добирается из ранее отложенного. " +
                                "Наряды и журнал не изменяются: банк — отдельный расчётный слой отчёта."
                        )
                    }
                    Text(
                        "Наряды не изменяются. Здесь хранится только помесячное расчётное распределение.".noOrphans(),
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }
        }
        item {
            SectionCard {
                Text("Стратегия", style = MaterialTheme.typography.titleSmall)
                // Чипы по ширине текста с переносом целыми чипами: в половине ширины «Пропорционально» рвалось.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    BankStrategy.entries.forEach { value ->
                        FilterChip(
                            selected = plan.strategy == value,
                            onClick = { if (plan.strategy != value) onStrategy(value) },
                            label = {
                                Text(
                                    when (value) {
                                        BankStrategy.PROPORTIONAL -> "Пропорционально"
                                        BankStrategy.PRIORITY -> "Приоритет"
                                        BankStrategy.GRADE_RATIO -> "По разрядам"
                                        BankStrategy.MANUAL -> "Вручную"
                                    },
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
                Text(
                    when (plan.strategy) {
                        BankStrategy.PROPORTIONAL -> "Операции переносятся равномерно, по доле каждой."
                        BankStrategy.PRIORITY -> "Сначала переносятся операции младших разрядов, добираются — старших."
                        BankStrategy.GRADE_RATIO -> "Перенос делится между разрядами в заданных долях."
                        BankStrategy.MANUAL -> "Вы сами переносите штуки кнопками у каждой операции."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (plan.strategy == BankStrategy.GRADE_RATIO) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradePercentField(3, grade3Text, { grade3Text = it }, Modifier.weight(1f))
                        GradePercentField(4, grade4Text, { grade4Text = it }, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradePercentField(5, grade5Text, { grade5Text = it }, Modifier.weight(1f))
                        GradePercentField(6, grade6Text, { grade6Text = it }, Modifier.weight(1f))
                    }
                    Text(
                        "Сумма: ${gradeSum.formatNumber()}% из 100%",
                        color = if (gradesValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = coefficientText,
                    onValueChange = { coefficientText = it },
                    label = { Text("Коэффициент лимита") },
                    supportingText = { Text("Лимит = часы за месяц × коэффициент") },
                    isError = !coefficientValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (dirty) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (paramsValid) "Есть несохранённые изменения" else "Исправьте отмеченные поля",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (paramsValid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onSaveParameters(coefficient!!, gradeValues[0]!!, gradeValues[1]!!, gradeValues[2]!!, gradeValues[3]!!)
                            },
                            enabled = paramsValid
                        ) { Text("Сохранить") }
                    }
                }
            }
        }
        item {
            SectionCard {
                val target = bank.targetHours
                val raw = bank.rawHours
                Text("Лимит месяца", style = MaterialTheme.typography.titleSmall)
                Text("${raw.formatNumber()} из ${target.formatNumber()} нч", style = MaterialTheme.typography.headlineSmall)
                if (target > 0) {
                    LinearProgressIndicator(
                        progress = { (raw / target).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = if (raw > target) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    when {
                        target <= 0 -> "Лимит появится, когда в нарядах будут часы присутствия."
                        raw > target -> "Сверх лимита ${(raw - target).formatNumber()} нч — их можно отложить в банк."
                        raw < target -> "До лимита ${(target - raw).formatNumber()} нч — можно добрать из банка (там ${bank.bankHoursBefore.formatNumber()} нч)."
                        else -> "Ровно по лимиту."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(Modifier.fillMaxWidth()) {
                    // Единица — в заголовке «… нч» выше: с ней подписи переносились.
                    StatCell(bank.adjustedHours.formatNumber(), "в отчёте")
                    StatCell(bank.bankHoursBefore.formatNumber(), "банк до")
                    StatCell(bank.bankHoursAfter.formatNumber(), "банк после")
                }
                Text(
                    "Основа лимита: ${bank.attendanceHours.formatNumber()} ч за месяц × ${plan.coefficient.formatNumber()}".noOrphans(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (plan.strategy != BankStrategy.MANUAL) {
                    Button(onClick = onApply, enabled = !dirty, modifier = Modifier.fillMaxWidth()) { Text("Рассчитать и применить") }
                    if (dirty) Text(
                        "Сначала сохраните параметры — расчёт использует сохранённые значения.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Сбросить распределение", maxLines = 1) }
            }
        }
        item { Text("Распределение операций", style = MaterialTheme.typography.titleMedium) }
        if (bank.lines.isEmpty()) {
            item { Text("В этом месяце нет операций для распределения.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        bank.lines.forEach { line ->
            item(key = line.operation.id) {
                SectionCard {
                    Column {
                        Text(line.operation.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf("${line.operation.grade} разряд", "факт ${line.rawQuantity}", "в отчёте ${line.adjustedQuantity}",
                                "в банке ${line.balanceAfter}").joinToString(" · ") { it.replace(' ', '\u00A0') },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (plan.strategy == BankStrategy.MANUAL) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = { onManualChange(line.operation.id, -1) },
                                enabled = line.balanceAfter > 0
                            ) { Text("−1 в наряд", maxLines = 1) }
                            TextButton(onClick = { manualFor = line }, modifier = Modifier.weight(1f)) {
                                Text(
                                    when {
                                        line.bankedQuantity > 0 -> "в банк ${line.bankedQuantity}"
                                        line.bankedQuantity < 0 -> "из банка ${-line.bankedQuantity}"
                                        else -> "без переноса"
                                    }
                                )
                            }
                            OutlinedButton(
                                onClick = { onManualChange(line.operation.id, 1) },
                                enabled = line.adjustedQuantity > 0
                            ) { Text("+1 в банк", maxLines = 1) }
                        }
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить распределение?") },
            text = { Text("Все переносы в банк и из банка за ${uiState.displayMonth.lowercase()} будут удалены. Наряды не изменятся.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmReset = false; onReset() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Сбросить") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } }
        )
    }

    manualFor?.let { line ->
        var text by remember(line.operation.id) { mutableStateOf(line.bankedQuantity.toString()) }
        val value = text.trim().toIntOrNull()
        val range = -line.balanceBefore..line.rawQuantity
        AlertDialog(
            onDismissRequest = { manualFor = null },
            title = { Text(line.operation.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Сколько штук перенести в банк в этом месяце. Отрицательное число — взять из банка.")
                    OutlinedTextField(
                        value = text,
                        onValueChange = { new -> if (new.length <= 7 && new.all { it.isDigit() || it == '-' }) text = new },
                        label = { Text("Штук") },
                        isError = value == null || value !in range,
                        supportingText = { Text("От ${range.first} до ${range.last}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onManualSet(line.operation.id, value!!); manualFor = null },
                    enabled = value != null && value in range
                ) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { manualFor = null }) { Text("Отмена") } }
        )
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
private fun ChartsReportTab(uiState: ReportUiState, onOpenDay: (LocalDate) -> Unit) {
    var selectedDay by rememberSaveable(uiState.month) { mutableStateOf<Int?>(null) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionCard {
                Text("Нормо-часы по дням", style = MaterialTheme.typography.titleMedium)
                Text("Выполненные операции × норма времени, до переводов в банк. Коснитесь столбика, чтобы увидеть день.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (uiState.dailyNormHours.isEmpty()) Text("В этом месяце пока нет нарядов")
                else {
                    MonthColumnChart(
                        dailyValues = uiState.dailyNormHours,
                        daysInMonth = uiState.month.lengthOfMonth(),
                        unit = "нч",
                        referenceValue = uiState.defaultDayHours,
                        referenceLabel = "${uiState.defaultDayHours.formatNumber()} ч смены",
                        selectedDay = selectedDay,
                        onSelectDay = { selectedDay = it }
                    )
                    selectedDay?.let { day ->
                        val value = uiState.dailyNormHours.firstOrNull { it.first == day }?.second ?: 0.0
                        val date = uiState.month.atDay(day)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${date.formatShort()}: ${value.formatNumber()} нч",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onOpenDay(date) }) { Text("Открыть день") }
                        }
                    }
                }
            }
        }
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
                        slices = byGrade.map { (grade, hours) -> "$grade разряд" to hours },
                        centerTitle = uiState.producedHours.formatNumber(),
                        centerSubtitle = "нормо-часов"
                    )
                }
            }
            item {
                SectionCard {
                    Text("Нормо-часы по операциям", style = MaterialTheme.typography.titleMedium)
                    HorizontalBarChart(uiState.totals.sortedByDescending { it.totalHours }
                        .map { Triple(it.name, it.totalHours, it.totalHours.formatHours()) })
                }
            }
        }
    }
}
