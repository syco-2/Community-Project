package com.sixmonths.app.ui.screens.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.ui.components.PlaceholderScaffold
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.theme.sixMonthsColors
import kotlinx.coroutines.launch

/**
 * Onboarding step 6: a message to the future self. It is stored locally and stays locked
 * until the six-month phase is complete.
 */
@Composable
fun OnboardingFutureMessageScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    var message by remember { mutableStateOf(TextFieldValue("")) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ServiceLocator.settingsRepository.setOnboardingStep(6)
    }

    LaunchedEffect(settings) {
        val current = settings ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        message = TextFieldValue(current.futureMessage.orEmpty())
        loaded = true
    }

    PlaceholderScaffold(
        title = "Future message",
        summary = null,
        onBack = onBack,
        progress = 6 to 7,
        footer = {
            PrimaryAction(
                text = "Lock this message",
                onClick = {
                    scope.launch {
                        ServiceLocator.settingsRepository.setFutureMessage(message.text)
                        ServiceLocator.settingsRepository.setFutureMessageRevealed(false)
                        onContinue()
                    }
                },
            )
            SecondaryAction(text = "Back", onClick = onBack)
        },
    ) {
        SixMonthsCard {
            OutlinedTextField(
                value = message,
                onValueChange = { updated ->
                    message = if (updated.text.length <= 2_000) updated else updated.copy(
                        text = updated.text.take(2_000),
                        selection = updated.selection.constrainWithin(updated.text.take(2_000)),
                    )
                },
                label = { Text("Dear future me…") },
                minLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
            )
            Text(
                text = "${message.text.length} / 2000",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.sixMonthsColors.textMuted,
            )
        }

        SixMonthsCard {
            Text(
                text = "Unlocks after six months",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun androidx.compose.ui.text.TextRange.constrainWithin(text: String) =
    androidx.compose.ui.text.TextRange(
        start = start.coerceIn(0, text.length),
        end = end.coerceIn(0, text.length),
    )
