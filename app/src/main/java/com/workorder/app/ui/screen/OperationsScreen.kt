package com.workorder.app.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workorder.app.data.dao.OperationUsage
import com.workorder.app.data.model.Operation
import com.workorder.app.ui.component.EmptyState
import com.workorder.app.ui.component.SectionHeader
import com.workorder.app.ui.viewmodel.OperationsViewModel
import com.workorder.app.util.formatNumber

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OperationsScreen(
    viewModel: OperationsViewModel,
    onNavigateBack: () -> Unit
) {
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val active = operations.filter { !it.archived }
    val archived = operations.filter { it.archived }

    // null — редактор закрыт; Operation(id=0) — создание новой
    var editorFor by remember { mutableStateOf<Operation?>(null) }
    var deleteFor by remember { mutableStateOf<Operation?>(null) }
    var showArchive by rememberSaveable { mutableStateOf(false) }

    // Локальная копия порядка: во время перетаскивания список меняется мгновенно, в базу — по отпусканию.
    val ordered = remember { mutableStateListOf<Operation>() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (draggingId == null) {
            ordered.clear()
            ordered.addAll(active)
        }
    }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Каталог операций") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editorFor = Operation(id = 0, name = "", durationHours = 0.0) }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Добавить операцию")
            }
        }
    ) { padding ->
        if (operations.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Outlined.Handyman,
                    title = "Каталог пуст",
                    subtitle = "Добавьте операции с нормами времени, чтобы вносить их в наряды",
                    action = {
                        Button(onClick = { editorFor = Operation(id = 0, name = "", durationHours = 0.0) }) {
                            Text("Добавить операцию")
                        }
                    }
                )
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (ordered.size > 1) {
                item(key = "hint") {
                    Text(
                        "Касание — изменить. Потяните за ≡, чтобы поменять порядок: он же используется в наряде, отчётах и на часах.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(ordered, key = { it.id }) { operation ->
                val dragging = operation.id == draggingId
                OperationCard(
                    operation = operation,
                    dragging = dragging,
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .then(if (dragging) Modifier else Modifier.animateItemPlacement())
                        .semantics {
                            val index = ordered.indexOfFirst { it.id == operation.id }
                            customActions = listOfNotNull(
                                if (index > 0) CustomAccessibilityAction("Поднять выше") { viewModel.moveUp(operation.id); true } else null,
                                if (index < ordered.lastIndex) CustomAccessibilityAction("Опустить ниже") { viewModel.moveDown(operation.id); true } else null
                            )
                        },
                    onClick = { editorFor = operation },
                    handle = Modifier.pointerInput(operation.id) {
                        detectDragGestures(
                            onDragStart = { draggingId = operation.id; dragOffset = 0f },
                            onDragEnd = {
                                draggingId = null
                                dragOffset = 0f
                                viewModel.reorder(ordered.map { it.id })
                            },
                            onDragCancel = { draggingId = null; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val items = listState.layoutInfo.visibleItemsInfo
                                val current = items.firstOrNull { it.key == operation.id } ?: return@detectDragGestures
                                val center = current.offset + current.size / 2 + dragOffset
                                val target = items.firstOrNull { info ->
                                    info.key != operation.id && info.key is Long &&
                                        center.toInt() in info.offset..(info.offset + info.size)
                                } ?: return@detectDragGestures
                                val from = ordered.indexOfFirst { it.id == operation.id }
                                val to = ordered.indexOfFirst { it.id == target.key }
                                if (from < 0 || to < 0) return@detectDragGestures
                                ordered.add(to, ordered.removeAt(from))
                                // Карточка встаёт на новое место; смещение пересчитываем, чтобы она осталась под пальцем.
                                val newOffset = if (to > from) target.offset + target.size - current.size else target.offset
                                dragOffset -= (newOffset - current.offset)
                            }
                        )
                    }
                )
            }
            if (archived.isNotEmpty()) {
                item(key = "archive-header") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showArchive = !showArchive }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader("Архив (${archived.size})", Modifier.weight(1f))
                        Icon(if (showArchive) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
                    }
                }
                if (showArchive) {
                    items(archived, key = { "archived-${it.id}" }) { operation ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).clickable { editorFor = operation }) {
                                    Text(operation.name, style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${operation.durationHours.formatNumber()} нч · ${operation.grade} разряд · скрыта из выбора",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { viewModel.setArchived(operation, false) }) {
                                    Icon(Icons.Outlined.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Вернуть")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editorFor?.let { operation ->
        OperationEditorSheet(
            operation = operation,
            onSave = { name, duration, grade ->
                viewModel.save(operation.id, name, duration, grade)
                editorFor = null
            },
            onArchive = if (operation.id != 0L) {
                { viewModel.setArchived(operation, !operation.archived); editorFor = null }
            } else null,
            onDelete = if (operation.id != 0L) {
                { deleteFor = operation; editorFor = null }
            } else null,
            onDismiss = { editorFor = null }
        )
    }

    deleteFor?.let { operation ->
        val usage by produceState<OperationUsage?>(null, operation.id) { value = viewModel.usage(operation) }
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Удалить операцию?") },
            text = {
                val used = usage
                Text(
                    when {
                        used == null -> "Проверяем, где используется «${operation.name}»…"
                        used.entries == 0 -> "«${operation.name}» ещё не вносилась в наряды и будет удалена."
                        else -> "Вместе с «${operation.name}» удалятся ${used.entries} записей журнала из ${used.days} нарядов. " +
                            "Отчёты и зарплата прошлых месяцев изменятся.\n\nЕсли операция просто больше не нужна, уберите её в архив: история сохранится."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(operation)
                        deleteFor = null
                    },
                    enabled = usage != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Удалить") }
            },
            dismissButton = {
                Row {
                    if ((usage?.entries ?: 0) > 0 && !operation.archived) {
                        TextButton(onClick = { viewModel.setArchived(operation, true); deleteFor = null }) { Text("В архив") }
                    }
                    TextButton(onClick = { deleteFor = null }) { Text("Отмена") }
                }
            }
        )
    }
}

@Composable
private fun OperationCard(
    operation: Operation,
    dragging: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    handle: Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dragging) 6.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Изменить", onClick = onClick)
                    .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp)
            ) {
                Text(text = operation.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${operation.durationHours.formatNumber()} нч за штуку · ${operation.grade} разряд",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.DragHandle,
                contentDescription = "Перетащить",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = handle.padding(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationEditorSheet(
    operation: Operation,
    onSave: (name: String, durationHours: Double, grade: Int) -> Unit,
    onArchive: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(operation.name) }
    var durationText by remember {
        mutableStateOf(
            if (operation.durationHours > 0) operation.durationHours.toString().removeSuffix(".0") else ""
        )
    }
    val parsedDuration = durationText.replace(',', '.').toDoubleOrNull()
    var grade by remember { mutableStateOf(operation.grade.coerceIn(3, 6)) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (operation.id == 0L) "Новая операция" else "Редактирование",
                style = MaterialTheme.typography.titleLarge
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = durationText,
                onValueChange = { durationText = it },
                label = { Text("Норма времени, ч за штуку") },
                supportingText = if (operation.id != 0L) {
                    { Text("Изменение нормы пересчитает нормо-часы и прошлых записей") }
                } else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text("Разряд операции", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (3..6).forEach { value ->
                    FilterChip(
                        selected = grade == value,
                        onClick = { grade = value },
                        label = { Text(value.toString()) }
                    )
                }
            }

            Button(
                onClick = { parsedDuration?.let { onSave(name, it, grade) } },
                enabled = name.isNotBlank() && parsedDuration != null && parsedDuration > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Сохранить")
            }

            if (onArchive != null || onDelete != null) {
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onArchive?.let {
                        OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) {
                            Icon(if (operation.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (operation.archived) "Вернуть из архива" else "В архив")
                        }
                    }
                    onDelete?.let {
                        TextButton(
                            onClick = it,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) { Text("Удалить…") }
                    }
                }
                if (!operation.archived) Text(
                    "Архивная операция не показывается при выборе и на часах, но её история и отчёты сохраняются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
