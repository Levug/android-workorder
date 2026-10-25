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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
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
    // Подписи стоят рядом с полосами, поэтому разные цвета не нужны: один цвет спокойнее.
    val barColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        data.forEach { (label, value, valueLabel) ->
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
                            .background(barColor)
                    )
                }
            }
        }
    }
}

/** Кольцевая диаграмма долей с легендой и текстом в центре. */
@Composable
fun DonutChart(
    slices: List<Pair<String, Double>>,
    centerTitle: String,
    centerSubtitle: String,
    modifier: Modifier = Modifier
) {
    // Больше 7 долей палитра начинает повторяться: хвост объединяется в «Прочие».
    val data = if (slices.size <= 7) slices else {
        val sorted = slices.sortedByDescending { it.second }
        sorted.take(6) + ("Прочие (${sorted.size - 6})" to sorted.drop(6).sumOf { it.second })
    }
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

/**
 * Столбики по дням месяца. Касание выбирает день (подпись и действие — через [selectedDay]/[onSelectDay]),
 * пунктир [referenceValue] показывает ориентир, например часы смены.
 */
@Composable
fun MonthColumnChart(
    dailyValues: List<Pair<Int, Double>>,
    daysInMonth: Int,
    modifier: Modifier = Modifier,
    unit: String = "ч",
    referenceValue: Double? = null,
    referenceLabel: String? = null,
    selectedDay: Int? = null,
    onSelectDay: ((Int?) -> Unit)? = null
) {
    if (dailyValues.isEmpty()) return
    val reference = referenceValue?.takeIf { it > 0 }
    val dataMax = dailyValues.maxOf { it.second }.coerceAtLeast(0.0)
    // Шкала вмещает и пунктир ориентира, а подпись «макс.» — только реальные значения.
    val maxValue = maxOf(dataMax, reference ?: 0.0)
    val minValue = dailyValues.minOf { it.second }.coerceAtMost(0.0)
    val span = (maxValue - minValue).takeIf { it > 0 } ?: 1.0
    val barColor = MaterialTheme.colorScheme.primary
    val dimmedBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val referenceColor = MaterialTheme.colorScheme.secondary
    val valueByDay = dailyValues.toMap()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "макс. ${dataMax.formatNumber()} $unit" +
                (if (minValue < 0) " · мин. ${minValue.formatNumber()} $unit" else "") +
                (if (reference != null && referenceLabel != null) " · пунктир — $referenceLabel" else ""),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .then(
                    if (onSelectDay != null) Modifier.pointerInput(daysInMonth, valueByDay) {
                        detectTapGestures { offset ->
                            val day = (offset.x / (size.width.toFloat() / daysInMonth)).toInt() + 1
                            onSelectDay(day.takeIf { it in 1..daysInMonth && it in valueByDay && it != selectedDay })
                        }
                    } else Modifier
                )
        ) {
            val slot = size.width / daysInMonth
            val barWidth = (slot * 0.65f).coerceAtLeast(2f)
            val zeroY = (maxValue / span * size.height).toFloat()

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
                val barHeight = (kotlin.math.abs(value) / span * size.height).toFloat()
                if (barHeight <= 0) continue
                drawRoundRect(
                    color = when {
                        day == selectedDay -> selectedColor
                        selectedDay != null -> dimmedBarColor
                        else -> barColor
                    },
                    topLeft = Offset(
                        x = (day - 1) * slot + (slot - barWidth) / 2,
                        y = if (value >= 0) zeroY - barHeight else zeroY
                    ),
                    size = Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
                )
            }
            drawLine(gridColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)
            if (reference != null) {
                val y = (zeroY - reference / span * size.height).toFloat()
                drawLine(
                    color = referenceColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        DayAxisLabels(daysInMonth)
    }
}

/** Подписи оси точно под центрами «своих» столбиков. */
@Composable
private fun DayAxisLabels(daysInMonth: Int) {
    val days = listOf(1, 5, 10, 15, 20, 25, daysInMonth).distinct()
    val style = MaterialTheme.typography.labelSmall
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Layout(
        content = { days.forEach { Text(it.toString(), style = style, color = color) } },
        modifier = Modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val slot = width.toFloat() / daysInMonth
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val center = (days[index] - 0.5f) * slot
                val x = (center - placeable.width / 2f).toInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}
