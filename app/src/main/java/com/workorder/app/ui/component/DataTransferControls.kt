package com.workorder.app.ui.component

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workorder.app.ui.viewmodel.SettingsViewModel
import com.workorder.app.util.TransferKind

@Composable
fun DataTransferControls(viewModel: SettingsViewModel) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val file by viewModel.exportFile.collectAsStateWithLifecycle()
    val pending by viewModel.pendingImport.collectAsStateWithLifecycle()
    val hasSafetyCopy by viewModel.hasSafetyCopy.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var saving by rememberSaveable { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        saving = false
        uri?.let(viewModel::saveExport)
    }
    val importDatabase = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.readImport(it, TransferKind.FULL_DATABASE) }
    }
    val importConfiguration = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.readImport(it, TransferKind.CONFIGURATION) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Вся база данных", style = MaterialTheme.typography.titleSmall)
        Text("Наряды, время и партии операций, заметки, настройки, порядок операций и банк.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.prepareExport(TransferKind.FULL_DATABASE) }, enabled = !busy,
                modifier = Modifier.weight(1f)) { Text("Экспорт базы") }
            OutlinedButton(onClick = {
                runCatching { importDatabase.launch(arrayOf("*/*")) }.onFailure { viewModel.showTransferError() }
            }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Импорт базы") }
        }
        Text("Операции и настройки", style = MaterialTheme.typography.titleSmall)
        Text("Отдельный конфиг без нарядов и банка. Можно выбрать старый work_order_settings.json.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.prepareExport(TransferKind.CONFIGURATION) }, enabled = !busy,
                modifier = Modifier.weight(1f)) { Text("Экспорт конфига") }
            OutlinedButton(onClick = {
                runCatching { importConfiguration.launch(arrayOf("*/*")) }.onFailure { viewModel.showTransferError() }
            }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Импорт конфига") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (hasSafetyCopy) {
            TextButton(onClick = viewModel::prepareSafetyExport, enabled = !busy) {
                Text("Сохранить базу до последнего импорта")
            }
        }
    }

    file?.takeUnless { saving }?.let { prepared ->
        AlertDialog(
            onDismissRequest = viewModel::dismissExport,
            title = { Text("Куда экспортировать?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Копия содержит личные данные и не зашифрована. Выбери место хранения или приложение-получатель.")
                    Button(onClick = {
                        saving = true
                        runCatching { save.launch(prepared.name) }.onFailure {
                            saving = false
                            viewModel.showTransferError()
                        }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Сохранить на устройстве") }
                    OutlinedButton(onClick = {
                        runCatching {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", prepared)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, prepared.name, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Отправить копию WorkOrder"))
                            viewModel.dismissExport()
                        }.onFailure { viewModel.showTransferError() }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Отправить в приложение") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = viewModel::dismissExport, enabled = !busy) { Text("Отмена") } }
        )
    }

    pending?.let { prepared ->
        val full = prepared.kind == TransferKind.FULL_DATABASE
        AlertDialog(
            onDismissRequest = viewModel::dismissImport,
            title = { Text(if (full) "Заменить всю базу?" else "Импортировать конфиг?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Операций: ${prepared.data.operations.size}")
                    if (full) {
                        Text("Нарядов: ${prepared.data.workDays.count { it.isRecorded }} · Планов: ${prepared.data.workDays.count { !it.isRecorded }} · Записей журнала: ${prepared.data.entries.size}\n" +
                            "Планов месяцев: ${prepared.data.monthlyPlans.size} · Записей банка: ${prepared.data.bankAllocations.size}")
                        Text("Текущие данные будут заменены копией из файла, а не объединены с ней. Сначала дождись синхронизации часов.")
                    } else {
                        Text("Одноимённые операции обновятся, новые добавятся. Наряды и записи журнала не удаляются. Изменение нормы времени влияет на расчёты.")
                    }
                    Text("Перед импортом автоматически сохранится страховочная копия всей текущей базы. Её можно экспортировать из этого раздела.")
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmImport, enabled = !busy) {
                Text(if (full) "Заменить базу" else "Импортировать")
            } },
            dismissButton = { TextButton(onClick = viewModel::dismissImport, enabled = !busy) { Text("Отмена") } }
        )
    }
}
