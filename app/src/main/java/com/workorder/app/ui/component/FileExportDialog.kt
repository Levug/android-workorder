package com.workorder.app.ui.component

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import android.net.Uri
import java.io.File

/**
 * Выбор, куда деть готовый файл: сохранить через системный диалог или отправить в приложение.
 * Файл лежит во временной папке приложения, поэтому без этого шага его не найти.
 */
@Composable
fun FileExportDialog(
    file: File,
    mimeType: String,
    title: String,
    description: String,
    onSave: (Uri) -> Unit,
    onError: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        if (uri != null) onSave(uri) else onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(description)
                Button(
                    onClick = { runCatching { save.launch(file.name) }.onFailure { onError() } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить на устройстве") }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = mimeType
                                putExtra(Intent.EXTRA_STREAM, uri)
                                clipData = ClipData.newUri(context.contentResolver, file.name, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Отправить отчёт"))
                            onDismiss()
                        }.onFailure { onError() }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Отправить в приложение") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
