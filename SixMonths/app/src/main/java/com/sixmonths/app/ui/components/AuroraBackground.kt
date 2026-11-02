package com.sixmonths.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.sixmonths.app.ui.theme.animationsEnabled
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Alive" background: three very low-alpha colour clouds drifting on different orbits.
 * It is deliberately slow (24 s / 31 s / 38 s) and desaturated, so it reads as depth and
 * never competes with the content. Off when Settings → animations is off.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    val animate = animationsEnabled
    val transition = rememberInfiniteTransition(label = "aurora")
    val t1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing)),
        label = "auroraBlue",
    )
    val t2 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(31_000, easing = LinearEasing)),
        label = "auroraOrange",
    )
    val t3 by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(38_000, easing = LinearEasing)),
        label = "auroraGreen",
    )

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!animate) {
                glow(center = Offset(size.width * 0.25f, size.height * 0.12f), radius = size.width * 0.85f,
                    color = Color(0xFF2F80ED), alpha = 0.13f * intensity)
                glow(center = Offset(size.width * 0.85f, size.height * 0.55f), radius = size.width * 0.7f,
                    color = Color(0xFFFF6B2C), alpha = 0.10f * intensity)
                return@Canvas
            }
            glow(
                center = orbit(t1, 0.25f, 0.14f, 0.10f, size.width, size.height),
                radius = size.width * 0.9f,
                color = Color(0xFF2F80ED),
                alpha = 0.15f * intensity,
            )
            glow(
                center = orbit(t2, 0.82f, 0.52f, 0.13f, size.width, size.height),
                radius = size.width * 0.75f,
                color = Color(0xFFFF6B2C),
                alpha = 0.10f * intensity,
            )
            glow(
                center = orbit(t3, 0.55f, 0.92f, 0.08f, size.width, size.height),
                radius = size.width * 0.65f,
                color = Color(0xFF22C55E),
                alpha = 0.055f * intensity,
            )
        }
        content()
    }
}

private fun orbit(
    progress: Float,
    baseX: Float,
    baseY: Float,
    radius: Float,
    width: Float,
    height: Float,
): Offset {
    val angle = progress * 2f * Math.PI.toFloat()
    return Offset(
        x = width * (baseX + cos(angle) * radius),
        y = height * (baseY + sin(angle) * radius * 1.4f),
    )
}

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
        alpha = 1f,
    )
}
