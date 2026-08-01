package com.workorder.app.ui.screen

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
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workorder.app.ui.component.CalendarGrid
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Календарь") },
                actions = {
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
                Text(
                    text = uiState.month.formatDisplay(),
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
                }
            }

            CalendarGrid(
                month = uiState.month,
                days = uiState.days,
                settings = uiState.settings,
                onDayClick = { date ->
                    onNavigateToDay(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                }
            )

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
}
