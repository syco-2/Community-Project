package com.sixmonths.app.ui.screens.dashboard

import android.content.Context
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.notifications.ReminderScheduler

/**
 * Re-schedules WorkManager after reminder settings changed. Reminders are the only thing
 * WorkManager is used for.
 */
suspend fun scheduleReminderAfterSettingsChange(context: Context) {
    val settings = ServiceLocator.settingsRepository.current()
    if (settings.onboardingCompleted && settings.reminderDays.isNotEmpty()) {
        ReminderScheduler.schedule(context, settings)
    } else {
        ReminderScheduler.cancel(context)
    }
}
