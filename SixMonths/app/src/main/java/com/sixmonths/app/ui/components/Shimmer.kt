package com.sixmonths.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sixmonths.app.ui.theme.animationsEnabled

/**
 * Slow shimmer sweep for skeleton placeholders. The gradient moves across the placeholder
 * instead of a hard blink, which is what makes loading feel calm rather than broken.
 */
@Composable
fun shimmerBrush(): Brush {
    val enabled = animationsEnabled
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f).compositeOver(base)

    if (!enabled) return Brush.linearGradient(listOf(base, base))

    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )
    val width = 900f
    val start = -width + progress * width * 2.2f
    return Brush.linearGradient(
        colors = listOf(base, highlight, base),
        start = Offset(start, 0f),
        end = Offset(start + width, width),
    )
}

/** One shimmering placeholder block. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    Box(modifier = modifier.clip(shape).background(shimmerBrush()))
}

/** Placeholder that mirrors the dashboard layout: greeting, streak card, list cards. */
@Composable
fun SkeletonDashboard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ShimmerBox(modifier = Modifier.width(150.dp).height(14.dp))
        ShimmerBox(modifier = Modifier.width(230.dp).height(30.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(148.dp), shape = RoundedCornerShape(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier.weight(1f).height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }
        repeat(3) {
            Row(
                modifier = Modifier.fillMaxWidth().height(74.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerBox(modifier = Modifier.size(44.dp), shape = CircleShape)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
                    ShimmerBox(modifier = Modifier.width(80.dp).height(11.dp))
                }
                ShimmerBox(modifier = Modifier.size(width = 54.dp, height = 26.dp))
            }
        }
    }
}

/** Placeholder rows for any list screen (train days, stats, monsters cabinet). */
@Composable
fun SkeletonRows(
    modifier: Modifier = Modifier,
    rows: Int = 5,
    rowHeight: Dp = 88.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(rows) {
            ShimmerBox(
                modifier = Modifier.fillMaxWidth().height(rowHeight),
                shape = RoundedCornerShape(20.dp),
            )
        }
    }
}
