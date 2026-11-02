package com.sixmonths.app.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.ui.theme.NumericBadgeStyle
import com.sixmonths.app.ui.theme.animationsEnabled

/**
 * Pill badge on the right of a Day Editor row: "3 × 6" for reps, "3 × 45s" for seconds.
 * The numerals count up when the value changes; animations can be disabled in Settings.
 */
@Composable
fun SetsRepsBadge(
    sets: Int,
    reps: Int,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val animate = animationsEnabled
    val spec: AnimationSpec<Int> = if (animate) tween(durationMillis = 320) else snap()
    val animatedSets by animateIntAsState(targetValue = sets, animationSpec = spec, label = "badgeSets")
    val animatedReps by animateIntAsState(targetValue = reps, animationSpec = spec, label = "badgeReps")
    val value = if (unit == ExerciseUnit.SECONDS) "${animatedReps}s" else animatedReps.toString()

    Text(
        text = "$animatedSets × $value",
        style = NumericBadgeStyle,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(CircleShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                shape = CircleShape,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
