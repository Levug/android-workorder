package com.workorder.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.workorder.app.data.model.Operation
import com.workorder.app.ui.viewmodel.BatchAddedEvent
import com.workorder.app.ui.viewmodel.QuickAction
import com.workorder.app.util.formatNumber
import com.workorder.app.util.formatSigned

/**
 * Шторка добавления партии: быстрые кнопки добавляют сразу, произвольное количество —
 * через поле ввода (можно и списать). Подтверждение с отменой показывается прямо в шторке,
 * потому что снекбар экрана находится под ней.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuantitySheet(
    operation: Operation,
    currentCount: Int,
    quickAmounts: List<Int>,
    lastAdded: BatchAddedEvent?,
    onAdd: (Int) -> Unit,
    onUndo: (BatchAddedEvent) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toIntOrNull()
    val canAdd = amount != null && amount > 0
    val add = {
        if (canAdd) {
            onAdd(amount!!)
            amountText = ""
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column {
                Text(
                    text = operation.name,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${operation.grade} разряд · ${operation.durationHours.formatNumber()} нч за штуку",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedValue(
                    text = currentCount.toString(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "шт за день · ${(operation.durationHours * currentCount).formatNumber()} нч",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            val confirmation = lastAdded?.takeIf { it.operationId == operation.id }
            AnimatedVisibility(visible = confirmation != null) {
                confirmation?.let { event ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (event.quantity > 0) "Добавлено ${event.quantity.formatSigned()}" else "Списано ${-event.quantity}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { onUndo(event) }) { Text("Отменить") }
                        }
                    }
                }
            }

            // До шести быстрых партий: больше четырёх — два ряда по три.
            val perRow = if (quickAmounts.size > 4) 3 else quickAmounts.size.coerceAtLeast(1)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                quickAmounts.chunked(perRow).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        row.forEach { quick ->
                            FilledTonalButton(
                                onClick = { onAdd(quick) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("+$quick", maxLines = 1)
                            }
                        }
                        repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { text ->
                    if (text.length <= 5 && text.all { it.isDigit() }) amountText = text
                },
                label = { Text("Своё количество") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        amount?.let { onAdd(-it) }
                        amountText = ""
                    },
                    enabled = canAdd && amount!! <= currentCount,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Списать")
                }
                Button(
                    onClick = add,
                    enabled = canAdd,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Добавить")
                }
            }
        }
    }
}

/** Шторка выбора операции: каталог с поиском. Недавние — в ряду «Быстро добавить» на экране наряда. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationPickerSheet(
    operations: List<Operation>,
    onPick: (Operation) -> Unit,
    onDismiss: () -> Unit,
    onOpenCatalog: (() -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(operations, query) {
        if (query.isBlank()) operations
        else operations.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = "Выберите операцию",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(12.dp))

            if (operations.size > 5) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            if (operations.isEmpty()) {
                Text(
                    text = "Каталог операций пуст. Добавьте операции с нормами времени — и их можно будет вносить в наряд.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                if (onOpenCatalog != null) {
                    Button(onClick = onOpenCatalog, modifier = Modifier.fillMaxWidth()) {
                        Text("Открыть каталог операций")
                    }
                }
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { operation ->
                        OperationRow(operation, null) { onPick(operation) }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                "Ничего не найдено",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperationRow(operation: Operation, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(operation.name) },
        supportingContent = {
            Text("${operation.grade} разряд · ${operation.durationHours.formatNumber()} нч за штуку")
        },
        leadingContent = icon?.let { { Icon(it, contentDescription = null) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    )
}
