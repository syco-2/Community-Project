package com.sixmonths.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sixmonths.app.ui.theme.animationsEnabled
import com.sixmonths.app.ui.theme.sixMonthsColors

enum class EmptyIllustration { MONSTER, DUMBBELL, CHART, SPARK }

/**
 * Empty states never say "No data". They show a small hand-drawn 2D illustration and one
 * motivating line that tells the user what happens next.
 */
@Composable
fun EmptyState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    illustration: EmptyIllustration = EmptyIllustration.SPARK,
    hint: String? = null,
) {
    val animate = animationsEnabled
    var appeared by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) { appeared = true }
    val scale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.82f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow),
        label = "emptyStateArt",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EmptyArt(
            illustration = illustration,
            modifier = Modifier
                .size(120.dp)
                .then(Modifier),
            scale = scale,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.sixMonthsColors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        hint?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.sixMonthsColors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(2.dp))
    }
}

/** The drawings: intentionally simple geometry, drawn with the theme accents. */
@Composable
fun EmptyArt(
    illustration: EmptyIllustration,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val muted = MaterialTheme.colorScheme.outline

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.028f)
        val cx = w / 2f
        val cy = h / 2f

        // Soft floor glow, shared by every illustration.
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.16f * scale),
                    Color.Transparent,
                ),
                center = Offset(cx, cy),
                radius = w * 0.52f,
            ),
            radius = w * 0.52f,
            center = Offset(cx, cy),
        )

        when (illustration) {
            EmptyIllustration.MONSTER -> {
                // A round, friendly blob with two eyes and a horn.
                drawCircle(
                    color = secondary.copy(alpha = 0.20f),
                    radius = w * 0.26f * scale,
                    center = Offset(cx, cy + h * 0.04f),
                )
                drawCircle(
                    color = secondary.copy(alpha = 0.55f),
                    radius = w * 0.26f * scale,
                    center = Offset(cx, cy + h * 0.04f),
                    style = stroke,
                )
                drawCircle(Color.White.copy(alpha = 0.85f), w * 0.045f, Offset(cx - w * 0.09f, cy))
                drawCircle(Color.White.copy(alpha = 0.85f), w * 0.045f, Offset(cx + w * 0.09f, cy))
                drawCircle(primary, w * 0.022f, Offset(cx - w * 0.09f, cy))
                drawCircle(primary, w * 0.022f, Offset(cx + w * 0.09f, cy))
                val horn = Path().apply {
                    moveTo(cx - w * 0.05f, cy - h * 0.20f)
                    lineTo(cx, cy - h * 0.33f)
                    lineTo(cx + w * 0.05f, cy - h * 0.20f)
                    close()
                }
                drawPath(horn, secondary.copy(alpha = 0.8f))
                val smile = Path().apply {
                    moveTo(cx - w * 0.06f, cy + h * 0.10f)
                    quadraticBezierTo(cx, cy + h * 0.17f, cx + w * 0.06f, cy + h * 0.10f)
                }
                drawPath(smile, Color.White.copy(alpha = 0.6f), style = stroke)
            }

            EmptyIllustration.DUMBBELL -> {
                val barHeight = h * 0.055f
                drawRoundRect(
                    color = muted.copy(alpha = 0.8f),
                    topLeft = Offset(cx - w * 0.20f, cy - barHeight / 2f),
                    size = Size(w * 0.40f, barHeight),
                    cornerRadius = CornerRadius(barHeight),
                )
                repeat(2) { index ->
                    val sign = if (index == 0) -1f else 1f
                    drawRoundRect(
                        color = primary.copy(alpha = 0.85f),
                        topLeft = Offset(cx + sign * w * 0.28f - w * 0.035f, cy - h * 0.13f),
                        size = Size(w * 0.07f, h * 0.26f),
                        cornerRadius = CornerRadius(w * 0.03f),
                    )
                    drawRoundRect(
                        color = secondary.copy(alpha = 0.7f),
                        topLeft = Offset(cx + sign * w * 0.36f - w * 0.022f, cy - h * 0.09f),
                        size = Size(w * 0.044f, h * 0.18f),
                        cornerRadius = CornerRadius(w * 0.02f),
                    )
                }
            }

            EmptyIllustration.CHART -> {
                val baseline = cy + h * 0.20f
                val bars = listOf(0.18f, 0.30f, 0.24f, 0.42f)
                bars.forEachIndexed { index, value ->
                    val barWidth = w * 0.10f
                    val left = cx - w * 0.26f + index * w * 0.14f
                    drawRoundRect(
                        color = if (index == bars.lastIndex) secondary else primary.copy(alpha = 0.6f),
                        topLeft = Offset(left, baseline - h * value * scale),
                        size = Size(barWidth, h * value * scale),
                        cornerRadius = CornerRadius(barWidth * 0.4f),
                    )
                }
                drawLine(
                    color = muted.copy(alpha = 0.6f),
                    start = Offset(cx - w * 0.30f, baseline),
                    end = Offset(cx + w * 0.30f, baseline),
                    strokeWidth = w * 0.012f,
                )
            }

            EmptyIllustration.SPARK -> {
                repeat(6) { index ->
                    val angle = (index * 60f) * (Math.PI / 180f)
                    val inner = w * 0.10f
                    val outer = w * 0.24f * scale
                    drawLine(
                        color = if (index % 2 == 0) primary else secondary,
                        start = Offset(cx + (Math.cos(angle) * inner).toFloat(), cy + (Math.sin(angle) * inner).toFloat()),
                        end = Offset(cx + (Math.cos(angle) * outer).toFloat(), cy + (Math.sin(angle) * outer).toFloat()),
                        strokeWidth = w * 0.03f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
                drawCircle(secondary.copy(alpha = 0.85f), w * 0.055f, Offset(cx, cy))
            }
        }
    }
}
