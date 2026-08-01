package com.workorder.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.workorder.app.util.formatNumber

/** Категориальная палитра, читаемая и на светлой, и на тёмной теме. */
val chartPalette = listOf(
    Color(0xFF6C7FD8),
    Color(0xFF56B4A2),
    Color(0xFFE1975A),
    Color(0xFFC77CA8),
    Color(0xFF8FB65C),
    Color(0xFFD8746C),
    Color(0xFF7FB2D8),
    Color(0xFFB39B5C)
)

/**
 * Горизонтальные полосы: название, доля от максимума, значение.
 * Идеально для длинных названий операций.
 */
@Composable
fun HorizontalBarChart(
    data: List<Triple<String, Double, String>>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    val maxValue = data.maxOf { it.second }.takeIf { it > 0 } ?: 1.0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        data.forEachIndexed { index, (label, value, valueLabel) ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = valueLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth((value / maxValue).toFloat().coerceIn(0.02f, 1f))
                            .clip(CircleShape)
                            .background(chartPalette[index % chartPalette.size])
                    )
                }
            }
        }
    }
}

/** Кольцевая диаграмма долей с легендой и текстом в центре. */
@Composable
fun DonutChart(
    data: List<Pair<String, Double>>,
    centerTitle: String,
    centerSubtitle: String,
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.second }
    if (data.isEmpty() || total <= 0) return

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(180.dp)) {
                val strokeWidth = 28.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2,
                    (size.height - diameter) / 2
                )
                val arcSize = Size(diameter, diameter)
                val gapAngle = if (data.size > 1) 2f else 0f
                var startAngle = -90f

                data.forEachIndexed { index, (_, value) ->
                    val sweep = (value / total * 360f).toFloat() - gapAngle
                    if (sweep > 0f) {
                        drawArc(
                            color = chartPalette[index % chartPalette.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                    }
                    startAngle += sweep + gapAngle
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = centerTitle,
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = centerSubtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            data.forEachIndexed { index, (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(chartPalette[index % chartPalette.size])
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(value / total * 100).formatNumber()} %",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** Столбики часов по дням месяца. */
@Composable
fun MonthColumnChart(
    dailyValues: List<Pair<Int, Double>>,
    daysInMonth: Int,
    modifier: Modifier = Modifier
) {
    if (dailyValues.isEmpty()) return
    val maxValue = dailyValues.maxOf { it.second }.takeIf { it > 0 } ?: 1.0
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "макс. ${maxValue.formatNumber()} ч",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            val valueByDay = dailyValues.toMap()
            val slot = size.width / daysInMonth
            val barWidth = (slot * 0.65f).coerceAtLeast(2f)

            // Сетка: линии на 0%, 50%, 100%
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = size.height * (1f - fraction)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            for (day in 1..daysInMonth) {
                val value = valueByDay[day] ?: continue
                val barHeight = (value / maxValue * size.height).toFloat()
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(
                        x = (day - 1) * slot + (slot - barWidth) / 2,
                        y = size.height - barHeight
                    ),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf(1, 5, 10, 15, 20, 25, daysInMonth).distinct().forEach { day ->
                Text(
                    text = day.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
