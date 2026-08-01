package com.workorder.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.workorder.app.data.model.Operation
import com.workorder.app.util.formatNumber

private val quickAmounts = listOf(1, 5, 10, 20)

/**
 * Шторка добавления партии: быстрые кнопки +1/+5/+10/+20 добавляют сразу,
 * произвольное количество — через поле ввода (можно и списать).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuantitySheet(
    operation: Operation,
    currentCount: Int,
    onAdd: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toIntOrNull()

    ModalBottomSheet(onDismissRequest = onDismiss) {
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
                    text = "${operation.grade} разряд · ${operation.durationHours.formatNumber()} нч за штуку · сегодня: $currentCount шт · ${(operation.durationHours * currentCount).formatNumber()} нч",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickAmounts.forEach { quick ->
                    FilledTonalButton(
                        onClick = { onAdd(quick) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("+$quick")
                    }
                }
            }

            OutlinedTextField(
                value = amountText,
                onValueChange = { text ->
                    if (text.length <= 5 && text.all { it.isDigit() }) amountText = text
                },
                label = { Text("Своё количество") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    enabled = amount != null && amount > 0 && amount <= currentCount,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Убавить")
                }
                Button(
                    onClick = {
                        amount?.let { onAdd(it) }
                        amountText = ""
                    },
                    enabled = amount != null && amount > 0,
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

/** Шторка выбора операции из каталога с поиском. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationPickerSheet(
    operations: List<Operation>,
    onPick: (Operation) -> Unit,
    onDismiss: () -> Unit
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
                    text = "Каталог операций пуст.\nДобавьте операции: Настройки → Операции.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn {
                    items(filtered, key = { it.id }) { operation ->
                        ListItem(
                            headlineContent = { Text(operation.name) },
                            supportingContent = {
                                Text("${operation.grade} разряд · ${operation.durationHours.formatNumber()} нч за штуку")
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(operation) }
                        )
                    }
                }
            }
        }
    }
}
