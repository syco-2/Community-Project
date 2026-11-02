package com.sixmonths.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.domain.SixMonthCycle
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.theme.sixMonthsColors

@Composable
fun ProfileScreen(
    onOpenStats: () -> Unit,
    onOpenMusic: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFutureMessage: () -> Unit,
) {
    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val streak by ServiceLocator.streakDao.observeState()
        .collectAsStateWithLifecycle(initialValue = null)

    val current = settings
    val unlocked = current?.let { SixMonthCycle.isUnlocked(it) } ?: false
    val remaining = current?.let { SixMonthCycle.daysRemaining(it) } ?: SixMonthCycle.DAYS
    val messageStatus = remember(current?.futureMessage, unlocked, remaining) {
        when {
            current?.futureMessage.isNullOrBlank() -> "No message written"
            unlocked -> "Ready to unlock"
            else -> "Locked · $remaining days"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
            // Clear the floating tab bar at the end of the scroll.
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Profile",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

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
                    Text(
                        text = "${streak?.currentStreak ?: 0} days",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Longest ${streak?.longestStreak ?: 0}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.sixMonthsColors.textSecondary,
                    )
                    Text(
                        text = "Phase ${current?.planPhase ?: 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.sixMonthsColors.textMuted,
                    )
                }
            }
        }

        SixMonthsCard {
            Text(
                text = "Future message",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = messageStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = if (unlocked) MaterialTheme.sixMonthsColors.success else MaterialTheme.sixMonthsColors.textSecondary,
            )
            SecondaryAction(text = "Open future message", onClick = onOpenFutureMessage)
        }

        SixMonthsCard {
            SecondaryAction(text = "Stats", onClick = onOpenStats)
            SecondaryAction(text = "Music", onClick = onOpenMusic)
            SecondaryAction(text = "Settings", onClick = onOpenSettings)
        }

        Text(
            text = "made by Syco",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            color = MaterialTheme.sixMonthsColors.textMuted,
        )
    }
}
