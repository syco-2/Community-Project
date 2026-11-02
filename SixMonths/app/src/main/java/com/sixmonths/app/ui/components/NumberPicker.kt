package com.sixmonths.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sixmonths.app.core.util.rememberHaptics
import com.sixmonths.app.ui.theme.sixMonthsColors

/**
 * Tappable value row that opens [NumberPickerDialog]. Used for sets, reps and seconds.
 * No wheel, no keyboard, no sheet: the dialog is centered and holds one number.
 */
@Composable
fun NumberPickerField(
    label: String,
    valueText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .springPress(interaction, enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.sixMonthsColors.textSecondary,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.sixMonthsColors.textMuted,
            )
        }
    }
}

/**
 * Clean, centered number picker. [title] is the unit the user is choosing — "Reps" for
 * rep-based exercises, "Seconds" for time-based ones, "Sets" for the set count.
 */
@Composable
fun NumberPickerDialog(
    title: String,
    value: Int,
    range: IntRange,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    step: Int = 1,
    unitLabel: String = "",
) {
    var current by remember(title, value) {
        mutableIntStateOf(value.coerceIn(range.first, range.last))
    }
    val haptics = rememberHaptics()

    fun set(newValue: Int) {
        val clamped = newValue.coerceIn(range.first, range.last)
        if (clamped != current) {
            current = clamped
            haptics.tick()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.sixMonthsColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (unitLabel.isBlank()) "$current" else "$current$unitLabel",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTextButton(
                        enabled = current > range.first,
                        onClick = { set(current - step) },
                    ) {
                        Text("−", style = MaterialTheme.typography.headlineSmall)
                    }
                    AppTextButton(
                        enabled = current < range.last,
                        onClick = { set(current + step) },
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(onClick = { onConfirm(current) }) {
                Text("Done", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
