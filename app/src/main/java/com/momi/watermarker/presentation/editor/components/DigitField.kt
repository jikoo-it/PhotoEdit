package com.momi.watermarker.presentation.editor.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** Whole-number field that ignores non-digits and syncs when [value] changes from outside. */
@Composable
fun DigitField(
    value: Long,
    onValueChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    maxDigits: Int = 6,
    allowZero: Boolean = false,
    enabled: Boolean = true,
) {
    val shown = when {
        allowZero -> value.coerceAtLeast(0L).toString()
        value > 0L -> value.toString()
        else -> ""
    }
    var text by remember { mutableStateOf(shown) }
    LaunchedEffect(shown) {
        if (text.toLongOrNull() != value) text = shown
    }
    OutlinedTextField(
        value = text,
        onValueChange = { incoming ->
            val digits = incoming.filter { it.isDigit() }.take(maxDigits)
            text = digits
            digits.toLongOrNull()
                ?.takeIf { if (allowZero) it >= 0L else it > 0L }
                ?.let(onValueChange)
        },
        label = { Text(label) },
        suffix = { if (suffix != null) Text(suffix) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
