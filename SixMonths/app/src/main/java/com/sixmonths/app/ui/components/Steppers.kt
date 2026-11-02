package com.sixmonths.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sixmonths.app.core.util.rememberHaptics
import com.sixmonths.app.ui.theme.sixMonthsColors

/** Compact -/+ stepper with large numerals, used for sets, reps and rest. */
@Composable
fun ValueStepper(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: IntRange = 1..20,
    step: Int = 1,
    valueText: (Int) -> String = { it.toString() },
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.sixMonthsColors.textSecondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(label = "−", enabled = value > valueRange.first) {
                onValueChange((value - step).coerceIn(valueRange))
            }
            Text(
                text = valueText(value),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 58.dp)
                    .padding(horizontal = 2.dp),
            )
            StepButton(label = "+", enabled = value < valueRange.last) {
                onValueChange((value + step).coerceIn(valueRange))
            }
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    OutlinedButton(
        onClick = {
            haptics.tick()
            onClick()
        },
        enabled = enabled,
        modifier = Modifier
            .size(38.dp)
            .springPress(interaction, enabled),
        interactionSource = interaction,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(text = label, style = MaterialTheme.typography.titleMedium)
    }
}
