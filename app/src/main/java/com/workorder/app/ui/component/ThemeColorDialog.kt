package com.workorder.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.workorder.app.ui.theme.seedColorScheme

/** Готовые исходные цвета: насыщенные, чтобы тема получалась выразительной. */
private val swatches = listOf(
    0xFF415F91, 0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1, 0xFF00897B, 0xFF43A047, 0xFF7CB342,
    0xFFFFB300, 0xFFF4511E, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF6D4C41, 0xFF757575
).map { it.toInt() }

private val hueBrush = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) })

/** «#3A6EA5» или «3a6ea5» → ARGB; null, если это не шесть шестнадцатеричных цифр. */
internal fun parseHexColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) return null
    return hex.toInt(16) or 0xFF000000.toInt()
}

internal fun Int.toHexColor(): String = "#%06X".format(this and 0xFFFFFF)

/**
 * Выбор цвета своей темы. Из одного цвета строится вся схема Material 3,
 * предпросмотр показывает её в текущем светлом или тёмном режиме.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeColorDialog(
    initial: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val start = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by rememberSaveable { mutableFloatStateOf(start[0]) }
    var saturation by rememberSaveable { mutableFloatStateOf(start[1]) }
    var brightness by rememberSaveable { mutableFloatStateOf(start[2]) }
    val color = Color.hsv(hue, saturation, brightness)
    val argb = color.toArgb()
    var hexText by rememberSaveable { mutableStateOf(initial.toHexColor()) }
    var hexEditing by remember { mutableStateOf(false) }
    val shownHex = if (hexEditing) hexText else argb.toHexColor()

    fun select(value: Int) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(value, hsv)
        // У серого оттенок не определён: прежний оттенок остаётся, чтобы полоса не прыгала.
        if (hsv[1] > 0f) hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
    }

    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scheme = remember(argb, dark) { seedColorScheme(argb, dark) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Своя тема") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SaturationValuePanel(hue, saturation, brightness) { s, v ->
                    hexEditing = false
                    saturation = s
                    brightness = v
                }
                HueBar(hue) { hexEditing = false; hue = it }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    swatches.forEach { swatch ->
                        val selected = swatch == argb
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(swatch))
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                )
                                .clickable(onClickLabel = "Выбрать цвет ${swatch.toHexColor()}") {
                                    hexEditing = false
                                    select(swatch)
                                }
                        )
                    }
                }
                OutlinedTextField(
                    value = shownHex,
                    onValueChange = { text ->
                        hexEditing = true
                        hexText = text.take(7)
                        parseHexColor(text)?.let(::select)
                    },
                    label = { Text("Цвет HEX") },
                    singleLine = true,
                    isError = hexEditing && parseHexColor(hexText) == null,
                    leadingIcon = { Box(Modifier.size(20.dp).clip(CircleShape).background(color)) },
                    modifier = Modifier.fillMaxWidth()
                )
                SchemePreview(scheme.primary, scheme.onPrimary, scheme.primaryContainer, scheme.onPrimaryContainer,
                    scheme.secondaryContainer, scheme.tertiaryContainer, scheme.surfaceContainer)
                Text(
                    "Из этого цвета строятся все цвета приложения и календаря. Контраст текста подбирается автоматически.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(argb) }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Квадрат: по горизонтали насыщенность, по вертикали яркость. */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (saturation: Float, brightness: Float) -> Unit
) {
    fun update(position: Offset, size: IntSize) {
        if (size.width == 0 || size.height == 0) return
        onChange(
            (position.x / size.width).coerceIn(0f, 1f),
            1f - (position.y / size.height).coerceIn(0f, 1f)
        )
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(MaterialTheme.shapes.small)
            .semantics { contentDescription = "Насыщенность и яркость" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position, size)
                    down.consume()
                    drag(down.id) { change ->
                        update(change.position, size)
                        change.consume()
                    }
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val center = Offset(saturation * size.width, (1f - brightness) * size.height)
        drawCircle(Color.White, radius = 9.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.4f), radius = 11.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
    }
}

@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit) {
    fun update(position: Offset, size: IntSize) {
        if (size.width == 0) return
        onChange((position.x / size.width).coerceIn(0f, 1f) * 359.9f)
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(CircleShape)
            .semantics { contentDescription = "Оттенок" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    update(down.position, size)
                    down.consume()
                    drag(down.id) { change ->
                        update(change.position, size)
                        change.consume()
                    }
                }
            }
    ) {
        drawRect(hueBrush)
        val x = (hue / 360f) * size.width
        drawCircle(Color.White, radius = size.height / 2 - 2.dp.toPx(), center = Offset(x, size.height / 2),
            style = Stroke(3.dp.toPx()))
    }
}

/** Образцы ролей будущей схемы: кнопка, карточка дня и поверхности. */
@Composable
private fun SchemePreview(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondaryContainer: Color,
    tertiaryContainer: Color,
    surface: Color
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).clip(MaterialTheme.shapes.small).background(primaryContainer).padding(8.dp)
            ) {
                Text("Наряд", color = onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
            }
            Box(
                Modifier.clip(CircleShape).background(primary).padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Кнопка", color = onPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(primary, primaryContainer, secondaryContainer, tertiaryContainer).forEach {
                Box(Modifier.weight(1f).height(16.dp).clip(MaterialTheme.shapes.extraSmall).background(it))
            }
        }
    }
}

/** Значок «Своя» в ряду тем: радуга, пока своя тема не выбрана, и её цвет — после выбора. */
@Composable
fun CustomThemeSwatch(color: Int?, selected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(
                if (color != null) Modifier.background(Color(color))
                else Modifier.background(Brush.sweepGradient((0..6).map { Color.hsv(it * 60f % 360f, 0.75f, 0.95f) }))
            )
            .border(
                if (selected) 3.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                CircleShape
            ),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        if (selected) Icon(Icons.Default.Check, null, tint = Color.White)
    }
}
