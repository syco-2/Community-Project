package com.sixmonths.app.ui.screens.train
import com.sixmonths.app.ui.components.AppTextButton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.SessionStatus
import com.sixmonths.app.core.model.Weekday
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.domain.WeeklyProgression
import com.sixmonths.app.playback.SoundFx
import com.sixmonths.app.ui.components.EmptyIllustration
import com.sixmonths.app.ui.components.EmptyState
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.SetsRepsBadge
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.components.SkeletonRows
import com.sixmonths.app.ui.components.TabBarScrollTrackerForColumn
import com.sixmonths.app.ui.theme.sixMonthsColors
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TrainScreen(
    onStartWorkout: (String) -> Unit,
    onOpenDayEditor: (Int) -> Unit,
    onEditPlan: () -> Unit,
    onOpenWeeklyReview: () -> Unit,
) {
    val plan by ServiceLocator.planRepository.observePlan()
        .collectAsStateWithLifecycle(initialValue = null)
    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val recentSessions by ServiceLocator.sessionDao
        .observeWithStatus(SessionStatus.COMPLETED, 200)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val scope = rememberCoroutineScope()
    var starting by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val todayDay = plan?.dayFor(today.dayOfWeek.value)
    val completedToday = recentSessions.any { DateUtils.toLocalDate(it.startedAt) == today }

    val weekStart = remember { WeeklyProgression.completedWeekStart(today) }
    val sessionsLastWeek = recentSessions.count { session ->
        DateUtils.toLocalDate(session.startedAt).let { it >= weekStart && it < weekStart.plusDays(7) }
    }
    val reviewResolved = settings?.lastProgressionReviewWeek == weekStart.toString()
    val hasNoTrainingDays = plan?.trainingDays?.isEmpty() != false

    val scrollState = rememberScrollState()
    TabBarScrollTrackerForColumn(scrollState)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            // Keeps the final plan row above the floating tab bar.
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (plan == null) {
            SkeletonRows(rows = 4, rowHeight = 96.dp)
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Train",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = DateUtils.relativeLabel(today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }
            AppTextButton(onClick = onEditPlan) {
                Text("Edit plan")
            }
        }

        SixMonthsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "TODAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = todayDay?.let { DayState.displayName(it.planDay.state).uppercase() } ?: "NO PLAN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }
            Text(
                text = when {
                    todayDay == null -> "No plan for today"
                    todayDay.planDay.state == DayState.REST -> "Rest day"
                    todayDay.entries.isEmpty() -> "Plan is empty"
                    else -> todayDay.planDay.name ?: "Training day"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            if (todayDay != null && todayDay.entries.isNotEmpty()) {
                todayDay.entries.take(3).forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        SetsRepsBadge(sets = entry.sets, reps = entry.reps, unit = entry.unit)
                    }
                }
                if (todayDay.entries.size > 3) {
                    Text(
                        text = "+${todayDay.entries.size - 3} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.sixMonthsColors.textMuted,
                    )
                }
            }

            when {
                completedToday -> Text(
                    text = "Done today",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.sixMonthsColors.success,
                )

                todayDay != null &&
                    todayDay.planDay.state != DayState.REST &&
                    todayDay.entries.isNotEmpty() -> PrimaryAction(
                    text = if (starting) "Starting…" else "Start workout",
                    enabled = !starting,
                    onClick = {
                        val day = todayDay ?: return@PrimaryAction
                        starting = true
                        scope.launch {
                            val session = ServiceLocator.workoutRepository.startSession(day.planDay.id)
                            starting = false
                            onStartWorkout(session.id)
                        }
                    },
                )
            }
        }

        SixMonthsCard {
            Text(
                text = "WEEKLY PROGRESSION",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = if (reviewResolved) {
                    "Last week reviewed"
                } else if (sessionsLastWeek > 0) {
                    "Review last week"
                } else {
                    "No review due"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SecondaryAction(
                text = if (reviewResolved) "Open review" else "Review ${DateUtils.formatShort(weekStart)}",
                onClick = onOpenWeeklyReview,
            )
        }

        SixMonthsCard {
            Text(
                text = "Plan",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hasNoTrainingDays) {
                EmptyState(
                    title = "No training days yet",
                    message = null,
                    illustration = EmptyIllustration.DUMBBELL,
                )
            }
            plan?.days?.forEach { day ->
                DayRow(day = day, onClick = { onOpenDayEditor(day.planDay.dayOfWeek) })
            }
        }

    }
}

@Composable
private fun DayRow(day: DaySnapshot, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Weekday.shortName(day.planDay.dayOfWeek),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(42.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = day.planDay.name ?: DayState.displayName(day.planDay.state),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (day.entries.isEmpty()) {
                    "No trainings"
                } else {
                    "${day.totalTrainings} trainings · ${day.totalSets} sets"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.sixMonthsColors.textMuted,
            )
        }
        if (day.entries.isNotEmpty()) {
            Text(
                text = day.entries.first().let { ExerciseCategory.displayName(it.category) },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.sixMonthsColors.textSecondary,
            )
        }
        AppTextButton(onClick = onClick) { Text("Edit") }
    }
}
