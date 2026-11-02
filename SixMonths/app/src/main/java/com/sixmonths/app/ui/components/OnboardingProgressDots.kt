package com.sixmonths.app.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sixmonths.app.ui.theme.animationsEnabled

/**
 * Six-step progress indicator. The wide pill marks the active step; the transition is a
 * spring unless Settings -> animations is off, in which case it snaps.
 */
@Composable
fun OnboardingProgressDots(
    currentStep: Int,
    totalSteps: Int,
    modifier: Modifier = Modifier,
) {
    val animate = animationsEnabled
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep - 1
            val isDone = index < currentStep - 1
            val targetWidth = if (isActive) 30.dp else 10.dp
            val widthSpec: AnimationSpec<Dp> = if (animate) {
                spring(dampingRatio = 0.6f, stiffness = 500f)
            } else {
                snap()
            }
            val width by animateDpAsState(
                targetValue = targetWidth,
                animationSpec = widthSpec,
                label = "onboardingDotWidth",
            )
            val color = when {
                isActive -> MaterialTheme.colorScheme.primary
                isDone -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}
