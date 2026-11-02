package com.sixmonths.app.ui.screens.dashboard
import com.sixmonths.app.ui.components.AppTextButton

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.domain.PlanProgression
import com.sixmonths.app.domain.ProgressionAction
import com.sixmonths.app.notifications.ReminderScheduler
import com.sixmonths.app.ui.components.AuroraBackground
import com.sixmonths.app.ui.components.CelebrationOverlay
import com.sixmonths.app.ui.components.ConfettiOverlay
import com.sixmonths.app.ui.components.HouseAnimation
import com.sixmonths.app.ui.components.HouseLottie
import com.sixmonths.app.ui.components.LocalTabBarState
import com.sixmonths.app.ui.components.EmptyIllustration
import com.sixmonths.app.ui.components.EmptyState
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.RollingNumber
import com.sixmonths.app.ui.components.RollingNumberSmall
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.SetsRepsBadge
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.components.SixMonthsLogo
import com.sixmonths.app.ui.components.SkeletonDashboard
import com.sixmonths.app.ui.components.TabBarScrollTrackerForColumn
import com.sixmonths.app.ui.theme.animationsEnabled
import com.sixmonths.app.ui.theme.sixMonthsColors
import com.sixmonths.app.ui.util.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(
    onStartWorkout: (String) -> Unit,
    onOpenPlanEditor: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMusic: () -> Unit,
) {
    val viewModel: DashboardViewModel = viewModel(
        factory = viewModelFactory { DashboardViewModel() },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var startingWorkout by remember { mutableStateOf(false) }
    var showProgression by remember { mutableStateOf(false) }
    var progressionHandled by rememberSaveable { mutableStateOf(false) }
    var showConfetti by remember { mutableStateOf(false) }
    var showTrophy by remember { mutableStateOf(false) }

    val weekStartIso = remember { LocalDate.now().with(DayOfWeek.MONDAY).toString() }
    val weeklyGoalDone = state.plannedThisWeek > 0 &&
        state.trainingDaysDoneThisWeek >= state.plannedThisWeek

    // Trophy celebration: once per completed week, the first time the dashboard sees it.
    LaunchedEffect(weeklyGoalDone, state.settings.lastTrophyWeek) {
        if (weeklyGoalDone && state.settings.lastTrophyWeek != weekStartIso) {
            showTrophy = true
        }
    }

    LaunchedEffect(state.progressionDue) {
        if (state.progressionDue && !progressionHandled) showProgression = true
    }

    val today = state.today
    val canTrain = today != null &&
        today.planDay.state != DayState.REST &&
        today.entries.isNotEmpty() &&
        !state.completedToday

    // Same scroll state is reported to the shell so the tab bar can slide away.
    val scrollState = rememberScrollState()
    TabBarScrollTrackerForColumn(scrollState)

    // The trophy overlay is full-screen: keep the floating bar out of it.
    val tabBarState = LocalTabBarState.current
    LaunchedEffect(showTrophy) { tabBarState.setHidden(showTrophy) }

    // The "alive" aurora sits behind the whole page; content scrolls over it.
    AuroraBackground(modifier = Modifier.fillMaxSize()) {
    if (state.loading) {
        SkeletonDashboard()
    } else Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            // The glass tab bar floats over the page; keep the last card clear of it.
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SixMonthsLogo(modifier = Modifier.size(42.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "Six Months",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "made by Syco",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = MaterialTheme.sixMonthsColors.textMuted,
                        maxLines = 1,
                    )
                }
                Text(
                    text = DateUtils.relativeLabel(DateUtils.today()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }
            AppTextButton(onClick = onOpenSettings) {
                Text(text = "Settings")
            }
        }

        SixMonthsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TODAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = today?.let { DayState.displayName(it.planDay.state).uppercase() } ?: "NO PLAN",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }

            if (today == null) {
                EmptyState(
                    illustration = EmptyIllustration.DUMBBELL,
                    title = "No plan yet",
                    message = null,
                )
            } else {
                Text(
                    text = when {
                        today.planDay.state == DayState.REST -> "Rest day"
                        today.planDay.state == DayState.OPTIONAL -> "Optional"
                        today.entries.isEmpty() -> "Training day"
                        else -> today.planDay.name ?: "Training day"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (today != null && today.planDay.state != DayState.REST && today.entries.isNotEmpty()) {
                TodayEntries(day = today)
                Text(
                    text = "${today.totalTrainings} trainings · ${today.totalSets} sets",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }

            when {
                state.completedToday -> Text(
                    text = "Done today",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.sixMonthsColors.success,
                )

                canTrain -> PrimaryAction(
                    text = if (startingWorkout) "Starting…" else "Start Workout",
                    enabled = !startingWorkout,
                    onClick = {
                        val day = state.today ?: return@PrimaryAction
                        startingWorkout = true
                        scope.launch {
                            val session = ServiceLocator.workoutRepository.startSession(day.planDay.id)
                            startingWorkout = false
                            onStartWorkout(session.id)
                        }
                    },
                )

                else -> SecondaryAction(
                    text = "Edit plan",
                    onClick = onOpenPlanEditor,
                )
            }
        }

        SixMonthsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "STREAK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        RollingNumber(
                            value = state.streak.currentStreak,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            contentDescription = "Current streak: ${state.streak.currentStreak} days",
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.streak.currentStreak == 1) "day" else "days",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.sixMonthsColors.textSecondary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                StreakFlame(streak = state.streak.currentStreak)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                StatPill(label = "Longest", value = state.streak.longestStreak)
                StatPill(label = "Freezes", value = state.streak.freezeTokens)
                StatPill(label = "Sessions", value = state.streak.totalCompletedSessions)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WeeklyRing(
                    completed = state.sessionsThisWeek,
                    planned = state.plannedThisWeek,
                )
                Column {
                    Text(
                        text = "This week",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${state.sessionsThisWeek} / ${state.plannedThisWeek}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.sixMonthsColors.textSecondary,
                    )
                }
            }
        }

        val nextReminder = remember(state.settings) {
            ReminderScheduler.nextOccurrence(state.settings)?.let { next ->
                next.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) +
                    " · " + state.settings.reminderTime
            }
        }
        SixMonthsCard {
            Text(
                text = "NEXT REMINDER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = nextReminder ?: "—",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        SixMonthsCard {
            SecondaryAction(text = "Music", onClick = onOpenMusic)
            SecondaryAction(text = "Stats", onClick = onOpenStats)
        }

    }

        if (showConfetti && animationsEnabled) {
            ConfettiOverlay(
                modifier = Modifier.fillMaxSize(),
                onFinished = { showConfetti = false },
            )
        }
    }

    // Weekly trophy: black screen, big trophy, one line, two seconds, then back to the app.
    LaunchedEffect(showTrophy) {
        if (!showTrophy) return@LaunchedEffect
        delay(2_000)
        showTrophy = false
        ServiceLocator.settingsRepository.setLastTrophyWeek(weekStartIso)
    }

    if (showTrophy) {
        CelebrationOverlay(
            title = "you made one more week",
            rawRes = HouseAnimation.TROPHY,
            withConfetti = true,
            onDismiss = {
                showTrophy = false
                scope.launch {
                    ServiceLocator.settingsRepository.setLastTrophyWeek(weekStartIso)
                }
            },
        )
    }

    if (showProgression && state.progressionDue) {
        val suggestion = PlanProgression.suggestion(state.completedThisPhase)
        AlertDialog(
            onDismissRequest = {
                showProgression = false
                progressionHandled = true
            },
            title = { Text("Phase ${state.settings.planPhase} complete") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(suggestion.title, style = MaterialTheme.typography.titleMedium)
                    Text(suggestion.detail, style = MaterialTheme.typography.bodyMedium)

                }
            },
            confirmButton = {
                AppTextButton(onClick = {
                    scope.launch {
                        when (suggestion.action) {
                            ProgressionAction.ADD_REP -> ServiceLocator.planRepository.increaseReps()
                            ProgressionAction.ADD_SET -> ServiceLocator.planRepository.increaseSets()
                            ProgressionAction.DELOAD -> ServiceLocator.planRepository.deload()
                        }
                        ServiceLocator.settingsRepository.edit {
                            it.copy(
                                planPhase = it.planPhase + 1,
                                planPhaseStartedAt = System.currentTimeMillis(),
                            )
                        }
                        showProgression = false
                        progressionHandled = true
                        showConfetti = true
                    }
                }) {
                    Text("Apply", color = MaterialTheme.sixMonthsColors.flameEnd)
                }
            },
            dismissButton = {
                AppTextButton(onClick = {
                    showProgression = false
                    progressionHandled = true
                }) {
                    Text("Not now")
                }
            },
        )
    }
}

@Composable
private fun TodayEntries(day: DaySnapshot) {
    day.entries.take(4).forEach { entry ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = ExerciseCategory.displayName(entry.category),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }
            SetsRepsBadge(sets = entry.sets, reps = entry.reps, unit = entry.unit)
        }
    }
    if (day.entries.size > 4) {
        Text(
            text = "+${day.entries.size - 4} more",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.sixMonthsColors.textMuted,
        )
    }
}

@Composable
private fun StatPill(label: String, value: Int) {
    Column {
        RollingNumberSmall(
            value = value,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.sixMonthsColors.textMuted,
        )
    }
}

/**
 * The streak flame: the bundled Fire Lottie animation, quiet at 62 dp. A zero streak shows
 * the same flame dimmed, so the card never changes shape.
 */
@Composable
private fun StreakFlame(streak: Int) {
    HouseLottie(
        rawRes = HouseAnimation.FIRE,
        contentDescription = "Current streak: $streak days",
        modifier = Modifier
            .size(62.dp)
            .graphicsLayer { alpha = if (streak > 0) 1f else 0.28f },
    )
}

@Composable
private fun WeeklyRing(completed: Int, planned: Int) {
    val animate = animationsEnabled
    val target = if (planned <= 0) 0f else (completed.toFloat() / planned).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = if (animate) tween(durationMillis = 700) else snap(),
        label = "weeklyProgress",
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    Box(modifier = Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                // Soft glow behind the filled part of the ring.
                drawArc(
                    color = accent.copy(alpha = 0.16f),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = stroke * 2.1f, cap = StrokeCap.Round),
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = if (planned <= 0) "—" else "$completed/$planned",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
