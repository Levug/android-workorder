package com.workorder.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.workorder.app.sync.WatchInfo
import com.workorder.app.sync.WearConnectionState
import com.workorder.app.util.formatMoment
import com.workorder.app.util.noOrphans

/**
 * Связь с часами: включение синхронизации, найденные часы и «Наряд» на них,
 * когда отправлен список операций и когда пришла последняя запись.
 */
@Composable
fun WearConnectionCard(
    state: WearConnectionState,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    showTitle: Boolean = true
) {
    SectionCard {
        if (showTitle) Text("Часы Wear OS", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Связь с часами", style = MaterialTheme.typography.bodyLarge)
                Text(
                    (if (state.enabled) "Операции и быстрые партии уходят на часы, записи с часов — в наряд"
                    else "Записи с часов ждут в очереди на часах и придут после включения").noOrphans(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(state.enabled, onToggle, enabled = enabled)
        }
        // Без раннего return: в компиляторе Compose 1.5.10 он ломал SlotTable.
        if (state.enabled) {
            when {
                state.checking && state.watches.isEmpty() ->
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp))
                state.serviceUnavailable -> Hint(
                    "Сервис Wear OS на телефоне недоступен. Установите приложение для часов " +
                        "(Wear OS или приложение производителя) и подключите часы."
                )
                state.checkedAt > 0 && state.watches.isEmpty() -> Hint(
                    "Часы не подключены. Включите Bluetooth и подключите часы в приложении для часов на телефоне."
                )
                else -> state.watches.forEach { WatchRow(it) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                InfoLine(
                    "Список операций",
                    if (state.catalogSentAt > 0) "${formatMoment(state.catalogSentAt)} · ${state.catalogSize} шт" else "ещё не отправлен"
                )
                InfoLine(
                    "Запись с часов",
                    if (state.lastEntryAt > 0) "${formatMoment(state.lastEntryAt)} · ${state.lastEntryLabel}" else "ещё не было"
                )
            }
            OutlinedButton(
                onClick = onRefresh,
                enabled = enabled && !state.checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.checking) "Проверяю…" else "Проверить связь", maxLines = 1)
            }
        }
    }
}

@Composable
private fun WatchRow(watch: WatchInfo) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Watch, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(watch.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = when {
                !watch.appFound -> "«Наряд» на часах не найден — установите приложение для часов из этого же выпуска"
                else -> buildList {
                    add("«Наряд» ${watch.appVersion ?: "установлен"}")
                    when (watch.catalogDelivered) {
                        true -> add("список получен")
                        false -> add("список в пути")
                        null -> Unit
                    }
                    watch.pendingEvents?.takeIf { it > 0 }?.let { add("в очереди $it") }
                }.joinToString(" · ")
            }
            Text(
                details.noOrphans(),
                style = MaterialTheme.typography.bodySmall,
                color = if (watch.appFound) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun InfoLine(title: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text.noOrphans(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
