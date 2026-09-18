package com.workorder.app.ui.component

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.workorder.app.BuildConfig
import com.workorder.app.util.FeedbackKind
import com.workorder.app.util.FeedbackMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf(FeedbackKind.BUG) }
    var includeDevice by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val body = FeedbackMessage.body(kind, description, "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        if (includeDevice) "Android ${Build.VERSION.RELEASE}; ${Build.MANUFACTURER} ${Build.MODEL}" else null)
    val valid = title.isNotBlank() && description.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Обратная связь") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Потребуется аккаунт GitHub. Текст будет открыт для проверки; отправку подтверждаешь на GitHub. Обращения доступны другим пользователям.")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    FeedbackKind.entries.forEachIndexed { index, value ->
                        SegmentedButton(selected = kind == value, onClick = { kind = value },
                            shape = SegmentedButtonDefaults.itemShape(index, FeedbackKind.entries.size)) { Text(value.label) }
                    }
                }
                OutlinedTextField(
                    title, { if (it.length <= 100) title = it }, label = { Text("Тема обращения") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("${title.length}/100") }
                )
                OutlinedTextField(
                    description, { if (it.length <= 1000) description = it }, label = { Text("Описание") },
                    minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth(),
                    supportingText = { Text("Что произошло и что ожидалось? ${description.length}/1000") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(includeDevice, { includeDevice = it })
                    Text("Добавить модель телефона и версию Android", style = MaterialTheme.typography.bodySmall)
                }
                Text("Версия приложения добавляется автоматически. База и записи нарядов не прикладываются.",
                    style = MaterialTheme.typography.bodySmall)
                status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString("${title.trim()}\n\n$body")); status = "Текст скопирован" },
                    enabled = valid, modifier = Modifier.fillMaxWidth()
                ) { Text("Скопировать текст") }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FeedbackMessage.issueUrl(kind, title, body))))
                    status = "Открыта форма GitHub. Обращение ещё нужно отправить."
                } catch (e: ActivityNotFoundException) {
                    status = "Браузер не найден. Скопируй текст и открой Issues проекта в GitHub."
                } catch (e: SecurityException) {
                    status = "Не удалось открыть GitHub. Скопируй текст обращения."
                }
            }) { Text("Открыть GitHub") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } }
    )
}
