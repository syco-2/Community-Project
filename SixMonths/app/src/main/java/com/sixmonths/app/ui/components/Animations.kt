package com.sixmonths.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import com.sixmonths.app.core.util.rememberHaptics
import com.sixmonths.app.ui.theme.animationsEnabled
import kotlinx.coroutines.delay

/**
 * The house press feel: 0.92x while held, spring back with a slight overshoot.
 * Used by buttons and tappable cards, so every tap feels the same.
 */
@Composable
fun Modifier.springPress(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    pressedScale: Float = 0.92f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.4f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "springPress",
    )
    return this.scale(scale)
}

/** Tappable surface with the same spring press and a light haptic. No click SFX. */
@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val haptics = rememberHaptics()
    return this
        .springPress(interactionSource, enabled)
        .clickable(
            enabled = enabled,
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = {
                haptics.tick()
                onClick()
            },
        )
}

/**
 * Staggered list entrance: each item fades and slides in 50 ms after the previous one.
 * Respects Settings → animations.
 */
@Composable
fun StaggeredItem(
    index: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = animationsEnabled,
    content: @Composable () -> Unit,
) {
    var visible by remember(index, enabled) { mutableStateOf(!enabled) }

    LaunchedEffect(index, enabled) {
        if (!enabled) {
            visible = true
        } else {
            delay((index.coerceIn(0, 20) * 50L) + 30L)
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = if (enabled) {
            fadeIn(tween(220)) +
                slideInVertically(
                    spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
                ) { height -> height / 3 } +
                scaleIn(
                    spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
                    initialScale = 0.94f,
                )
        } else {
            EnterTransition.None
        },
        exit = ExitTransition.None,
        content = { content() },
    )
}
