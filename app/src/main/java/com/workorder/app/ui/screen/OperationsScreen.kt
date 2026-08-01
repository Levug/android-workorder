package com.workorder.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workorder.app.data.model.Operation
import com.workorder.app.ui.component.EmptyState
import com.workorder.app.ui.component.SectionCard
import com.workorder.app.ui.viewmodel.OperationsViewModel
import com.workorder.app.util.formatNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsScreen(
    viewModel: OperationsViewModel,
    onNavigateBack: () -> Unit
) {
    val operations by viewModel.operations.collectAsStateWithLifecycle()

    // null — редактор закрыт; Operation(id=0) — создание новой
    var editorFor by remember { mutableStateOf<Operation?>(null) }
    var deleteFor by remember { mutableStateOf<Operation?>(null) }

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
                    subtitle = "Добавьте операции с нормами времени, чтобы вносить их в наряды"
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(operations, key = { it.id }) { operation ->
                    val index = operations.indexOfFirst { it.id == operation.id }
                    SectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = operation.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${operation.durationHours.formatNumber()} нч за штуку · ${operation.grade} разряд",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column {
                                IconButton(
                                    onClick = { viewModel.moveUp(operation.id) },
                                    enabled = index > 0
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Поднять выше")
                                }
                                IconButton(
                                    onClick = { viewModel.moveDown(operation.id) },
                                    enabled = index in 0 until operations.lastIndex
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Опустить ниже")
                                }
                            }
                            IconButton(onClick = { editorFor = operation }) {
                                Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                            }
                            IconButton(onClick = { deleteFor = operation }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error
                                )
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
            onDismiss = { editorFor = null }
        )
    }

    deleteFor?.let { operation ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("Удалить операцию?") },
            text = {
                Text("«${operation.name}» будет удалена. Записи журнала с этой операцией тоже удалятся из всех нарядов.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(operation)
                        deleteFor = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteFor = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationEditorSheet(
    operation: Operation,
    onSave: (name: String, durationHours: Double, grade: Int) -> Unit,
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
        }
    }
}
