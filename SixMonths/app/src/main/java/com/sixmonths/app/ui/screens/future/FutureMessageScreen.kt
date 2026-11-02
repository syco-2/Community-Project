package com.sixmonths.app.ui.screens.future

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.core.util.rememberHaptics
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.domain.SixMonthCycle
import com.sixmonths.app.playback.SoundFx
import com.sixmonths.app.ui.components.ConfettiOverlay
import com.sixmonths.app.ui.components.PlaceholderScaffold
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.theme.animationsEnabled
import com.sixmonths.app.ui.theme.sixMonthsColors
import kotlinx.coroutines.launch

/**
 * The future message stays locked until [SixMonthCycle] says the phase is complete. The
 * reveal is explicit: the app never shows the text early, and the two cycle buttons are
 * the only place the phase restarts.
 */
@Composable
fun FutureMessageScreen(
    onBack: () -> Unit,
    onStartAgain: () -> Unit,
    onContinue: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val animate = animationsEnabled
    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    var showConfetti by remember { mutableStateOf(false) }
    var revealing by remember { mutableStateOf(false) }

    val current = settings
    val unlocked = current?.let { SixMonthCycle.isUnlocked(it) } ?: false
    val remaining = current?.let { SixMonthCycle.daysRemaining(it) } ?: SixMonthCycle.DAYS
    val message = current?.futureMessage
    val revealed = current?.futureMessageRevealed == true || revealing

    Box(modifier = Modifier.fillMaxSize()) {
    PlaceholderScaffold(
        title = "Future message",
        summary = null,
        onBack = onBack,
        footer = {
            if (revealed && unlocked && !message.isNullOrBlank()) {
                PrimaryAction(
                    text = "Continue with last plan",
                    onClick = {
                        scope.launch {
                            val now = System.currentTimeMillis()
                            ServiceLocator.settingsRepository.edit {
                                it.copy(
                                    onboardingCompleted = true,
                                    planPhase = 1,
                                    planPhaseStartedAt = now,
                                    cycleStartedAt = now,
                                    cycleIsExplicit = true,
                                    futureMessageRevealed = false,
                                    lastProgressionReviewWeek = null,
                                )
                            }
                            ServiceLocator.streakEngine.resetForNewCycle()
                            onContinue()
                        }
                    },
                )
                SecondaryAction(
                    text = "Start again",
                    onClick = {
                        scope.launch {
                            val now = System.currentTimeMillis()
                            ServiceLocator.settingsRepository.edit {
                                it.copy(cycleStartedAt = now, cycleIsExplicit = true)
                            }
                            ServiceLocator.settingsRepository.resetForNewOnboarding()
                            ServiceLocator.streakEngine.resetForNewCycle()
                            onStartAgain()
                        }
                    },
                )
            }
        },
    ) {
        if (current == null) {
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.sixMonthsColors.textSecondary,
            )
            return@PlaceholderScaffold
        }

        when {
            message.isNullOrBlank() -> SixMonthsCard {
                Text(
                    text = "No message was written",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            !unlocked -> LockedMessage(remaining = remaining)

            !revealed -> SixMonthsCard {
                Text(
                    text = "The phase is complete",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.sixMonthsColors.flameEnd,
                )
                PrimaryAction(
                    text = "Unlock message",
                    onClick = {
                        haptics.confirm()
                        revealing = true
                        showConfetti = animate
                        scope.launch {
                            ServiceLocator.settingsRepository.setFutureMessageRevealed(true)
                        }
                    },
                )
            }

            else -> {
                AnimatedVisibility(
                    visible = true,
                    enter = if (animate) {
                        fadeIn(tween(500)) + scaleIn(spring(dampingRatio = 0.5f), initialScale = 0.86f)
                    } else {
                        fadeIn(tween(0))
                    },
                ) {
                    SixMonthsCard {
                        Text(
                            text = "DEAR FUTURE ME",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.sixMonthsColors.flameEnd,
                        )
                        Text(
                            text = message.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

    }

    if (showConfetti && animate) {
        ConfettiOverlay(
            modifier = Modifier.fillMaxSize(),
            onFinished = { showConfetti = false },
        )
    }
    }
}

@Composable
private fun LockedMessage(remaining: Long) {
    val animate = animationsEnabled
    val transition = rememberInfiniteTransition(label = "futureLock")
    val glow by if (animate) {
        transition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_600), RepeatMode.Reverse),
            label = "lockGlow",
        )
    } else {
        remember { mutableStateOf(0.8f) }
    }
    val textAlpha by animateFloatAsState(
        targetValue = glow,
        label = "lockAlpha",
    )
    val gold = MaterialTheme.sixMonthsColors.flameEnd

    SixMonthsCard {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(120.dp)) {
                drawCircle(
                    color = gold.copy(alpha = 0.10f * textAlpha),
                    radius = size.minDimension / 2f,
                )
                drawCircle(
                    color = gold.copy(alpha = 0.35f * textAlpha),
                    radius = size.minDimension / 2.6f,
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawCircle(
                    color = gold.copy(alpha = 0.45f),
                    radius = 3.dp.toPx(),
                    center = Offset(size.width * 0.5f, size.height * 0.18f),
                )
            }
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = gold.copy(alpha = textAlpha),
                modifier = Modifier.size(46.dp),
            )
        }
        Text(
            text = "Locked",
            style = MaterialTheme.typography.headlineSmall,
            color = gold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "$remaining days",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.sixMonthsColors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}
