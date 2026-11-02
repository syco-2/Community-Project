package com.sixmonths.app.ui.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.sixmonths.app.ui.screens.dashboard.scheduleReminderAfterSettingsChange
import com.sixmonths.app.ui.theme.sixMonthsColors
import kotlinx.coroutines.launch

/** Onboarding step 2: the seven day cards, all REST by default. */
@Composable
fun OnboardingDaysScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val plan by ServiceLocator.planRepository.observePlan()
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ServiceLocator.planRepository.ensureWeek()
        ServiceLocator.settingsRepository.setOnboardingStep(2)
    }

    val trainingCount = plan?.trainingDays?.size ?: 0
    val totalSets = plan?.trainingDays?.sumOf { it.totalSets } ?: 0

    PlaceholderScaffold(
        title = "Training days",
        summary = null,
        onBack = onBack,
        progress = 2 to 7,
        footer = {
            PrimaryAction(text = "Continue", onClick = onContinue, enabled = plan != null)
            SecondaryAction(text = "Back", onClick = onBack)
        },
    ) {
        Text(
            text = if (plan == null) {
                "Loading your week…"
            } else {
                "$trainingCount training ${if (trainingCount == 1) "day" else "days"} · $totalSets sets"
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )

        plan?.days?.forEachIndexed { index, day ->
            StaggeredItem(index = index) {
                DayStateCard(
                    day = day,
                    onClick = {
                        scope.launch {
                            ServiceLocator.planRepository.setDayState(
                                dayOfWeek = day.planDay.dayOfWeek,
                                state = nextState(day.planDay.state),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DayStateCard(day: DaySnapshot, onClick: () -> Unit) {
    val state = day.planDay.state
    val accent = when (state) {
        DayState.TRAINING -> MaterialTheme.colorScheme.primary
        DayState.OPTIONAL -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.sixMonthsColors.textMuted
    }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (state == DayState.TRAINING) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = Weekday.fullName(day.planDay.dayOfWeek),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state == DayState.TRAINING) {
                    Text(
                        text = "${day.totalTrainings} trainings · ${day.totalSets} sets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.sixMonthsColors.textMuted,
                    )
                }
            }
            Text(
                text = DayState.displayName(state).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

private fun nextState(current: String): String = when (current) {
    DayState.REST -> DayState.TRAINING
    DayState.TRAINING -> DayState.OPTIONAL
    else -> DayState.REST
}
