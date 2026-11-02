package com.sixmonths.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.Weekday
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.ui.components.PlaceholderScaffold
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.StaggeredItem
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.theme.sixMonthsColors

/** Onboarding step 3: overview of the chosen training days, tap a day to edit it. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun OnboardingPlanScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onOpenDayEditor: (Int) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val plan by ServiceLocator.planRepository.observePlan()
        .collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(Unit) {
        ServiceLocator.planRepository.ensureWeek()
        ServiceLocator.settingsRepository.setOnboardingStep(3)
    }

    val trainingDays = plan?.trainingDays.orEmpty()

    PlaceholderScaffold(
        title = "Your plan",
        summary = null,
        onBack = onBack,
        progress = 3 to 7,
        footer = {
            PrimaryAction(text = "Continue", onClick = onContinue, enabled = plan != null)
            SecondaryAction(text = "Back to training days", onClick = onBack)
        },
    ) {
        when {
            plan == null -> Text(
                text = "Loading your plan…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.sixMonthsColors.textSecondary,
            )

            trainingDays.isEmpty() -> {
                Text(
                    text = "No training days yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            else -> {
                Text(
                    text = "$trainingDays training ${if (trainingDays.size == 1) "day" else "days"} · " +
                        "${trainingDays.sumOf { it.totalSets }} sets · " +
                        "${trainingDays.sumOf { it.totalTrainings }} trainings",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )

                trainingDays.forEachIndexed { index, day ->
                    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                        with(sharedTransitionScope) {
                            Modifier.sharedElement(
                                rememberSharedContentState("day-card-${day.planDay.dayOfWeek}"),
                                animatedVisibilityScope,
                            )
                        }
                    } else {
                        Modifier
                    }
                    StaggeredItem(index = index) {
                        TrainingDayCard(
                            day = day,
                            onClick = { onOpenDayEditor(day.planDay.dayOfWeek) },
                            modifier = sharedModifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingDayCard(
    day: DaySnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = day.planDay.name ?: Weekday.fullName(day.planDay.dayOfWeek),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (day.entries.isEmpty()) {
                        "No trainings"
                    } else {
                        "${day.totalTrainings} trainings · ${day.totalSets} sets"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }
            Text(
                text = if (day.planDay.state == DayState.OPTIONAL) "EDIT · OPTIONAL" else "EDIT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
