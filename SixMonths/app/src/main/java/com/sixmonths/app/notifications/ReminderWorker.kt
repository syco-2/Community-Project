package com.sixmonths.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.di.ServiceLocator
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * Inexact WorkManager reminder that repeats every minute.
 *
 * Outside the reminder window nothing is posted and the next window is scheduled. Inside the
 * window it posts one notification and queues the next minute, until today's plan is done or
 * the user has started the workout — then it stops for the day.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        NotificationHelper.ensureChannel(context)
        return runCatching { deliver(context) }
            .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    private suspend fun deliver(context: Context) {
        val settings = ServiceLocator.settingsRepository.current()

        if (!settings.onboardingCompleted) {
            ReminderScheduler.cancel(context)
            return
        }

        val now = ZonedDateTime.now()
        if (!ReminderScheduler.reminderWindowOpen(settings, now)) {
            // Before the set time, or not a reminder day: wait for the next window.
            ReminderScheduler.schedule(context, settings)
            return
        }

        if (nothingLeftToRemind(now.toLocalDate())) {
            ReminderScheduler.schedule(context, settings)
            return
        }

        NotificationHelper.showTrainingReminder(
            context = context,
            title = "Training day",
            text = "Your plan is ready.",
        )
        ServiceLocator.settingsRepository.setLastReminderDay(now.toLocalDate().toString())
        ReminderScheduler.scheduleRepeat(context)
    }

    /** True on rest days, on days without entries, and once a session exists for today. */
    private suspend fun nothingLeftToRemind(today: LocalDate): Boolean {
        val plan = ServiceLocator.planRepository.loadPlan()
        val todayDay = plan.days.firstOrNull { it.planDay.dayOfWeek == today.dayOfWeek.value }
        if (todayDay == null || !todayDay.isTrainingDay || todayDay.entries.isEmpty()) {
            return true
        }
        val from = DateUtils.toEpochMillis(today)
        val to = DateUtils.toEpochMillis(today.plusDays(1))
        return ServiceLocator.workoutRepository.sessionsBetween(from, to).isNotEmpty()
    }
}
