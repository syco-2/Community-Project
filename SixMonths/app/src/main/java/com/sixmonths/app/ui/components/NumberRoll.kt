package com.sixmonths.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.sixmonths.app.ui.theme.animationsEnabled

/**
 * Slot-machine number: when [value] changes the old number slides out and the new one
 * slides in from the opposite direction. Direction follows the value, so a raise rolls up
 * and a drop rolls down. Respects Settings → animations.
 */
@Composable
fun RollingNumber(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
    color: Color = MaterialTheme.colorScheme.onBackground,
    suffix: String = "",
    contentDescription: String? = null,
) {
    val animate = animationsEnabled
    Row(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription ?: "$value$suffix"
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                if (!animate) {
                    fadeIn(spring(stiffness = Spring.StiffnessHigh)) togetherWith
                        fadeOut(spring(stiffness = Spring.StiffnessHigh))
                } else {
                    val rising = targetState >= initialState
                    val enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.5f,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        initialOffsetY = { height -> if (rising) height else -height },
                    ) + fadeIn(spring(stiffness = Spring.StiffnessMedium))
                    val exit = slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = 0.7f,
                            stiffness = Spring.StiffnessMedium,
                        ),
                        targetOffsetY = { height -> if (rising) -height else height },
                    ) + fadeOut(spring(stiffness = Spring.StiffnessMedium))
                    enter togetherWith exit using SizeTransform(clip = false)
                }
            },
            label = "rollingNumber",
        ) { target ->
            Text(text = "$target", style = style, color = color)
        }
        if (suffix.isNotEmpty()) {
            Text(text = suffix, style = style, color = color)
        }
    }
}

/** Compact variant used inside cards and pills. */
@Composable
fun RollingNumberSmall(
    value: Int,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    suffix: String = "",
) {
    RollingNumber(
        value = value,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        color = color,
        suffix = suffix,
    )
}
