package com.sixmonths.app.ui.screens.onboarding

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.core.model.Weekday
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.prefs.Settings
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.notifications.NotificationHelper
import com.sixmonths.app.ui.components.PlaceholderScaffold
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.theme.sixMonthsColors
import kotlinx.coroutines.launch
import java.util.Locale

/** Onboarding step 4: reminder time and days, notification permission. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingRemindersScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val plan by ServiceLocator.planRepository.observePlan()
        .collectAsStateWithLifecycle(initialValue = null)

    var reminderDays by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var reminderTime by remember { mutableStateOf(Settings.DEFAULT_REMINDER_TIME) }
    var loaded by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var notificationsGranted by remember { mutableStateOf(NotificationHelper.canNotify(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        notificationsGranted = NotificationHelper.canNotify(context)
    }

    LaunchedEffect(settings, plan) {
        val current = settings ?: return@LaunchedEffect
        val currentPlan = plan ?: return@LaunchedEffect
        if (loaded) return@LaunchedEffect
        val derivedDays = current.reminderDays.ifEmpty {
            currentPlan.trainingDays.map { it.planDay.dayOfWeek }.toSet()
        }
        reminderDays = derivedDays
        reminderTime = current.reminderTime
        loaded = true
        ServiceLocator.settingsRepository.setOnboardingStep(4)
        if (current.reminderDays.isEmpty() && derivedDays.isNotEmpty()) {
            ServiceLocator.settingsRepository.setReminderDays(derivedDays)
        }
    }

    LaunchedEffect(showTimePicker) {
        if (!showTimePicker) return@LaunchedEffect
        val current = DateUtils.parseReminderTime(reminderTime)
        val dialog = TimePickerDialog(
            context,
            { _, hour, minute ->
                val formatted = String.format(Locale.ENGLISH, "%02d:%02d", hour, minute)
                reminderTime = formatted
                scope.launch {
                    ServiceLocator.settingsRepository.setReminderTime(formatted)
                }
                showTimePicker = false
            },
            current.hour,
            current.minute,
            true,
        )
        dialog.setOnCancelListener { showTimePicker = false }
        dialog.show()
    }

    PlaceholderScaffold(
        title = "Reminders",
        summary = null,
        onBack = onBack,
        progress = 4 to 7,
        footer = {
            PrimaryAction(text = "Continue", onClick = onContinue)
            SecondaryAction(text = "Back", onClick = onBack)
        },
    ) {
        SixMonthsCard {
            Text(
                text = "Reminder time",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = reminderTime,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                SecondaryAction(
                    text = "Change time",
                    onClick = { showTimePicker = true },
                    modifier = Modifier.fillMaxWidth(0.45f),
                )
            }
            Text(
                text = "Reminder days",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.sixMonthsColors.textSecondary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Weekday.ALL.forEach { day ->
                    val selected = day in reminderDays
                    FilterChip(
                        selected = selected,
                        onClick = {
                            reminderDays = if (selected) reminderDays - day else reminderDays + day
                            scope.launch {
                                ServiceLocator.settingsRepository.setReminderDays(reminderDays)
                            }
                        },
                        label = {
                            Text(
                                text = Weekday.shortName(day),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                    )
                }
            }
        }

        SixMonthsCard {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (notificationsGranted) {
                Text(
                    text = "Notifications allowed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.sixMonthsColors.success,
                )
            } else {
                SecondaryAction(
                    text = "Enable notifications",
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationsGranted = NotificationHelper.canNotify(context)
                        }
                    },
                )
            }
        }

    }
}
