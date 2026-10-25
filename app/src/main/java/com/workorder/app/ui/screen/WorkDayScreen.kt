package com.workorder.app.ui.screen

import com.workorder.app.data.model.allowsPartialCompOff
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.workorder.app.data.model.MANUAL_MULTIPLIER_RANGE
import com.workorder.app.data.model.DEFAULT_MANUAL_MULTIPLIER
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.automirrored.outlined.Comment
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workorder.app.data.dao.EntryWithOperation
import com.workorder.app.data.dao.OperationTotal
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.ui.component.AnimatedValue
import com.workorder.app.ui.component.DateSelectDialog
import com.workorder.app.ui.component.EmptyState
import com.workorder.app.ui.component.LoadingBox
import com.workorder.app.ui.component.OperationPickerSheet
import com.workorder.app.ui.component.QuantitySheet
import com.workorder.app.ui.component.QuickChip
import com.workorder.app.ui.component.SectionCard
import com.workorder.app.ui.viewmodel.BatchAddedEvent
import com.workorder.app.ui.viewmodel.QuickAction
import com.workorder.app.ui.viewmodel.SetupState
import com.workorder.app.ui.viewmodel.WorkDayUiState
import com.workorder.app.ui.viewmodel.WorkDayViewModel
import com.workorder.app.util.formatDayTitle
import com.workorder.app.util.formatFull
import com.workorder.app.util.formatNumber
import com.workorder.app.util.formatSigned
import com.workorder.app.util.formatTime
import com.workorder.app.util.noOrphans
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

private enum class DayTab { SUMMARY, JOURNAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDayScreen(
    viewModel: WorkDayViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToDate: ((LocalDate) -> Unit)? = null,
    onOpenCatalog: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val batchAdded by viewModel.batchAdded.collectAsStateWithLifecycle()
    val entryRemoved by viewModel.entryRemoved.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(DayTab.SUMMARY) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var quantityForId by rememberSaveable { mutableStateOf<Long?>(null) }
    var lastBatch by remember { mutableStateOf<BatchAddedEvent?>(null) }
    var showHoursDialog by rememberSaveable { mutableStateOf(false) }
    var showManualDialog by rememberSaveable { mutableStateOf(false) }
    var showDayTypeSheet by rememberSaveable { mutableStateOf(false) }
    var expandedOperationId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showCommentDialog by rememberSaveable { mutableStateOf(false) }
    var showSpecializationDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    val error by viewModel.error.collectAsStateWithLifecycle()
    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it); viewModel.consumeError() }
    }

    // Снекбар показывается в отдельной корутине: сброс события не должен его отменять.
    LaunchedEffect(batchAdded) {
        val event = batchAdded ?: return@LaunchedEffect
        viewModel.consumeBatchAddedEvent()
        lastBatch = event
        view.confirmHaptic()
        if (quantityForId != null) return@LaunchedEffect // подтверждение уже видно в шторке
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "${event.operationName}: ${event.quantity.formatSigned()} шт",
                actionLabel = "Отменить",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoBatch(event.entryId)
                lastBatch = null
            }
        }
    }

    LaunchedEffect(entryRemoved) {
        val event = entryRemoved ?: return@LaunchedEffect
        viewModel.consumeEntryRemovedEvent()
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Удалено: ${event.operationName} ${event.entry.quantity.formatSigned()} шт",
                actionLabel = "Вернуть",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreEntry(event)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column(
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(enabled = onNavigateToDate != null, onClickLabel = "Выбрать дату") { showDatePicker = true }
                            .padding(horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (uiState.isToday) "Сегодня" else "Наряд")
                            if (onNavigateToDate != null) Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        Text(
                            text = uiState.date.formatDayTitle(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    if (onNavigateToDate != null) {
                        IconButton(onClick = { onNavigateToDate(uiState.date.minusDays(1)) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий день")
                        }
                        IconButton(onClick = { onNavigateToDate(uiState.date.plusDays(1)) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий день")
                        }
                    }
                    if (uiState.dayExists) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Удалить наряд") },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    },
                                    onClick = { showMenu = false; showDeleteDialog = true }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showPicker = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Добавить") }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            LoadingBox(Modifier.padding(padding))
            return@Scaffold
        }
        val swipe = if (onNavigateToDate != null && uiState.daySwipeEnabled) Modifier.pointerInput(uiState.date) {
            val threshold = 96.dp.toPx()
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = {
                    if (total > threshold) onNavigateToDate(uiState.date.minusDays(1))
                    else if (total < -threshold) onNavigateToDate(uiState.date.plusDays(1))
                },
                onHorizontalDrag = { change, amount -> total += amount; change.consume() }
            )
        } else Modifier

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).then(swipe),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.setup?.let { setup ->
                item(key = "setup") {
                    SetupCard(
                        setup = setup,
                        onOpenSettings = onOpenSettings,
                        onOpenCatalog = onOpenCatalog,
                        onHide = viewModel::hideSetup
                    )
                }
            }

            item(key = "hero") {
                DayHeroCard(
                    state = uiState,
                    onHoursClick = {
                        if (uiState.dayType == WorkDayType.MANUAL) showManualDialog = true else showHoursDialog = true
                    },
                    onDayTypeClick = { showDayTypeSheet = true },
                    onCommentClick = { showCommentDialog = true },
                    onSpecializationClick = { showSpecializationDialog = true }
                )
            }

            if (uiState.quickAddEnabled && uiState.recent.isNotEmpty()) {
                item(key = "quick") {
                    QuickAddRow(
                        recent = uiState.recent,
                        showHint = !uiState.quickHintSeen,
                        onAdd = { viewModel.addBatch(it.operation.id, it.quantity) },
                        onChoose = {
                            viewModel.markQuickHintSeen()
                            quantityForId = it.operation.id
                        }
                    )
                }
            }

            item(key = "tabs") {
                OperationsHeader(
                    selected = selectedTab,
                    units = uiState.totalUnits,
                    journalCount = uiState.entries.size,
                    onSelect = { selectedTab = it }
                )
            }

            when (selectedTab) {
                DayTab.SUMMARY -> if (uiState.totals.isEmpty()) {
                    item(key = "summary-empty") {
                        EmptyState(
                            icon = Icons.AutoMirrored.Outlined.PlaylistAdd,
                            title = "Пока пусто",
                            subtitle = "Нажмите «Добавить», выберите операцию и внесите первую партию"
                        )
                    }
                } else {
                    // Один список вместо карточки на каждую операцию.
                    item(key = "summary") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            uiState.totals.forEachIndexed { index, total ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                val active = operations.any { it.id == total.operationId }
                                val quantity = uiState.lastQuantities[total.operationId] ?: uiState.quickAmounts.first()
                                val id = total.operationId
                                SummaryRow(
                                    total = total,
                                    quickQuantity = quantity.takeIf { active && uiState.summaryQuickAddEnabled },
                                    expanded = expandedOperationId == id,
                                    onToggle = { expandedOperationId = if (expandedOperationId == id) null else id },
                                    onOpen = { quantityForId = id },
                                    onQuickAdd = { viewModel.addBatch(id, quantity) }
                                )
                            }
                        }
                    }
                }

                DayTab.JOURNAL -> if (uiState.entries.isEmpty()) {
                    item(key = "journal-empty") {
                        EmptyState(
                            icon = Icons.Outlined.History,
                            title = "Журнал пуст",
                            subtitle = "Здесь будет видно, в какое время и какими партиями добавлялись операции"
                        )
                    }
                } else {
                    item(key = "journal") {
                        JournalCard(entries = uiState.entries, onDelete = viewModel::removeEntry)
                    }
                }
            }
        }
    }

    if (showPicker) {
        OperationPickerSheet(
            operations = operations,
            onPick = { operation ->
                showPicker = false
                quantityForId = operation.id
            },
            onDismiss = { showPicker = false },
            onOpenCatalog = onOpenCatalog?.let { open -> { showPicker = false; open() } }
        )
    }

    quantityForId?.let { id ->
        // Архивная операция уже не в каталоге выбора, но её количество за день ещё можно поправить.
        val operation = operations.find { it.id == id }
            ?: uiState.totals.find { it.operationId == id }?.let { Operation(it.operationId, it.name, it.durationHours, it.grade) }
        if (operation == null) {
            SideEffect { quantityForId = null }
        } else {
            QuantitySheet(
                operation = operation,
                currentCount = uiState.totals.find { it.operationId == id }?.totalCount ?: 0,
                quickAmounts = uiState.quickAmounts,
                lastAdded = lastBatch,
                onAdd = { amount -> viewModel.addBatch(operation.id, amount) },
                onUndo = { event -> viewModel.undoBatch(event.entryId); lastBatch = null },
                onDismiss = { quantityForId = null; lastBatch = null }
            )
        }
    }

    if (showHoursDialog) {
        HoursDialog(
            initialHours = if (uiState.isRecorded) uiState.dayHours else uiState.defaultDayHours,
            defaultHours = uiState.defaultDayHours,
            compOffAllowed = uiState.dayType.allowsPartialCompOff,
            initialCompOff = uiState.compOffHours,
            compTimeBalance = uiState.compTimeBalance,
            onConfirm = { hours, compOff ->
                viewModel.setHours(hours, compOff)
                showHoursDialog = false
            },
            onDismiss = { showHoursDialog = false }
        )
    }

    if (showDayTypeSheet) {
        DayTypeSheet(
            state = uiState,
            onPick = { type ->
                showDayTypeSheet = false
                when {
                    // Ручной режим сохраняется вместе с часами, поэтому сначала открывается диалог.
                    type == WorkDayType.MANUAL -> showManualDialog = true
                    type != uiState.dayType -> viewModel.setDayType(type)
                }
            },
            onDismiss = { showDayTypeSheet = false }
        )
    }

    if (showManualDialog) {
        val hours = if (uiState.isRecorded) uiState.dayHours else uiState.defaultDayHours
        val offDay = uiState.dayType == WorkDayType.HOLIDAY ||
            (uiState.dayType == WorkDayType.AUTO && uiState.scheduledOff)
        // Подсказка для нового ручного дня: как посчитал бы обычный режим без учёта нормы месяца.
        val initialSingle = when {
            uiState.dayType == WorkDayType.MANUAL -> uiState.manualSingleHours
            offDay -> 0.0
            else -> minOf(hours, uiState.defaultDayHours)
        }
        val initialDouble = if (uiState.dayType == WorkDayType.MANUAL) uiState.manualDoubleHours else hours - initialSingle
        ManualHoursDialog(
            initialSingle = initialSingle,
            initialDouble = initialDouble,
            initialMultiplier = if (uiState.dayType == WorkDayType.MANUAL) uiState.manualMultiplier else DEFAULT_MANUAL_MULTIPLIER,
            onConfirm = { single, double, multiplier ->
                viewModel.setManualHours(single, double, multiplier)
                showManualDialog = false
            },
            onDismiss = { showManualDialog = false }
        )
    }

    if (showCommentDialog) {
        CommentDialog(
            initialComment = uiState.comment,
            onConfirm = { comment ->
                viewModel.setComment(comment)
                showCommentDialog = false
            },
            onDismiss = { showCommentDialog = false }
        )
    }

    if (showSpecializationDialog && uiState.specializationEnabled) {
        var text by rememberSaveable { mutableStateOf(uiState.specialization) }
        AlertDialog(
            onDismissRequest = { showSpecializationDialog = false },
            title = { Text("Специализация дня") },
            text = {
                OutlinedTextField(text, { if (it.length <= 120) text = it },
                    label = { Text("Например: сборка, наладка") },
                    supportingText = { Text("${text.length}/120 · Пустое поле удалит подпись. Специализация не добавляет часов и не отмечает смену.") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
            },
            confirmButton = { TextButton(onClick = { viewModel.setSpecialization(text) { showSpecializationDialog = false } }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { showSpecializationDialog = false }) { Text("Отмена") } }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Удалить наряд?") },
            text = { Text("Наряд на ${uiState.date.formatFull()} и весь его журнал будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteDay { onNavigateBack?.invoke() }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") } }
        )
    }

    if (showDatePicker && onNavigateToDate != null) {
        DateSelectDialog(
            initial = uiState.date,
            onPick = { picked ->
                showDatePicker = false
                if (picked != uiState.date) onNavigateToDate(picked)
            },
            onDismiss = { showDatePicker = false }
        )
    }
}

private fun View.confirmHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.KEYBOARD_TAP
    )
}

@Composable
private fun SetupCard(
    setup: SetupState,
    onOpenSettings: (() -> Unit)?,
    onOpenCatalog: (() -> Unit)?,
    onHide: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Первая настройка", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onHide) { Text("Скрыть") }
            }
            SetupStep("Ставка, часы смены и график", setup.rateDone, onOpenSettings)
            SetupStep("Операции с нормами времени", setup.operationsDone, onOpenCatalog)
            Text(
                "Уже вели учёт на другом телефоне? Импортируйте резервную копию в настройках.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp, end = 8.dp)
            )
        }
    }
}

@Composable
private fun SetupStep(title: String, done: Boolean, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = onClick != null && !done) { onClick?.invoke() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = if (done) "Готово" else "Не сделано",
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (!done && onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayHeroCard(
    state: WorkDayUiState,
    onHoursClick: () -> Unit,
    onDayTypeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onSpecializationClick: () -> Unit
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = onContainer
        )
    ) {
        Column(
            Modifier.padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Три цифры дня в одну строку вместо карточки на пол-экрана.
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                HeroMetric(
                    value = if (state.isRecorded) state.dayHours.formatNumber() else "—",
                    label = hoursLabel(state),
                    editable = true,
                    // Отступ внутри области касания: скруглённый угол больше не срезает текст.
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .clickable(onClickLabel = "Изменить часы", onClick = onHoursClick)
                        .padding(4.dp)
                )
                MetricDivider(onContainer)
                HeroMetric(state.producedHours.formatNumber(), "нормо-ч", modifier = Modifier.weight(1f).padding(start = 12.dp, top = 4.dp))
                MetricDivider(onContainer)
                HeroMetric(
                    value = if (state.isRecorded && state.dayHours > 0) "${(state.producedHours / state.dayHours * 100).roundToInt()}%" else "—",
                    label = "выработка",
                    modifier = Modifier.weight(1f).padding(start = 12.dp, top = 4.dp)
                )
            }
            FlowRow(Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroChip(
                    icon = Icons.Outlined.Event,
                    label = dayTypeLabel(state),
                    trailingDropDown = true,
                    description = "Тип дня: ${dayTypeLabel(state)}",
                    onClick = onDayTypeClick
                )
                HeroChip(
                    icon = Icons.AutoMirrored.Outlined.Comment,
                    label = state.comment.ifBlank { "Заметка" },
                    description = if (state.comment.isBlank()) "Добавить заметку" else "Заметка: ${state.comment}",
                    onClick = onCommentClick
                )
                if (state.specializationEnabled) HeroChip(
                    icon = if (state.specialization.isBlank()) Icons.Filled.Add else Icons.Outlined.Handyman,
                    label = state.specialization.ifBlank { "Специализация" },
                    description = if (state.specialization.isBlank()) "Добавить специализацию" else "Специализация: ${state.specialization}",
                    onClick = onSpecializationClick
                )
            }
            Box(Modifier.padding(start = 4.dp)) { CompTimeBalance(state) }
        }
    }
}

@Composable
private fun HeroMetric(value: String, label: String, modifier: Modifier = Modifier, editable: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedValue(text = value, style = MaterialTheme.typography.headlineSmall)
            if (editable) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        // До двух строк с переносом по пробелу: в трети ширины «указать часы» обрезалось.
        Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MetricDivider(color: Color) {
    Box(Modifier.width(1.dp).fillMaxHeight().background(color.copy(alpha = 0.18f)))
}

@Composable
private fun HeroChip(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    trailingDropDown: Boolean = false
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    AssistChip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        trailingIcon = if (trailingDropDown) {{ Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }} else null,
        colors = AssistChipDefaults.assistChipColors(
            labelColor = onContainer,
            leadingIconContentColor = onContainer,
            trailingIconContentColor = onContainer
        ),
        modifier = Modifier.semantics { contentDescription = description }
    )
}

private fun hoursLabel(state: WorkDayUiState): String = when {
    !state.isRecorded && state.dayType == WorkDayType.UNPAID -> "день БС"
    !state.isRecorded -> "указать часы"
    state.compOffHours > 0 -> "часов + отгул ${state.compOffHours.formatNumber()} ч"
    state.dayType == WorkDayType.MANUAL -> "ручной режим"
    state.dayType == WorkDayType.COMP_WORK -> "в баланс"
    state.dayType == WorkDayType.COMP_OFF -> "отгул"
    else -> "часов"
}

private fun dayTypeLabel(state: WorkDayUiState): String = when (state.dayType) {
    WorkDayType.AUTO -> if (state.scheduledOff) "По графику · выходной ×2" else "По графику · рабочий"
    WorkDayType.REGULAR -> "Рабочий день"
    WorkDayType.HOLIDAY -> "Праздник ×2"
    WorkDayType.COMP_WORK -> "Выход за отгул"
    WorkDayType.COMP_OFF -> "Отгул"
    WorkDayType.MANUAL -> "Ручной режим"
    WorkDayType.UNPAID -> "БС · без содержания"
}

private data class DayTypeOption(val type: WorkDayType, val title: String, val description: String, val icon: ImageVector)

/**
 * Шторка вместо длинного выпадающего меню: шесть плиток в две строки.
 * Отгулы и ручной режим — отдельной группой, баланс отгулов — на плитке «Отгул».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayTypeSheet(state: WorkDayUiState, onPick: (WorkDayType) -> Unit, onDismiss: () -> Unit) {
    val regular = listOf(
        DayTypeOption(WorkDayType.AUTO, "По графику", "5/2 или 2/2", Icons.Outlined.Event),
        DayTypeOption(WorkDayType.REGULAR, "Рабочий", "×1 и в выходной", Icons.Outlined.WorkOutline),
        DayTypeOption(WorkDayType.HOLIDAY, "Праздник", "×2 в любой день", Icons.Outlined.StarOutline)
    )
    val special = listOf(
        DayTypeOption(WorkDayType.COMP_WORK, "Выход за отгул", "часы — в баланс", Icons.Outlined.MoreTime),
        DayTypeOption(WorkDayType.COMP_OFF, "Отгул", "есть ${state.compTimeBalance.formatNumber()} ч", Icons.Outlined.WbSunny),
        DayTypeOption(WorkDayType.UNPAID, "БС", "без содержания, не в норме", Icons.Outlined.EventBusy),
        DayTypeOption(WorkDayType.MANUAL, "Ручной", "свои часы и коэффициент", Icons.Outlined.Edit)
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Тип дня", style = MaterialTheme.typography.titleLarge)
                Text(
                    ((if (state.scheduledOff) "По графику в этот день выходной." else "По графику в этот день рабочий.") +
                        " Выбор сохраняется сразу.").noOrphans(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DayTypeGroup("Оплата", regular, state.dayType, onPick)
            DayTypeGroup("Отгулы и особые дни", special, state.dayType, onPick)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    ("Отгул на весь день отмечает смену на ${state.defaultDayHours.formatNumber()} ч и списывает их из баланса. " +
                        "Отгул на часть дня — в окне часов: «отработано» плюс «отгул».").noOrphans(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DayTypeGroup(title: String, options: List<DayTypeOption>, selected: WorkDayType, onPick: (WorkDayType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Три плитки в ряд — только когда в плитку влезает самое длинное слово, иначе две:
            // при крупном шрифте на узком экране «По графику» и «Праздник» рвались посередине.
            val columns = if (maxWidth / LocalDensity.current.fontScale >= 300.dp) 3 else 2
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.chunked(columns).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { option ->
                            DayTypeTile(option, option.type == selected, { onPick(option.type) }, Modifier.weight(1f).fillMaxHeight())
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTypeTile(option: DayTypeOption, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.onPrimaryContainer else colors.onSurface
    Box(
        modifier
            .heightIn(min = 104.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.primaryContainer else colors.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.primary else colors.outlineVariant,
                shape = MaterialTheme.shapes.medium
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(option.icon, contentDescription = null, tint = if (selected) content else colors.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Text(option.title.noOrphans(), style = MaterialTheme.typography.titleSmall, color = content)
            Text(option.description.noOrphans(), style = MaterialTheme.typography.bodySmall,
                color = if (selected) content else colors.onSurfaceVariant)
        }
        if (selected) Icon(
            Icons.Filled.Check,
            contentDescription = "Выбрано",
            tint = content,
            modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
        )
    }
}

/** Баланс отгулов в карточке дня — только у дней, связанных с отгулами; остальное время он на плитке «Отгул». */
@Composable
private fun CompTimeBalance(state: WorkDayUiState) {
    val related = state.dayType == WorkDayType.COMP_WORK || state.dayType == WorkDayType.COMP_OFF || state.compOffHours > 0
    if (!related) return
    val change = when {
        !state.isRecorded -> ""
        state.dayType == WorkDayType.COMP_WORK -> " · этот день +${state.dayHours.formatNumber()} ч"
        state.dayType == WorkDayType.COMP_OFF -> " · этот день −${state.dayHours.formatNumber()} ч"
        else -> " · этот день −${state.compOffHours.formatNumber()} ч"
    }
    Text(
        "Баланс отгулов: ${state.compTimeBalance.formatNumber()} ч$change".noOrphans(),
        style = MaterialTheme.typography.bodySmall,
        color = if (state.compTimeBalance < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.semantics {
            contentDescription = "Баланс отгулов ${state.compTimeBalance.formatNumber()} часов"
        }
    )
}

@Composable
private fun QuickAddRow(
    recent: List<QuickAction>,
    showHint: Boolean,
    onAdd: (QuickAction) -> Unit,
    onChoose: (QuickAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Быстро добавить", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(recent.take(6), key = { _, action -> action.operation.id }) { index, action ->
                val first = index == 0
                QuickChip(
                    text = "${action.operation.name} +${action.quantity}",
                    emphasized = first,
                    icon = if (first) Icons.Filled.Replay else null,
                    onClick = { onAdd(action) },
                    onLongClick = { onChoose(action) },
                    modifier = Modifier.widthIn(max = 260.dp)
                )
            }
        }
        // Подсказка — пока ни разу не удерживали кнопку.
        if (showHint) Text(
            "Удерживайте, чтобы выбрать количество".noOrphans(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OperationsHeader(selected: DayTab, units: Int, journalCount: Int, onSelect: (DayTab) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            Text("Операции", style = MaterialTheme.typography.titleMedium, maxLines = 1)
            if (units > 0) Text(
                " · $units шт",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(3.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            PillTab("Сводка", selected == DayTab.SUMMARY) { onSelect(DayTab.SUMMARY) }
            PillTab("Журнал · $journalCount", selected == DayTab.JOURNAL) { onSelect(DayTab.JOURNAL) }
        }
    }
}

@Composable
private fun PillTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(19.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .heightIn(min = 38.dp)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
private fun SummaryRow(
    total: OperationTotal,
    quickQuantity: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onQuickAdd: () -> Unit
) {
    // Быстрое добавление спрятано за касанием: случайное нажатие в списке не добавляет партию.
    val quick = quickQuantity != null
    val open = quick && expanded
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (open) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = if (quick) (if (expanded) "Скрыть быстрое добавление" else "Показать быстрое добавление")
                    else "Выбрать количество",
                    onClick = if (quick) onToggle else onOpen
                )
                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = if (open) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = total.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${total.durationHours.formatNumber()} нч · всего ${total.totalHours.formatNumber()} нч".noOrphans(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            AnimatedValue(
                text = total.totalCount.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (quick) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        AnimatedVisibility(visible = open) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Короткие подписи в одну строку: «Количество…» переносилось по буквам при крупном шрифте.
                Button(
                    onClick = onQuickAdd,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f).semantics {
                        contentDescription = "Добавить $quickQuantity шт: ${total.name}"
                    }
                ) { Text("+$quickQuantity", maxLines = 1) }
                OutlinedButton(
                    onClick = onOpen,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("Указать", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun JournalCard(
    entries: List<EntryWithOperation>,
    onDelete: (EntryWithOperation) -> Unit
) {
    SectionCard {
        entries.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(entry.createdAt),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(52.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = entry.operationName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "${entry.quantity.formatSigned()} шт · ${entry.hours.formatNumber()} нч",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (entry.quantity < 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(entry) }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Удалить запись",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun Double.toInput(): String =
    if (this % 1.0 == 0.0) toLong().toString() else formatNumber()

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HoursDialog(
    initialHours: Double,
    defaultHours: Double,
    compOffAllowed: Boolean,
    initialCompOff: Double,
    compTimeBalance: Double,
    onConfirm: (hours: Double, compOff: Double?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initialHours.toInput()) }
    var compText by rememberSaveable { mutableStateOf(initialCompOff.toInput()) }
    val parsed = text.replace(',', '.').filterNot(Char::isWhitespace).toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..24.0 }
    val comp = if (compOffAllowed) compText.replace(',', '.').filterNot(Char::isWhitespace).toDoubleOrNull()
        ?.takeIf { it.isFinite() && it in 0.0..24.0 } else 0.0
    val valid = parsed != null && comp != null && parsed + comp <= 24.0
    fun step(delta: Double) {
        text = ((parsed ?: initialHours) + delta).coerceIn(0.0, 24.0).toInput()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Часы на смене") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (compOffAllowed) Text("Отработано", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { step(-0.5) }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Минус полчаса")
                    }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = parsed == null,
                        suffix = { Text("ч") },
                        textStyle = TextStyle.Default.merge(MaterialTheme.typography.headlineSmall).copy(textAlign = TextAlign.Center),
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                    )
                    FilledTonalIconButton(onClick = { step(0.5) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Плюс полчаса")
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(defaultHours, 8.0, 11.0, 12.0).distinct().sorted().forEach { hours ->
                        FilterChip(
                            selected = parsed == hours,
                            onClick = { text = hours.toInput() },
                            label = { Text("${hours.formatNumber()} ч") }
                        )
                    }
                }
                if (compOffAllowed) {
                    // Отгул на часть дня: например, отработано 4 ч и ещё 4 ч — из баланса отгулов.
                    ManualNumberField("Отгул к этому дню", compText, comp != null, 0.5, 0.0..24.0, "ч") { compText = it }
                    val rest = (defaultHours - (parsed ?: 0.0)).coerceAtLeast(0.0)
                    if (rest > 0 && rest != comp) FilterChip(
                        selected = false,
                        onClick = { compText = rest.toInput() },
                        label = { Text("До смены: ${rest.formatNumber()} ч") }
                    )
                }
                Text(
                    when {
                        !valid && parsed != null && comp != null -> "Всего ${(parsed + comp).formatNumber()} ч — в сутках не больше 24"
                        compOffAllowed -> ("Отгул оплачивается как обычные часы и списывается из баланса " +
                            "(сейчас ${compTimeBalance.formatNumber()} ч). 0 ч отработано — смена без присутствия.")
                        else -> "0 ч — смена отмечена без часов присутствия"
                    }.noOrphans(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid || parsed == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (valid) onConfirm(parsed!!, comp.takeIf { compOffAllowed }) },
                enabled = valid
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
internal fun ManualHoursDialog(
    initialSingle: Double,
    initialDouble: Double,
    initialMultiplier: Double,
    onConfirm: (single: Double, double: Double, multiplier: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var singleText by rememberSaveable { mutableStateOf(initialSingle.toInput()) }
    var doubleText by rememberSaveable { mutableStateOf(initialDouble.toInput()) }
    var multiplierText by rememberSaveable { mutableStateOf(initialMultiplier.toInput()) }
    fun parse(text: String, range: ClosedFloatingPointRange<Double>) =
        text.replace(',', '.').filterNot(Char::isWhitespace).toDoubleOrNull()?.takeIf { it.isFinite() && it in range }
    val single = parse(singleText, 0.0..24.0)
    val double = parse(doubleText, 0.0..24.0)
    val multiplier = parse(multiplierText, MANUAL_MULTIPLIER_RANGE)
    val total = if (single != null && double != null) single + double else null
    val valid = total != null && total <= 24.0 && multiplier != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ручной режим") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ManualNumberField("По обычной ставке ×1", singleText, single != null, 0.5, 0.0..24.0, "ч") { singleText = it }
                ManualNumberField(
                    "По повышенной ставке ×${multiplier?.formatNumber() ?: "?"}", doubleText, double != null, 0.5, 0.0..24.0, "ч"
                ) { doubleText = it }
                ManualNumberField("Коэффициент", multiplierText, multiplier != null, 0.25, MANUAL_MULTIPLIER_RANGE, "×") {
                    multiplierText = it
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1.5, 2.0, 3.0).forEach { option ->
                        FilterChip(
                            selected = multiplier == option,
                            onClick = { multiplierText = option.toInput() },
                            label = { Text("×${option.formatNumber()}") }
                        )
                    }
                }
                Text(
                    when {
                        total == null -> "Введите часы от 0 до 24"
                        multiplier == null -> "Коэффициент — от 1 до 10"
                        !valid -> "Всего ${total.formatNumber()} ч — в сутках не больше 24"
                        else -> "Всего ${total.formatNumber()} ч, к оплате ${(single!! + double!! * multiplier).formatNumber()} ч по ставке. " +
                            "График и норма не влияют."
                    }.noOrphans(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onConfirm(single!!, double!!, multiplier!!) }, enabled = valid) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun ManualNumberField(
    label: String,
    text: String,
    valid: Boolean,
    step: Double,
    range: ClosedFloatingPointRange<Double>,
    suffix: String,
    onChange: (String) -> Unit
) {
    val value = text.replace(',', '.').toDoubleOrNull() ?: range.start
    // Подпись над полем: внутри узкого поля длинная подпись переносилась и наезжала на рамку.
    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = { onChange((value - step).coerceIn(range).toInput()) }) {
            Icon(Icons.Filled.Remove, contentDescription = "$label: уменьшить")
        }
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            isError = !valid,
            suffix = { Text(suffix) },
            textStyle = TextStyle.Default.merge(MaterialTheme.typography.titleLarge).copy(textAlign = TextAlign.Center),
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        FilledTonalIconButton(onClick = { onChange((value + step).coerceIn(range).toInput()) }) {
            Icon(Icons.Filled.Add, contentDescription = "$label: увеличить")
        }
    }
}

@Composable
private fun CommentDialog(
    initialComment: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initialComment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Комментарий") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Заметка к наряду") },
                supportingText = { Text("Заметка на пустом дне не отмечает смену") },
                minLines = 2,
                maxLines = 5
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
