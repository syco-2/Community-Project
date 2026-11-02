package com.sixmonths.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.sixmonths.app.ui.theme.sixMonthsColors

/** Falling confetti, used when a plan phase is applied. Disabled with animations off. */
@Composable
fun ConfettiOverlay(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.sixMonthsColors.success,
        MaterialTheme.sixMonthsColors.flameEnd,
    )

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1_800, easing = LinearEasing),
        )
        onFinished()
    }

    Canvas(modifier = modifier) {
        repeat(60) { index ->
            val x = ((index * 53) % 100) / 100f * size.width
            val speed = 0.55f + ((index * 31) % 45) / 100f
            val y = -30f + progress.value * (size.height + 60f) * speed
            drawRect(
                color = colors[index % colors.size],
                topLeft = Offset(x, y),
                size = Size(10f, 18f),
                alpha = (1f - progress.value).coerceIn(0f, 1f),
            )
        }
    }
}
