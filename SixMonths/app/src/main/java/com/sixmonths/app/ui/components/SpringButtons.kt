package com.sixmonths.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.sixmonths.app.core.util.rememberHaptics

/**
 * Material's TextButton plus the house feel: 0.92x while pressed, spring back with a small
 * overshoot, click SFX and a real 10 ms haptic. Signature mirrors [TextButton], so screens
 * can swap it in without changing any layout.
 */
@Composable
fun AppTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    TextButton(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier.springPress(interaction, enabled),
        enabled = enabled,
        contentPadding = contentPadding,
        interactionSource = interaction,
        content = content,
    )
}

/** [IconButton] with the same spring press, click SFX and haptic. */
@Composable
fun AppIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    IconButton(
        onClick = {
            haptics.tick()
            onClick()
        },
        modifier = modifier.springPress(interaction, enabled),
        enabled = enabled,
        interactionSource = interaction,
        content = content,
    )
}
