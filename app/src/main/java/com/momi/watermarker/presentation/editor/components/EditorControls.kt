package com.momi.watermarker.presentation.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** A horizontally-scrolling, single-select chip row backed by a generic list. */
@Composable
fun <T> OptionChipRow(
    options: List<T>,
    selected: T?,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelected(option) },
                label = { Text(labelOf(option)) },
            )
        }
    }
}

/** A row of circular color swatches; the selected one shows a check mark. */
@Composable
fun ColorSwatchRow(
    colors: List<Int>,
    selectedArgb: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        colors.forEach { argb ->
            val color = Color(argb)
            val isSelected = argb == selectedArgb
            Row(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .clickable { onSelected(argb) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    )
                }
            }
        }
    }
}

/**
 * An RGB color picker: a live preview swatch with the numeric `RGB(r, g, b)`
 * value, plus one 0–255 slider per channel. [colorArgb]'s alpha is ignored;
 * [onColorChanged] always reports an opaque color.
 */
@Composable
fun RgbColorPicker(
    colorArgb: Int,
    onColorChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val r = (colorArgb shr 16) and 0xFF
    val g = (colorArgb shr 8) and 0xFF
    val b = colorArgb and 0xFF

    fun argb(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(argb(r, g, b)))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            ) {}
            Text(
                text = "RGB($r, $g, $b)",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ChannelSlider("R", r) { onColorChanged(argb(it, g, b)) }
        ChannelSlider("G", g) { onColorChanged(argb(r, it, b)) }
        ChannelSlider("B", b) { onColorChanged(argb(r, g, it)) }
    }
}

/** A single 0–255 color-channel slider labeled with its current value. */
@Composable
private fun ChannelSlider(label: String, value: Int, onValueChange: (Int) -> Unit) {
    Column {
        Text(
            text = "$label — $value",
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 0f..255f,
        )
    }
}

/** A labeled slider that shows the current value as a percentage. */
@Composable
fun PercentSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        Text(
            text = "$label — ${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelLarge,
        )
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}
