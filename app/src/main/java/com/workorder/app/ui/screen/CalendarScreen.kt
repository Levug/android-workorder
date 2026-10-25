package com.workorder.app.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workorder.app.ui.component.CalendarGrid
import com.workorder.app.ui.component.MonthSelectDialog
import com.workorder.app.ui.component.SectionCard
import com.workorder.app.ui.component.StatCell
import com.workorder.app.ui.viewmodel.CalendarViewModel
import com.workorder.app.util.formatDisplay
import com.workorder.app.util.formatNumber
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onNavigateToDay: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSpecialization by rememberSaveable { mutableStateOf(false) }
    var showMonthPicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(uiState.settings.daySpecializationEnabled) {
        if (!uiState.settings.daySpecializationEnabled) showSpecialization = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Календарь") },
                actions = {
                    if (uiState.settings.daySpecializationEnabled) {
                        FilledTonalIconToggleButton(
                            checked = showSpecialization,
                            onCheckedChange = { showSpecialization = it }
                        ) {
                            Icon(
                                Icons.Outlined.Flip,
                                contentDescription = if (showSpecialization) "Показать часы" else "Показать специализации"
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.goToCurrentMonth() }) {
                        Icon(Icons.Outlined.Today, contentDescription = "Текущий месяц")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.previousMonth() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
                }
                Row(
                    Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = "Выбрать месяц") { showMonthPicker = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.month.formatDisplay(),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
                }
            }

            // Свайп влево/вправо листает месяцы; содержимое сдвигается в сторону жеста.
            AnimatedContent(
                targetState = uiState,
                contentKey = { it.month },
                transitionSpec = {
                    val forward = targetState.month > initialState.month
                    (slideInHorizontally(tween(250)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(250)))
                        .togetherWith(slideOutHorizontally(tween(200)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
                },
                label = "calendarMonth",
                modifier = Modifier.pointerInput(Unit) {
                    val threshold = 72.dp.toPx()
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total > threshold) viewModel.previousMonth()
                            else if (total < -threshold) viewModel.nextMonth()
                        },
                        onHorizontalDrag = { change, amount -> total += amount; change.consume() }
                    )
                }
            ) { state ->
                CalendarGrid(
                    month = state.month,
                    days = state.days,
                    settings = state.settings,
                    showSpecialization = showSpecialization,
                    onDayClick = { date ->
                        onNavigateToDay(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    }
                )
            }

            if (uiState.settings.daySpecializationEnabled && showSpecialization) {
                val specializedDays = uiState.days.toSortedMap().filterValues { it.specialization.isNotBlank() }
                SectionCard {
                    Text("Специализации за месяц", style = MaterialTheme.typography.titleMedium)
                    if (specializedDays.isEmpty()) Text("Откройте день и задайте специализацию текстом в наряде.",
                        style = MaterialTheme.typography.bodySmall)
                    specializedDays.forEach { (date, day) ->
                        Row(Modifier.fillMaxWidth().clickable { onNavigateToDay(date.toString()) }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(date.format(DateTimeFormatter.ofPattern("dd.MM")), style = MaterialTheme.typography.labelLarge)
                            Text(day.specialization, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            SectionCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    StatCell(
                        value = uiState.workedDays.toString(),
                        label = "смен"
                    )
                    StatCell(
                        value = uiState.workedHours.formatNumber(),
                        label = "часов",
                        valueColor = MaterialTheme.colorScheme.primary
                    )
                    StatCell(
                        value = if (uiState.workedDays > 0)
                            (uiState.workedHours / uiState.workedDays).formatNumber()
                        else "0",
                        label = "ч / смена"
                    )
                }
            }
        }
    }

    if (showMonthPicker) {
        MonthSelectDialog(
            initial = uiState.month,
            onPick = { showMonthPicker = false; viewModel.goToMonth(it) },
            onDismiss = { showMonthPicker = false }
        )
    }
}
