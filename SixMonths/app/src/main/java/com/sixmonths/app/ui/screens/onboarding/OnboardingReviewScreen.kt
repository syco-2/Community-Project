package com.sixmonths.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.Weekday
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.domain.CoachReview
import com.sixmonths.app.domain.Severity
import com.sixmonths.app.ui.components.PlaceholderScaffold
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.screens.dashboard.scheduleReminderAfterSettingsChange
import com.sixmonths.app.ui.theme.sixMonthsColors
import kotlinx.coroutines.launch

/** Onboarding step 7: deterministic coach summary, then "Finish". */
@Composable
fun OnboardingReviewScreen(
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val plan by ServiceLocator.planRepository.observePlan()
        .collectAsStateWithLifecycle(initialValue = null)
    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)

    var finishing by remember { mutableStateOf(false) }
    val review = remember(plan) { plan?.let { CoachReview.analyze(it) } }

    LaunchedEffect(Unit) {
        ServiceLocator.settingsRepository.setOnboardingStep(7)
    }

    val trainingDays = plan?.trainingDays.orEmpty()
    val totalSets = trainingDays.sumOf { it.totalSets }
    val totalTrainings = trainingDays.sumOf { it.totalTrainings }

    PlaceholderScaffold(
        title = "Coach review",
        summary = null,
        onBack = onBack,
        progress = 7 to 7,
        footer = {
            PrimaryAction(
                text = if (finishing) "Saving…" else "Finish and start",
                onClick = {
                    if (!finishing) {
                        finishing = true
                        scope.launch {
                            val existing = ServiceLocator.settingsRepository.current()
                            val now = System.currentTimeMillis()
                            ServiceLocator.settingsRepository.edit {
                                it.copy(
                                    onboardingCompleted = true,
                                    onboardingStep = 7,
                                    // Editing an existing plan keeps its phase and cycle.
                                    planPhase = if (existing.planPhaseStartedAt > 0L) existing.planPhase else 1,
                                    planPhaseStartedAt = if (existing.planPhaseStartedAt > 0L) {
                                        existing.planPhaseStartedAt
                                    } else {
                                        now
                                    },
                                    cycleStartedAt = if (existing.cycleStartedAt > 0L) {
                                        existing.cycleStartedAt
                                    } else {
                                        now
                                    },
                                    cycleIsExplicit = existing.cycleIsExplicit,
                                )
                            }
                            scheduleReminderAfterSettingsChange(context)
                            onFinish()
                        }
                    }
                },
                enabled = !finishing,
            )
            SecondaryAction(text = "Back to adjust", onClick = onBack)
        },
    ) {
        val currentSettings = settings
        if (plan == null || currentSettings == null) {
            Text(
                text = "Building your summary…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.sixMonthsColors.textSecondary,
            )
            return@PlaceholderScaffold
        }

        if (trainingDays.isEmpty()) {
            Text(
                text = "No training days yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            SixMonthsCard {
                Text(
                    text = "Your week",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$totalTrainings trainings · $totalSets sets across ${trainingDays.size} days",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.sixMonthsColors.textSecondary,
                )
                Text(
                    text = trainingDays.joinToString(" · ") { day ->
                        (day.planDay.name ?: Weekday.shortName(day.planDay.dayOfWeek)) +
                            " (${day.totalSets} sets)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }

            SixMonthsCard {
                Text(
                    text = "Sets by category",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ExerciseCategory.ALL.forEach { category ->
                    val sets = review?.setsByCategory?.get(category) ?: 0
                    Text(
                        text = "${ExerciseCategory.displayName(category)}: $sets sets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (sets == 0) {
                            MaterialTheme.sixMonthsColors.textMuted
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            if (review != null && review.suggestions.isNotEmpty()) {
                SixMonthsCard {
                    Text(
                        text = "Coach notes",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    review.suggestions.take(4).forEach { suggestion ->
                        Column {
                            Text(
                                text = suggestion.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = when (suggestion.severity) {
                                    Severity.CRITICAL -> MaterialTheme.sixMonthsColors.danger
                                    Severity.WARNING -> MaterialTheme.sixMonthsColors.warning
                                    Severity.INFO -> MaterialTheme.sixMonthsColors.success
                                },
                            )
                            Text(
                                text = suggestion.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.sixMonthsColors.textSecondary,
                            )
                        }
                    }
                }
            }
        }

        SixMonthsCard {
            Text(
                text = "Reminders & music",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (currentSettings.reminderDays.isEmpty()) {
                    "No reminder days selected."
                } else {
                    "${currentSettings.reminderTime} on ${currentSettings.reminderDays.sorted().joinToString(", ") { Weekday.shortName(it) }}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.sixMonthsColors.textSecondary,
            )
            Text(
                text = if (currentSettings.musicPlaylistUris.isEmpty()) {
                    "No music selected — optional."
                } else {
                    "${currentSettings.musicPlaylistUris.size} tracks in the playlist."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.sixMonthsColors.textMuted,
            )
        }
    }
}

