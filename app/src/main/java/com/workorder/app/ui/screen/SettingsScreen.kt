package com.workorder.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Feedback
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workorder.app.BuildConfig
import com.workorder.app.data.model.ThemeMode
import com.workorder.app.data.model.WorkSchedule
import com.workorder.app.ui.component.DataTransferControls
import com.workorder.app.ui.component.FeedbackDialog
import com.workorder.app.ui.component.SectionCard
import com.workorder.app.ui.theme.supportsDynamicColor
import com.workorder.app.ui.theme.themePresets
import com.workorder.app.ui.viewmodel.SettingsForm
import com.workorder.app.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onNavigateToOperations: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val saving by viewModel.savingForm.collectAsStateWithLifecycle()
    val transferBusy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    var feedback by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it, withDismissAction = true)
            viewModel.consumeMessage()
        }
    }
    val draft = form
    if (draft == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val settings = draft.stored
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Настройки") }) },
        bottomBar = {
            if (draft.dirty) {
                Surface(tonalElevation = 4.dp, shadowElevation = 4.dp) {
                    Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            if (draft.valid) "Есть несохранённые изменения" else "Исправьте отмеченные поля",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (draft.valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                        )
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = viewModel::discardForm, enabled = !saving) { Text("Отменить") }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { focus.clearFocus(); viewModel.saveForm() },
                                enabled = draft.valid && !saving && !transferBusy,
                                modifier = Modifier.weight(1f)
                            ) { Text(if (saving) "Сохраняем…" else "Сохранить настройки") }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard {
                Text("Оплата и смена", style = MaterialTheme.typography.titleMedium)
                Text("Измени нужные поля и нажми «Сохранить настройки» внизу экрана.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PreferenceField(draft, SettingsForm.RATE, "Реальная ставка в час, ₽", viewModel::edit, !saving)
                PreferenceField(draft, SettingsForm.CONTRACT, "Договорная ставка для аванса, ₽/ч", viewModel::edit, !saving)
                PreferenceField(draft, SettingsForm.HOURS, "Часов в смене по умолчанию", viewModel::edit, !saving,
                    "Подставляется при создании нового наряда")
                Text("Рабочий график", style = MaterialTheme.typography.labelLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    WorkSchedule.entries.forEachIndexed { index, schedule ->
                        SegmentedButton(
                            selected = draft.schedule == schedule,
                            onClick = { viewModel.edit(SettingsForm.SCHEDULE, schedule.name) },
                            enabled = !saving,
                            shape = SegmentedButtonDefaults.itemShape(index, WorkSchedule.entries.size)
                        ) { Text(if (schedule == WorkSchedule.FIVE_TWO) "5/2" else "2/2") }
                    }
                }
                if (draft.schedule == WorkSchedule.TWO_TWO) {
                    PreferenceField(draft, SettingsForm.ANCHOR, "Первая рабочая смена цикла 2/2", viewModel::edit, !saving,
                        "ГГГГ-ММ-ДД · дата сдвигает весь цикл", KeyboardType.Ascii)
                }
            }

            SectionCard {
                Text("Оформление", style = MaterialTheme.typography.titleMedium)
                Text("Оформление и масштаб сохраняются сразу.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            enabled = !transferBusy,
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                        ) { Text(when (mode) { ThemeMode.SYSTEM -> "Система"; ThemeMode.LIGHT -> "Светлая"; ThemeMode.DARK -> "Тёмная" }) }
                    }
                }
                if (supportsDynamicColor) {
                    PreferenceSwitch("Цвета из обоев", "Material You (Android 12+)", settings.dynamicColor,
                        !transferBusy, viewModel::setDynamicColor)
                }
                if (!settings.dynamicColor) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(themePresets) { preset ->
                            val selected = settings.themePreset == preset.name
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable(enabled = !transferBusy) { viewModel.setThemePreset(preset.name) }
                            ) {
                                Box(
                                    Modifier.size(44.dp).clip(CircleShape).background(preset.previewColor)
                                        .border(if (selected) 3.dp else 1.dp,
                                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                            CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) Icon(Icons.Default.Check, null, tint = Color.White)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(preset.displayName, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                HorizontalDivider()
                var scale by rememberSaveable(settings.interfaceScale) { mutableStateOf((settings.interfaceScale * 100).toFloat()) }
                Text("Масштаб интерфейса: ${scale.roundToInt()}%", style = MaterialTheme.typography.titleSmall)
                Text("Размер текста, кнопок и карточек. Системный размер шрифта тоже учитывается.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = scale,
                    onValueChange = { scale = it },
                    onValueChangeFinished = { viewModel.setInterfaceScale((scale / 5).roundToInt() * 5 / 100.0) },
                    valueRange = 85f..130f, steps = 8, enabled = !transferBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("85%", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { scale = 100f; viewModel.setInterfaceScale(1.0) }, enabled = !transferBusy) { Text("Сбросить до 100%") }
                    Spacer(Modifier.weight(1f))
                    Text("130%", style = MaterialTheme.typography.labelMedium)
                }
            }

            SectionCard {
                Text("Экспериментальные функции", style = MaterialTheme.typography.titleMedium)
                PreferenceSwitch(
                    "Специализация дня",
                    "Кнопка в календаре переворачивает дни: часы ↔ специализации. Планируй специализацию без добавления часов. Выключение скрывает подписи, сохраняя текст.",
                    settings.daySpecializationEnabled, !transferBusy, viewModel::setDaySpecializationEnabled
                )
                Text("Переключатель сохраняется сразу.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionCard {
                Text("Данные", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().clickable(onClick = onNavigateToOperations).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Handyman, null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Каталог операций", style = MaterialTheme.typography.bodyLarge)
                        Text("Названия и нормы времени", style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
                if (draft.dirty) Text("В экспорт войдут сохранённые настройки. Изменения формы пока в черновике.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DataTransferControls(viewModel)
            }

            SectionCard {
                Text("Обратная связь", style = MaterialTheme.typography.titleMedium)
                Text("Сообщи об ошибке или предложи улучшение. Обращение откроется в GitHub для проверки и отправки.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { feedback = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Feedback, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Написать разработчику")
                }
            }
            SectionCard {
                Text("Наряд", style = MaterialTheme.typography.titleMedium)
                Text("Версия ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (feedback) FeedbackDialog(onDismiss = { feedback = false })
}

@Composable
private fun PreferenceField(
    draft: SettingsForm, field: String, label: String, onEdit: (String, String) -> Unit,
    enabled: Boolean, hint: String = "", keyboard: KeyboardType = KeyboardType.Decimal
) {
    val error = draft.error(field)
    OutlinedTextField(
        value = draft.text(field), onValueChange = { onEdit(field, it) },
        label = { Text(label) }, enabled = enabled,
        isError = error != null, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        supportingText = if (error != null || hint.isNotEmpty()) {{ Text(error ?: hint) }} else null,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange, enabled = enabled)
    }
}
