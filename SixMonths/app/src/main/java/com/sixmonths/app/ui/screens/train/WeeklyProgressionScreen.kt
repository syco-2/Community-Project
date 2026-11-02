package com.sixmonths.app.ui.screens.train
import com.sixmonths.app.ui.components.AppTextButton

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.core.model.Weekday
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.domain.WeeklyAction
import com.sixmonths.app.domain.WeeklyEntrySuggestion
import com.sixmonths.app.domain.WeeklyProgression
import com.sixmonths.app.playback.SoundFx
import com.sixmonths.app.ui.components.PlaceholderScaffold
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.theme.sixMonthsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * One row per exercise of the last completed week. Nothing changes until the user taps
 * Accept; Keep leaves the plan untouched. The week is only marked reviewed once every row
 * is resolved.
 */
@Composable
fun WeeklyProgressionScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val plan by ServiceLocator.planRepository.observePlan()
        .collectAsStateWithLifecycle(initialValue = null)
    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)

    val weekStart = remember { WeeklyProgression.completedWeekStart(LocalDate.now()) }
    var suggestions by remember { mutableStateOf<List<WeeklyEntrySuggestion>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var resolved by remember { mutableStateOf(setOf<String>()) }
    var finished by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(plan) {
        val currentPlan = plan ?: return@LaunchedEffect
        loading = true
        val data = withContext(Dispatchers.IO) {
            val sessions = ServiceLocator.workoutRepository.completedSessionsBetween(
                DateUtils.toEpochMillis(weekStart),
                DateUtils.toEpochMillis(weekStart.plusDays(7)),
            )
            val logs = sessions.associate { session ->
                session.id to ServiceLocator.setLogDao.listForSession(session.id)
            }
            WeeklyProgression.analyze(currentPlan, sessions, logs, weekStart)
        }
        suggestions = data
        loading = false
    }

    val open = suggestions.filterNot { it.entry.planExerciseId in resolved }
    val allResolved = !loading && open.isEmpty()

    LaunchedEffect(allResolved, settings) {
        val current = settings ?: return@LaunchedEffect
        if (allResolved && !finished && current.lastProgressionReviewWeek != weekStart.toString()) {
            ServiceLocator.settingsRepository.setLastProgressionReviewWeek(weekStart.toString())
            finished = true
        }
    }

    fun resolve(item: WeeklyEntrySuggestion, accept: Boolean) {
        scope.launch {
            if (accept && item.action != WeeklyAction.KEEP) {
                ServiceLocator.planRepository.updateEntry(
                    planExerciseId = item.entry.planExerciseId,
                    sets = item.suggestedSets,
                    reps = item.suggestedReps,
                )
                message = "${item.entry.name}: ${item.headline.lowercase()}"
            } else {
                message = "${item.entry.name}: kept as is."
            }
            resolved = resolved + item.entry.planExerciseId
        }
    }

    PlaceholderScaffold(
        title = "Weekly progression",
        summary = null,
        onBack = onBack,
        footer = {
            if (allResolved) {
                PrimaryAction(text = "Done", onClick = onBack)
            } else {
                SecondaryAction(text = "Decide later", onClick = onBack)
            }
        },
    ) {
        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.sixMonthsColors.success,
            )
        }

        when {
            loading -> Text(
                text = "Reading last week…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.sixMonthsColors.textSecondary,
            )

            suggestions.isEmpty() -> SixMonthsCard {
                Text(
                    text = "No completed sessions last week",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            else -> {
                if (allResolved) {
                    SixMonthsCard {
                        Text(
                            text = "Week reviewed",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.sixMonthsColors.success,
                        )
                    }
                }

                open.forEach { item ->
                    SuggestionCard(item = item, onAccept = { resolve(item, true) }, onKeep = { resolve(item, false) })
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    item: WeeklyEntrySuggestion,
    onAccept: () -> Unit,
    onKeep: () -> Unit,
) {
    val unit = if (item.entry.unit == ExerciseUnit.SECONDS) "s" else ""
    SixMonthsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${Weekday.shortName(item.dayOfWeek)} · ${item.entry.label} · now ${item.entry.sets} × ${item.entry.reps}$unit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }
            Text(
                text = "${item.completedSets}/${item.plannedSets} sets",
                style = MaterialTheme.typography.labelMedium,
                color = if (item.completedSets >= item.plannedSets) {
                    MaterialTheme.sixMonthsColors.success
                } else {
                    MaterialTheme.sixMonthsColors.warning
                },
            )
        }
        Text(
            text = item.headline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = item.detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.sixMonthsColors.textSecondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            AppTextButton(onClick = onKeep) { Text("Keep") }
            if (item.action != WeeklyAction.KEEP) {
                AppTextButton(onClick = onAccept) { Text("Accept") }
            }
        }
    }
}
