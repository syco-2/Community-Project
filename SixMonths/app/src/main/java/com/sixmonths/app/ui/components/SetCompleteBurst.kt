package com.sixmonths.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sixmonths.app.ui.theme.animationsEnabled
import kotlin.math.cos
import kotlin.math.sin

/**
 * Burst of 12 glowing particles plus a heavy spring on the label when a set is logged.
 * Trigger with a changing [trigger] value (for example the logged-set count).
 */
@Composable
fun SetCompleteBurst(
    trigger: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = animationsEnabled,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(trigger) {
        if (trigger <= 0 || !enabled) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.55f,
                stiffness = Spring.StiffnessLow,
            ),
        )
    }

    if (!enabled) return

    Canvas(modifier = modifier) {
        val value = progress.value
        if (value <= 0.001f) return@Canvas
        val radius = size.minDimension / 2f
        repeat(12) { index ->
            val angle = (index * 30f + 8f) * (Math.PI / 180f)
            val distance = radius * (0.35f + 0.65f * value)
            val particleRadius = (4.dp.toPx() * (1f - value * 0.7f)).coerceAtLeast(1f)
            val color = if (index % 2 == 0) {
                Color(0xFFFFD166)
            } else {
                Color(0xFF2F80ED)
            }
            drawCircle(
                color = color.copy(alpha = (1f - value).coerceIn(0f, 1f)),
                radius = particleRadius,
                center = Offset(
                    x = center.x + cos(angle).toFloat() * distance,
                    y = center.y + sin(angle).toFloat() * distance,
                ),
            )
        }
    }
}

/** Centers the burst behind any content, e.g. the "Set logged" label. */
@Composable
fun SetLoggedBurst(
    trigger: Int,
    content: @Composable () -> Unit,
) {
    Box(contentAlignment = Alignment.Center) {
        SetCompleteBurst(
            trigger = trigger,
            modifier = Modifier.size(130.dp),
        )
        content()
    }
}
