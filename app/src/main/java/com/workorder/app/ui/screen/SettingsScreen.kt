package com.workorder.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workorder.app.BuildConfig
import com.workorder.app.data.model.ThemeMode
import com.workorder.app.data.model.WorkSchedule
import com.workorder.app.ui.component.SectionCard
import com.workorder.app.ui.theme.supportsDynamicColor
import com.workorder.app.ui.theme.themePresets
import com.workorder.app.ui.viewmodel.SettingsViewModel
import com.workorder.app.util.formatNumber
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToOperations: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // Локальный текст ставки; сохранение — по кнопке-галочке
    var rateText by remember(settings.hourlyRate) {
        mutableStateOf(settings.hourlyRate.formatNumber().replace(" ", ""))
    }
    val parsedRate = rateText.replace(',', '.').replace(" ", "").toDoubleOrNull()
    val rateChanged = parsedRate != null && parsedRate != settings.hourlyRate

    var contractRateText by remember(settings.contractHourlyRate) {
        mutableStateOf(settings.contractHourlyRate.formatNumber().replace(" ", ""))
    }
    val parsedContractRate = contractRateText.replace(',', '.').replace(" ", "").toDoubleOrNull()
    val contractRateChanged = parsedContractRate != null && parsedContractRate != settings.contractHourlyRate

    var anchorText by remember(settings.shiftAnchorDate) { mutableStateOf(settings.shiftAnchorDate) }
    val anchorValid = runCatching { LocalDate.parse(anchorText) }.isSuccess

    // Продолжительность смены по умолчанию
    var dayHoursText by remember(settings.defaultDayHours) {
        mutableStateOf(settings.defaultDayHours.formatNumber().replace(" ", ""))
    }
    val parsedDayHours = dayHoursText.replace(',', '.').replace(" ", "").toDoubleOrNull()
    val dayHoursChanged = parsedDayHours != null && parsedDayHours > 0 &&
        parsedDayHours != settings.defaultDayHours

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text("Настройки") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard {
                Text("Оплата и смена", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = rateText,
                    onValueChange = { rateText = it },
                    label = { Text("Реальная ставка в час, ₽") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (rateChanged) {
                            IconButton(onClick = { parsedRate?.let(viewModel::setHourlyRate) }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Сохранить ставку",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )
                OutlinedTextField(
                    value = contractRateText,
                    onValueChange = { contractRateText = it },
                    label = { Text("Договорная ставка для аванса, ₽/ч") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (contractRateChanged) {
                            IconButton(onClick = { parsedContractRate?.let(viewModel::setContractHourlyRate) }) {
                                Icon(Icons.Default.Check, contentDescription = "Сохранить договорную ставку")
                            }
                        }
                    }
                )
                OutlinedTextField(
                    value = dayHoursText,
                    onValueChange = { dayHoursText = it },
                    label = { Text("Часов в смене по умолчанию") },
                    supportingText = { Text("Подставляется при создании нового наряда") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        if (dayHoursChanged) {
                            IconButton(onClick = { parsedDayHours?.let(viewModel::setDefaultDayHours) }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Сохранить часы по умолчанию",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )

                Text("Рабочий график", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    WorkSchedule.entries.forEachIndexed { index, schedule ->
                        SegmentedButton(
                            selected = settings.workSchedule == schedule,
                            onClick = { viewModel.setWorkSchedule(schedule) },
                            shape = SegmentedButtonDefaults.itemShape(index, WorkSchedule.entries.size)
                        ) {
                            Text(if (schedule == WorkSchedule.FIVE_TWO) "5/2" else "2/2")
                        }
                    }
                }
                if (settings.workSchedule == WorkSchedule.TWO_TWO) {
                    OutlinedTextField(
                        value = anchorText,
                        onValueChange = { anchorText = it },
                        label = { Text("Первая рабочая смена цикла 2/2") },
                        supportingText = { Text("Формат: ГГГГ-ММ-ДД; изменение даты сдвигает весь цикл") },
                        isError = !anchorValid,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (anchorValid && anchorText != settings.shiftAnchorDate) {
                                IconButton(onClick = { viewModel.setShiftAnchorDate(anchorText) }) {
                                    Icon(Icons.Default.Check, contentDescription = "Сохранить смещение графика")
                                }
                            }
                        }
                    )
                }
            }

            SectionCard {
                Text("Оформление", style = MaterialTheme.typography.titleMedium)

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size
                            )
                        ) {
                            Text(
                                when (mode) {
                                    ThemeMode.SYSTEM -> "Система"
                                    ThemeMode.LIGHT -> "Светлая"
                                    ThemeMode.DARK -> "Тёмная"
                                }
                            )
                        }
                    }
                }

                if (supportsDynamicColor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Цвета из обоев", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Material You (Android 12+)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.dynamicColor,
                            onCheckedChange = viewModel::setDynamicColor
                        )
                    }
                }

                if (!settings.dynamicColor) {
                    Text(
                        text = "Цветовая схема",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(themePresets) { preset ->
                            val isSelected = settings.themePreset == preset.name
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable {
                                    viewModel.setThemePreset(preset.name)
                                }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(preset.previewColor)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = androidx.compose.ui.graphics.Color.White
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = preset.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            SectionCard {
                Text("Данные", style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToOperations),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Handyman,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Каталог операций", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Названия и нормы времени",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.exportData() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.FileUpload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Экспорт")
                    }
                    OutlinedButton(
                        onClick = { viewModel.importData() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Импорт")
                    }
                }
                Text(
                    text = "Файл: Документы/WorkOrder/work_order_settings.json",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Наряд", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
