package com.workorder.app.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workorder.app.data.dao.EntryWithOperation
import com.workorder.app.data.dao.OperationTotal
import com.workorder.app.data.model.Operation
import com.workorder.app.data.model.WorkDayType
import com.workorder.app.ui.component.EmptyState
import com.workorder.app.ui.component.OperationPickerSheet
import com.workorder.app.ui.component.QuantitySheet
import com.workorder.app.ui.component.SectionCard
import com.workorder.app.ui.component.StatCell
import com.workorder.app.ui.viewmodel.WorkDayViewModel
import com.workorder.app.util.formatFull
import com.workorder.app.util.formatNumber
import com.workorder.app.util.formatSigned
import com.workorder.app.util.formatTime

private enum class DayTab { SUMMARY, JOURNAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkDayScreen(
    viewModel: WorkDayViewModel,
    onNavigateBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val batchAdded by viewModel.batchAdded.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(DayTab.SUMMARY) }
    var showPicker by remember { mutableStateOf(false) }
    var quantityFor by remember { mutableStateOf<Operation?>(null) }
    var showHoursDialog by remember { mutableStateOf(false) }
    var showCommentDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(batchAdded) {
        val event = batchAdded ?: return@LaunchedEffect
        viewModel.consumeBatchAddedEvent()
        val result = snackbarHostState.showSnackbar(
            message = "${event.operationName}: ${event.quantity.formatSigned()}",
            actionLabel = "Отменить",
            withDismissAction = true
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoBatch(event.entryId)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (uiState.isToday) "Сегодня" else "Наряд")
                        Text(
                            text = uiState.date.formatFull(),
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
                    if (uiState.dayExists) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "Удалить наряд",
                                tint = MaterialTheme.colorScheme.error
                            )
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DayHeaderCard(
                    hours = uiState.dayHours,
                    units = uiState.totalUnits,
                    producedHours = uiState.producedHours,
                    comment = uiState.comment,
                    dayType = uiState.dayType,
                    onHoursClick = { showHoursDialog = true },
                    onCommentClick = { showCommentDialog = true },
                    onDayTypeChange = viewModel::setDayType
                )

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedTab == DayTab.SUMMARY,
                        onClick = { selectedTab = DayTab.SUMMARY },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) {
                        Text("Сводка")
                    }
                    SegmentedButton(
                        selected = selectedTab == DayTab.JOURNAL,
                        onClick = { selectedTab = DayTab.JOURNAL },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) {
                        Text("Журнал (${uiState.entries.size})")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when (selectedTab) {
                DayTab.SUMMARY -> SummaryTab(
                    totals = uiState.totals,
                    onRowClick = { total ->
                        operations.find { it.id == total.operationId }?.let { quantityFor = it }
                    }
                )

                DayTab.JOURNAL -> JournalTab(
                    entries = uiState.entries,
                    onDelete = { viewModel.removeEntry(it.id) }
                )
            }
        }
    }

    if (showPicker) {
        OperationPickerSheet(
            operations = operations,
            onPick = { operation ->
                showPicker = false
                quantityFor = operation
            },
            onDismiss = { showPicker = false }
        )
    }

    quantityFor?.let { operation ->
        val currentCount = uiState.totals.find { it.operationId == operation.id }?.totalCount ?: 0
        QuantitySheet(
            operation = operation,
            currentCount = currentCount,
            onAdd = { amount -> viewModel.addBatch(operation.id, amount) },
            onDismiss = { quantityFor = null }
        )
    }

    if (showHoursDialog) {
        HoursDialog(
            initialHours = uiState.dayHours,
            onConfirm = { hours ->
                viewModel.setHours(hours)
                showHoursDialog = false
            },
            onDismiss = { showHoursDialog = false }
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
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun DayHeaderCard(
    hours: Double,
    units: Int,
    producedHours: Double,
    comment: String,
    dayType: WorkDayType,
    onHoursClick: () -> Unit,
    onCommentClick: () -> Unit,
    onDayTypeChange: (WorkDayType) -> Unit
) {
    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f).clickable(onClick = onHoursClick)) {
                StatCell(
                    value = hours.formatNumber(),
                    label = "часов ✎",
                    valueColor = MaterialTheme.colorScheme.primary
                )
            }
            Row(modifier = Modifier.weight(1f)) {
                StatCell(value = units.toString(), label = "штук")
            }
            Row(modifier = Modifier.weight(1f)) {
                StatCell(value = producedHours.formatNumber(), label = "нормо-часов")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                WorkDayType.AUTO to "По графику",
                WorkDayType.REGULAR to "Рабочий",
                WorkDayType.HOLIDAY to "Праздник ×2"
            ).forEach { (type, label) ->
                FilterChip(
                    selected = dayType == type,
                    onClick = { onDayTypeChange(type) },
                    label = { Text(label) }
                )
            }
        }
        Text(
            text = if (dayType == WorkDayType.AUTO)
                "Тип оплаты определяется выбранным графиком 5/2 или 2/2"
            else if (dayType == WorkDayType.REGULAR)
                "Принудительно обычный рабочий день, даже если по графику выходной"
            else "Принудительно оплачивается как праздник/выходной ×2",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCommentClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Comment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = comment.ifBlank { "Добавить комментарий" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (comment.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryTab(
    totals: List<OperationTotal>,
    onRowClick: (OperationTotal) -> Unit
) {
    if (totals.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.PlaylistAdd,
            title = "Пока пусто",
            subtitle = "Нажмите «Добавить», выберите операцию и внесите первую партию"
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(totals, key = { it.operationId }) { total ->
            SectionCard(modifier = Modifier.clickable { onRowClick(total) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = total.name,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${total.durationHours.formatNumber()} нч × ${total.totalCount} = ${total.totalHours.formatNumber()} нч",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = total.totalCount.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    FilledTonalIconButton(onClick = { onRowClick(total) }) {
                        Icon(Icons.Default.Add, contentDescription = "Добавить партию")
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalTab(
    entries: List<EntryWithOperation>,
    onDelete: (EntryWithOperation) -> Unit
) {
    if (entries.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.History,
            title = "Журнал пуст",
            subtitle = "Здесь будет видно, в какое время и какими партиями добавлялись операции"
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(entries, key = { it.id }) { entry ->
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
                    Text(
                        text = entry.operationName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${entry.quantity.formatSigned()} шт · ${entry.hours.formatNumber()} нч",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (entry.quantity < 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(entry) }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить запись",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HoursDialog(
    initialHours: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialHours.toString().removeSuffix(".0")) }
    val parsed = text.replace(',', '.').toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Рабочие часы") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Часов за смену") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null && parsed > 0
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun CommentDialog(
    initialComment: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialComment) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Комментарий") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Заметка к наряду") },
                minLines = 2,
                maxLines = 5
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
