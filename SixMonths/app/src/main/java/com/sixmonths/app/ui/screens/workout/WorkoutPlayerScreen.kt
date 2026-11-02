package com.sixmonths.app.ui.screens.workout

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.util.rememberHaptics
import com.sixmonths.app.domain.TrainingBlock
import com.sixmonths.app.playback.SoundFx
import com.sixmonths.app.ui.components.AppIconButton
import com.sixmonths.app.ui.components.AppTextButton
import com.sixmonths.app.ui.components.CelebrationOverlay
import com.sixmonths.app.ui.components.HouseAnimation
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.SetsRepsBadge
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.components.SkeletonRows
import com.sixmonths.app.ui.components.springPress
import com.sixmonths.app.ui.theme.sixMonthsColors
import com.sixmonths.app.ui.util.viewModelFactory
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * The workout player: one set at a time.
 *
 * Timed work gets a big stopwatch that the user starts, pauses and resets. Rep work gets a
 * big rep counter. There is no rest timer — logging a set moves straight to the next one and
 * the user rests as long as they like. The set is written to Room immediately, so closing the
 * app mid-workout keeps every logged set and reopens on the next open set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutPlayerScreen(
    sessionId: String?,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    if (sessionId.isNullOrBlank()) {
        MissingSessionScreen(onBack = onBack)
        return
    }

    val viewModel: WorkoutViewModel = viewModel(
        key = "workout-$sessionId",
        factory = viewModelFactory { WorkoutViewModel(sessionId) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    // Screen stays on for the whole session, so no unlock between two sets.
    val rootView = LocalView.current
    DisposableEffect(rootView) {
        rootView.keepScreenOn = true
        onDispose { rootView.keepScreenOn = false }
    }
    var showCelebration by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    var finishRequested by remember { mutableStateOf(false) }

    fun leave() {
        if (leaving) return
        leaving = true
        showCelebration = false
        onFinished()
    }

    // Finished sessions always end the player: black screen, big flame, one line, two seconds.
    LaunchedEffect(state.completed, finishRequested) {
        if (!state.completed || !finishRequested) return@LaunchedEffect
        haptics.heartbeat()
        showCelebration = true
        delay(2_000)
        leave()
    }

    // Leaving the player no longer abandons the session: the active session and its logged
    // sets stay in Room, and the dashboard resumes it on the next visit.
    BackHandler { onBack() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.day?.planDay?.name ?: "Workout",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.loading) {
                SkeletonRows(rows = 3, rowHeight = 120.dp)
                return@Column
            }

            if (state.entries.isEmpty()) {
                SixMonthsCard {
                    state.error?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "Nothing planned for this day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.sixMonthsColors.textSecondary,
                    )
                    SecondaryAction(text = "Back", onClick = onBack)
                }
                return@Column
            }

            val entry = state.currentEntry
            if (entry == null) {
                SixMonthsCard {
                    Text(
                        text = "This workout is finished.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Round ${state.round} of ${state.rounds} · " +
                        "${state.setsLogged} / ${state.totalSetsPlanned} sets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textSecondary,
                )
                BlockBadge(block = state.currentBlock)
            }
            LinearProgressIndicator(
                progress = {
                    if (state.totalSetsPlanned == 0) {
                        0f
                    } else {
                        state.setsLogged.toFloat() / state.totalSetsPlanned
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            SixMonthsCard {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SetsRepsBadge(sets = entry.sets, reps = entry.reps, unit = entry.unit)
                }
                Text(
                    text = ExerciseCategory.displayName(entry.category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(entry.sets) { index ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < state.currentSetIndex) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                        )
                    }
                }
                entry.formTip?.let { tip ->
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.sixMonthsColors.success,
                    )
                }
            }

            if (state.isTimeBased) {
                StopwatchPanel(
                    seconds = state.stopwatchSeconds,
                    countdown = state.countdownSeconds,
                    running = state.stopwatchRunning,
                    enabled = !state.logging && !state.finishing,
                    onToggle = viewModel::toggleTimer,
                    onReset = viewModel::resetTimer,
                )
            } else {
                RepCounterPanel(
                    value = state.currentValue,
                    enabled = !state.logging && !state.finishing,
                    onValueChange = viewModel::setCurrentValue,
                )
            }

            PrimaryAction(
                text = when {
                    state.logging -> "Saving…"
                    state.isTimeBased -> "Log ${formatTime(state.stopwatchSeconds)}"
                    else -> "Log set ${state.currentSetIndex + 1} of ${entry.sets}"
                },
                enabled = !state.finishing && !state.allDone && !state.logging,
                onClick = {
                    // One of the only two sound moments in the app: completing a set.
                    SoundFx.popForSet(state.currentSetIndex + 1)
                    haptics.thud()
                    viewModel.logSet()
                },
            )

            if (state.allDone) {
                PrimaryAction(
                    text = if (state.finishing) "Saving…" else "Finish workout",
                    onClick = {
                        finishRequested = true
                        viewModel.finishWorkout()
                    },
                    enabled = !state.finishing,
                )
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.sixMonthsColors.danger,
                )
            }
        }
    }

    // Drawn after the Scaffold so the black celebration covers the whole player.
    if (showCelebration) {
        CelebrationOverlay(
            title = "you made one more day",
            rawRes = HouseAnimation.FIRE,
            onDismiss = { leave() },
        )
    }
}

/** Large, quiet stopwatch for time-based exercises. */
@Composable
private fun StopwatchPanel(
    seconds: Int,
    countdown: Int,
    running: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onReset: () -> Unit,
) {
    SixMonthsCard {
        Text(
            text = if (countdown > 0) "$countdown" else formatTime(seconds),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (countdown > 0) {
            Text(
                text = "starting in",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.sixMonthsColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppTextButton(enabled = enabled, onClick = onReset) {
                Text("Reset", color = MaterialTheme.sixMonthsColors.textMuted)
            }
            PrimaryAction(
                text = when {
                    countdown > 0 -> "Start now"
                    running -> "Pause"
                    else -> "Start"
                },
                onClick = onToggle,
                enabled = enabled,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Big rep counter: − number +, nothing else. */
@Composable
private fun RepCounterPanel(
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    SixMonthsCard {
        Text(
            text = "$value",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CounterButton(label = "−", enabled = enabled && value > 1) {
                onValueChange(value - 1)
            }
            CounterButton(label = "+", enabled = enabled && value < 200) {
                onValueChange(value + 1)
            }
        }
    }
}

@Composable
private fun CounterButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
            )
            .springPress(interaction, enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptics.tick()
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.sixMonthsColors.textMuted
            },
        )
    }
}

/** The current circuit block, colour-coded so the order is obvious at a glance. */
@Composable
private fun BlockBadge(block: TrainingBlock) {
    val accent = when (block) {
        TrainingBlock.SKILL -> MaterialTheme.colorScheme.primary
        TrainingBlock.PLANCHE -> MaterialTheme.colorScheme.secondary
        TrainingBlock.NORMAL -> MaterialTheme.sixMonthsColors.textSecondary
    }
    Text(
        text = block.label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun MissingSessionScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No active session",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryAction(text = "Back", onClick = onBack)
    }
}

private fun formatTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    return String.format(Locale.ENGLISH, "%d:%02d", safe / 60, safe % 60)
}
