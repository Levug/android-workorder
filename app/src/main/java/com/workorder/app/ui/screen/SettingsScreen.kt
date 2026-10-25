package com.workorder.app.ui.screen

import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Code
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import com.workorder.app.util.formatNumber
import com.workorder.app.sync.WearConnectionState
import com.workorder.app.data.model.quickAmountList
import com.workorder.app.data.model.Settings
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
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
import com.workorder.app.data.model.CUSTOM_THEME_PRESET
import com.workorder.app.data.model.ThemeMode
import com.workorder.app.data.model.WorkSchedule
import androidx.compose.material.icons.outlined.CalendarMonth
import com.workorder.app.domain.SalaryCalculator
import com.workorder.app.util.noOrphans
import com.workorder.app.ui.component.DataTransferControls
import com.workorder.app.ui.component.DateSelectDialog
import com.workorder.app.ui.component.FeedbackDialog
import com.workorder.app.ui.component.CustomThemeSwatch
import com.workorder.app.ui.component.SectionCard
import com.workorder.app.ui.component.ThemeColorDialog
import com.workorder.app.ui.component.WearConnectionCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    var pickingColor by rememberSaveable { mutableStateOf(false) }

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
    var section by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    BackHandler(enabled = section != null) { section = null }
    val wear = viewModel.wearState?.collectAsStateWithLifecycle()
    val operationCounts by viewModel.operationCounts.collectAsStateWithLifecycle()
    val compTimeBalance by viewModel.compTimeBalance.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshWear() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(section?.title ?: "Настройки", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (section != null) IconButton(onClick = { section = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад к настройкам")
                    }
                }
            )
        },
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
                            ) { Text(if (saving) "Сохраняем…" else "Сохранить", maxLines = 1) }
                        }
                    }
                }
            }
        }
    ) { padding ->
        // Своя прокрутка у каждого раздела: возврат к списку не оставляет его прокрученным.
        key(section) {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (section) {
                    null -> SettingsOverview(
                        settings = settings,
                        wear = wear?.value,
                        operationCounts = operationCounts,
                        onOpen = { section = it },
                        onCatalog = onNavigateToOperations,
                        onFeedback = { feedback = true }
                    )
                    SettingsSection.PAY -> PaySection(draft, compTimeBalance, !saving, viewModel::edit)
                    SettingsSection.WATCH -> if (wear != null) WearConnectionCard(
                        state = wear.value,
                        enabled = !transferBusy,
                        onToggle = viewModel::setWearSyncEnabled,
                        onRefresh = viewModel::refreshWear,
                        showTitle = false
                    )
                    SettingsSection.APPEARANCE -> AppearanceSection(settings, !transferBusy, viewModel) { pickingColor = true }
                    SettingsSection.EXPERIMENTS -> ExperimentsSection(draft, settings, !saving, !transferBusy, viewModel)
                    SettingsSection.DATA -> SectionCard {
                        if (draft.dirty) Text("В экспорт войдут сохранённые настройки. Изменения формы пока в черновике.".noOrphans(),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DataTransferControls(viewModel)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (feedback) FeedbackDialog(onDismiss = { feedback = false })
    if (pickingColor) {
        ThemeColorDialog(
            initial = settings.customThemeColor,
            onConfirm = { viewModel.setCustomTheme(it); pickingColor = false },
            onDismiss = { pickingColor = false }
        )
    }
}

private enum class SettingsSection(val title: String) {
    PAY("Оплата и смена"),
    WATCH("Часы Wear OS"),
    APPEARANCE("Оформление"),
    EXPERIMENTS("Эксперименты"),
    DATA("Резервная копия")
}

/** Разделы со сводкой вместо одной длинной формы: главное видно без прокрутки. */
@Composable
private fun SettingsOverview(
    settings: Settings,
    wear: WearConnectionState?,
    operationCounts: Pair<Int, Int>?,
    onOpen: (SettingsSection) -> Unit,
    onCatalog: () -> Unit,
    onFeedback: () -> Unit
) {
    SettingsGroup("Работа") {
        SettingsRow(Icons.Outlined.Payments, "Оплата и смена", paySummary(settings)) { onOpen(SettingsSection.PAY) }
        SettingsRow(Icons.Outlined.Handyman, "Каталог операций", catalogSummary(operationCounts), onClick = onCatalog)
    }
    if (wear != null) SettingsGroup("Устройства") {
        SettingsRow(
            Icons.Outlined.Watch, "Часы Wear OS", watchSummary(wear),
            trailing = if (wear.enabled && wear.watches.isNotEmpty()) {{ Badge("на связи") }} else null
        ) { onOpen(SettingsSection.WATCH) }
    }
    SettingsGroup("Вид и функции") {
        SettingsRow(
            Icons.Outlined.Palette, "Оформление", appearanceSummary(settings),
            trailing = {
                Box(
                    Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                )
            }
        ) { onOpen(SettingsSection.APPEARANCE) }
        val switches = experimentSwitches(settings)
        SettingsRow(
            Icons.Outlined.Science, "Эксперименты",
            "Банк, свайп дней, цвета календаря, быстрые кнопки, партии " +
                settings.quickAmountList.joinToString(" ") { "+$it" },
            trailing = {
                Text("${switches.count { it }} из ${switches.size}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        ) { onOpen(SettingsSection.EXPERIMENTS) }
    }
    SettingsGroup("Данные") {
        SettingsRow(Icons.Outlined.Storage, "Резервная копия", "Экспорт и импорт базы, перенос настроек") {
            onOpen(SettingsSection.DATA)
        }
    }
    // Ссылки открываются в браузере: разрешение на интернет приложению не нужно.
    val uriHandler = LocalUriHandler.current
    SettingsGroup("О приложении") {
        SettingsRow(Icons.Outlined.Feedback, "Обратная связь", "Ошибка или идея — разработчику", onClick = onFeedback)
        SettingsRow(Icons.Outlined.FavoriteBorder, "Поддержать разработчика", "По желанию — все функции бесплатны") {
            runCatching { uriHandler.openUri(DONATE_URL) }
        }
        SettingsRow(Icons.Outlined.Code, "Наряд на GitHub", "Исходный код, новости и выпуски") {
            runCatching { uriHandler.openUri(GITHUB_URL) }
        }
        // RuStore требует, чтобы политика была доступна и в самом приложении.
        SettingsRow(Icons.Outlined.PrivacyTip, "Конфиденциальность", "Политика: данные хранятся только на телефоне") {
            runCatching { uriHandler.openUri(PRIVACY_URL) }
        }
    }
    Text(
        "Наряд ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    summary: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // До двух строк: при крупном шрифте «Поддержать разработчика» обрезалось.
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                summary.noOrphans(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private const val GITHUB_URL = "https://github.com/Levug/android-workorder"
private const val PRIVACY_URL = "https://github.com/Levug/android-workorder/blob/main/PRIVACY.md"

/** Добровольная поддержка: ничего не открывает и не продаёт — иначе это была бы оплата в обход RuStore. */
private const val DONATE_URL = "https://pay.cloudtips.ru/p/21a61187"

/** Переключатели раздела «Эксперименты» — для счётчика «N из M» в списке разделов. */
private fun experimentSwitches(settings: Settings) = listOf(
    settings.daySpecializationEnabled, settings.advanceFillEnabled, settings.bankEnabled,
    settings.daySwipeEnabled, settings.calendarHeatEnabled, settings.quickAddEnabled, settings.summaryQuickAddEnabled
)

/** Эксперименты: функции, которые можно выключить, и настройки добавления партий. */
@Composable
private fun ExperimentsSection(
    draft: SettingsForm,
    settings: Settings,
    formEnabled: Boolean,
    enabled: Boolean,
    viewModel: SettingsViewModel
) {
    SectionCard {
        SubTitle("Учёт и расчёты")
        PreferenceSwitch(
            "Банк нормо-часов",
            "Вкладка «Банк» в отчётах и перенос операций между месяцами. Выключено — отчёт по фактическим операциям, распределение сохраняется.",
            settings.bankEnabled, enabled, viewModel::setBankEnabled
        )
        PreferenceSwitch(
            "Аванс с переработками",
            "Если до 15-го пропущены рабочие дни, в аванс идут переработки и выходы в выходные — " +
                "не больше часов рабочих дней по графику за 1–15 число.",
            settings.advanceFillEnabled, enabled, viewModel::setAdvanceFillEnabled
        )
        PreferenceSwitch(
            "Специализация дня",
            "Кнопка в календаре переворачивает дни: часы ↔ специализации. Выключение скрывает подписи, текст сохраняется.",
            settings.daySpecializationEnabled, enabled, viewModel::setDaySpecializationEnabled
        )
    }
    SectionCard {
        SubTitle("Наряд и календарь")
        PreferenceSwitch(
            "Свайп между днями",
            "Листать наряды пальцем влево и вправо. Стрелки вверху работают всегда.",
            settings.daySwipeEnabled, enabled, viewModel::setDaySwipeEnabled
        )
        PreferenceSwitch(
            "Цвета в календаре",
            "Чем больше часов, тем темнее плитка. Выключено — все отмеченные дни одного цвета.",
            settings.calendarHeatEnabled, enabled, viewModel::setCalendarHeatEnabled
        )
        PreferenceSwitch(
            "Быстрые кнопки",
            "Ряд «Быстро добавить» на экране наряда: партия недавней операции одним касанием.",
            settings.quickAddEnabled, enabled, viewModel::setQuickAddEnabled
        )
    }
    SectionCard {
        SubTitle("Добавление партий")
        PreferenceField(draft, SettingsForm.QUICK, "Быстрые партии", viewModel::edit, formEnabled,
            "Через запятую, до 6 чисел: кнопки «+N» в наряде и на часах", KeyboardType.Phone)
        PreferenceSwitch(
            "Кнопка «+N» в сводке",
            if (settings.summaryQuickAddEnabled) "Коснитесь операции в сводке дня — появится кнопка «+N» с последним размером партии"
            else "Касание операции в сводке сразу открывает выбор количества",
            settings.summaryQuickAddEnabled, enabled, viewModel::setSummaryQuickAddEnabled
        )
    }
}

private fun paySummary(settings: Settings): String =
    "${settings.hourlyRate.formatNumber()} ₽/ч · смена ${settings.defaultDayHours.formatNumber()} ч · " +
        if (settings.workSchedule == WorkSchedule.FIVE_TWO) "5/2" else "2/2"

private fun catalogSummary(counts: Pair<Int, Int>?): String {
    if (counts == null) return "Названия, нормы времени, порядок и архив"
    val (active, archived) = counts
    if (active == 0 && archived == 0) return "Пока пусто — добавьте операции"
    val main = "$active ${plural(active, "операция", "операции", "операций")}"
    return if (archived > 0) "$main · $archived в архиве" else main
}

/** 1 операция, 2 операции, 5 операций. */
private fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

private fun watchSummary(wear: WearConnectionState): String = when {
    !wear.enabled -> "Связь выключена"
    wear.serviceUnavailable -> "Сервис Wear OS недоступен"
    wear.watches.isNotEmpty() -> wear.watches.first().let { watch ->
        watch.name + if (watch.appFound) " · «Наряд» ${watch.appVersion ?: "найден"}" else " · нет «Наряда»"
    }
    wear.checking -> "Ищу часы…"
    else -> "Часы не подключены"
}

private fun appearanceSummary(settings: Settings): String {
    val theme = when {
        settings.dynamicColor && supportsDynamicColor -> "Цвета из обоев"
        settings.themePreset == CUSTOM_THEME_PRESET -> "Своя тема"
        else -> themePresets.find { it.name == settings.themePreset }?.displayName ?: "Сапфир"
    }
    val mode = when (settings.themeMode) {
        ThemeMode.SYSTEM -> "как в системе"
        ThemeMode.LIGHT -> "светлая"
        ThemeMode.DARK -> "тёмная"
    }
    return "$theme · $mode · ${(settings.interfaceScale * 100).roundToInt()}%"
}

/** «Оплата и смена»: короткая страница вместо первой трети прежней формы. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaySection(draft: SettingsForm, compTimeBalance: Double?, enabled: Boolean, onEdit: (String, String) -> Unit) {
    SectionCard {
        SubTitle("Ставки")
        PairRow(
            { PreferenceField(draft, SettingsForm.RATE, "Реальная, ₽/ч", onEdit, enabled, modifier = it) },
            { PreferenceField(draft, SettingsForm.CONTRACT, "Договорная, ₽/ч", onEdit, enabled, modifier = it) }
        )
        Text("Договорная — только для аванса".noOrphans(), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    SectionCard {
        SubTitle("Смена и график")
        PairRow(
            { PreferenceField(draft, SettingsForm.HOURS, "Часов в смене", onEdit, enabled, modifier = it) },
            { ScheduleSelector(draft, enabled, onEdit, it) }
        )
        if (draft.schedule == WorkSchedule.TWO_TWO) {
            AnchorDateField(draft, enabled = enabled, onPick = { onEdit(SettingsForm.ANCHOR, it.toString()) })
        }
        SchedulePreview(draft.preview)
    }
    SectionCard {
        SubTitle("Отгулы")
        PreferenceField(
            draft, SettingsForm.COMP, "Баланс до начала учёта, ч", onEdit, enabled,
            compTimeBalance?.let { "Сейчас в балансе ${it.formatNumber()} ч" } ?: ""
        )
    }
}

/** Два поля рядом, пока подписи помещаются; при крупном шрифте на узком экране — друг под другом. */
@Composable
private fun PairRow(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth / LocalDensity.current.fontScale >= 280.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                first(Modifier.weight(1f))
                second(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSelector(draft: SettingsForm, enabled: Boolean, onEdit: (String, String) -> Unit, modifier: Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        WorkSchedule.entries.forEachIndexed { index, schedule ->
            SegmentedButton(
                selected = draft.schedule == schedule,
                onClick = { onEdit(SettingsForm.SCHEDULE, schedule.name) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, WorkSchedule.entries.size),
                icon = {}
            ) { Text(if (schedule == WorkSchedule.FIVE_TWO) "5/2" else "2/2", maxLines = 1) }
        }
    }
}

@Composable
private fun SubTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceSection(settings: Settings, enabled: Boolean, viewModel: SettingsViewModel, onPickColor: () -> Unit) {
    SectionCard {
        SubTitle("Тема")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                ) { Text(when (mode) { ThemeMode.SYSTEM -> "Система"; ThemeMode.LIGHT -> "Светлая"; ThemeMode.DARK -> "Тёмная" }, maxLines = 1) }
            }
        }
        if (supportsDynamicColor) {
            PreferenceSwitch("Цвета из обоев", "Material You (Android 12+)", settings.dynamicColor, enabled, viewModel::setDynamicColor)
        }
        if (!settings.dynamicColor) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(themePresets) { preset ->
                    val selected = settings.themePreset == preset.name
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(enabled = enabled) { viewModel.setThemePreset(preset.name) }
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
                item {
                    val custom = settings.themePreset == CUSTOM_THEME_PRESET
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable(enabled = enabled, onClickLabel = "Выбрать цвет своей темы", onClick = onPickColor)
                    ) {
                        CustomThemeSwatch(settings.customThemeColor.takeIf { custom }, selected = custom)
                        Spacer(Modifier.height(4.dp))
                        Text("Своя", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    SectionCard {
        var scale by rememberSaveable(settings.interfaceScale) { mutableStateOf((settings.interfaceScale * 100).toFloat()) }
        SubTitle("Масштаб интерфейса: ${scale.roundToInt()}%")
        Text("Размер текста, кнопок и карточек. Системный размер шрифта тоже учитывается.".noOrphans(),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = scale,
            onValueChange = { scale = it },
            onValueChangeFinished = { viewModel.setInterfaceScale((scale / 5).roundToInt() * 5 / 100.0) },
            valueRange = 85f..130f, steps = 8, enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("85%", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { scale = 100f; viewModel.setInterfaceScale(1.0) }, enabled = enabled) { Text("Сбросить до 100%", maxLines = 1) }
            Spacer(Modifier.weight(1f))
            Text("130%", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PreferenceField(
    draft: SettingsForm, field: String, label: String, onEdit: (String, String) -> Unit,
    enabled: Boolean, hint: String = "", keyboard: KeyboardType = KeyboardType.Decimal,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val error = draft.error(field)
    OutlinedTextField(
        value = draft.text(field), onValueChange = { onEdit(field, it) },
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }, enabled = enabled,
        isError = error != null, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        supportingText = if (error != null || hint.isNotEmpty()) {{ Text((error ?: hint).noOrphans()) }} else null,
        modifier = modifier
    )
}

/** Дата начала цикла 2/2 выбирается в календаре, а не вводится вручную. */
@Composable
private fun AnchorDateField(draft: SettingsForm, enabled: Boolean, onPick: (LocalDate) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val error = draft.error(SettingsForm.ANCHOR)
    Box {
        OutlinedTextField(
            value = draft.anchorDate?.format(DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Locale("ru")))
                ?: draft.text(SettingsForm.ANCHOR),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text("Первая рабочая смена цикла 2/2") },
            trailingIcon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
            isError = error != null,
            supportingText = { Text(error ?: "Дата сдвигает весь цикл") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // Поле только для чтения не реагирует на касание — ловим его поверх.
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = 20.dp)
                .clickable(enabled = enabled, onClickLabel = "Выбрать дату") { picking = true }
        )
    }
    if (picking) {
        DateSelectDialog(
            initial = draft.anchorDate ?: LocalDate.now(),
            onPick = { picking = false; onPick(it) },
            onDismiss = { picking = false },
            confirmText = "Выбрать"
        )
    }
}

/** Две недели вперёд по графику из формы — видно, правильно ли лёг цикл, ещё до сохранения. */
@Composable
private fun SchedulePreview(settings: com.workorder.app.data.model.Settings) {
    val today = LocalDate.now()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ближайшие две недели", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        (0L until 14L).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { offset ->
                    val date = today.plusDays(offset)
                    val work = SalaryCalculator.isScheduledWorkDay(date, settings)
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(if (work) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale("ru")).replaceFirstChar { it.titlecase() },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (work) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (work) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Text("Цветом выделены рабочие смены", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title.noOrphans(), style = MaterialTheme.typography.bodyLarge)
            Text(description.noOrphans(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange, enabled = enabled)
    }
}
