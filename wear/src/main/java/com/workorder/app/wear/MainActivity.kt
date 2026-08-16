package com.workorder.app.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.workorder.app.wear.sync.WearSyncRepository
import com.workorder.shared.WearOperationDto
import kotlinx.coroutines.launch
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as WearWorkOrderApp).container.syncRepository
        setContent {
            MaterialTheme {
                WearWorkOrderScreen(repository)
            }
        }
    }
}

@Composable
private fun WearWorkOrderScreen(repository: WearSyncRepository) {
    val operations by repository.operations.collectAsState()
    val status by repository.status.collectAsState()
    val isSending by repository.isSending.collectAsState()
    var selectedId by remember { mutableLongStateOf(0L) }
    val selected = operations.firstOrNull { it.id == selectedId }

    LaunchedEffect(Unit) { repository.refreshCatalog() }
    BackHandler(enabled = selected != null) { selectedId = 0L }

    if (selected == null) {
        OperationList(
            operations = operations,
            status = status,
            onSelect = { selectedId = it.id },
            onRefresh = { repository.refreshCatalog() }
        )
    } else {
        QuantityScreen(
            operation = selected,
            isSending = isSending,
            status = status,
            onSend = { repository.queueOperation(selected, it) },
            onBack = { selectedId = 0L }
        )
    }
}

@Composable
private fun OperationList(
    operations: List<WearOperationDto>,
    status: String,
    onSelect: (WearOperationDto) -> Unit,
    onRefresh: suspend () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 34.dp),
            autoCentering = AutoCenteringParams(itemIndex = 0),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text("Наряд · Сегодня", style = MaterialTheme.typography.title3)
            }
            item {
                Text(
                    status,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (operations.isEmpty()) {
                item {
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { scope.launch { onRefresh() } },
                        label = { Text("Повторить синхронизацию") },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            } else {
                items(operations, key = { it.id }) { operation ->
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onSelect(operation) },
                        label = {
                            Text(operation.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        },
                        secondaryLabel = {
                            Text("${operation.grade} разряд · ${formatHours(operation.durationHours)} нч")
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuantityScreen(
    operation: WearOperationDto,
    isSending: Boolean,
    status: String,
    onSend: suspend (Int) -> Boolean,
    onBack: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var customQuantity by remember(operation.id) { mutableIntStateOf(1) }

    fun send(quantity: Int) {
        if (isSending) return
        scope.launch {
            if (onSend(quantity)) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onBack()
            }
        }
    }

    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 34.dp),
            autoCentering = AutoCenteringParams(itemIndex = 0),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    operation.name,
                    style = MaterialTheme.typography.title3,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            item { Text("Быстро добавить", style = MaterialTheme.typography.caption1) }
            item { QuickButtons(values = listOf(1, 5), enabled = !isSending, onClick = ::send) }
            item { QuickButtons(values = listOf(10, 17), enabled = !isSending, onClick = ::send) }
            item { Spacer(Modifier.height(2.dp)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        modifier = Modifier.size(48.dp),
                        onClick = { customQuantity = (customQuantity - 1).coerceAtLeast(1) },
                        enabled = !isSending,
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) { Text("−") }
                    Text(customQuantity.toString(), style = MaterialTheme.typography.title2)
                    Button(
                        modifier = Modifier.size(48.dp),
                        onClick = { customQuantity = (customQuantity + 1).coerceAtMost(999) },
                        enabled = !isSending,
                        colors = ButtonDefaults.secondaryButtonColors()
                    ) { Text("+") }
                }
            }
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { send(customQuantity) },
                    enabled = !isSending,
                    label = { Text(if (isSending) "Сохраняю…" else "Добавить +$customQuantity") }
                )
            }
            item {
                Text(
                    status,
                    style = MaterialTheme.typography.caption2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onBack,
                    label = { Text("Назад") },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

@Composable
private fun QuickButtons(values: List<Int>, enabled: Boolean, onClick: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        values.forEach { value ->
            Button(
                modifier = Modifier.size(58.dp),
                onClick = { onClick(value) },
                enabled = enabled
            ) {
                Text("+$value")
            }
        }
    }
}

private val hoursFormat = DecimalFormat("0.###")

private fun formatHours(value: Double): String = hoursFormat.format(value)
