package com.sixmonths.app.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.prefs.Settings
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Training reminders from the set time on, repeated every minute until the workout is started.
 * Inexact on purpose: no SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM is requested.
 *
 * One unique chain of one-time work requests does the repeating, because periodic WorkManager
 * work cannot run more often than every 15 minutes.
 */
object ReminderScheduler {

    private const val WORK_NAME = "six_months_training_reminder"

    /** Gap between two reminders while today's workout has not been started. */
    const val REPEAT_INTERVAL_MILLIS = 60_000L

    /** Starts (or restarts) the chain at the next reminder window. */
    fun schedule(context: Context, settings: Settings) {
        val next = nextOccurrence(settings)
        if (next == null) {
            cancel(context)
            return
        }
        val delayMillis = Duration.between(ZonedDateTime.now(), next)
            .toMillis()
            .coerceAtLeast(0L)
        enqueue(context, delayMillis, ExistingWorkPolicy.REPLACE)
    }

    /** Queues the next minute of the running chain without restarting it. */
    fun scheduleRepeat(context: Context) {
        enqueue(context, REPEAT_INTERVAL_MILLIS, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** True once today is a selected reminder day and the set time has passed. */
    fun reminderWindowOpen(
        settings: Settings,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Boolean {
        val today = now.toLocalDate()
        if (today.dayOfWeek.value !in settings.reminderDays) return false
        val start = today.atTime(DateUtils.parseReminderTime(settings.reminderTime)).atZone(now.zone)
        return !now.isBefore(start)
    }

    /** Next reminder strictly in the future, looking at most one week ahead. */
    fun nextOccurrence(
        settings: Settings,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): ZonedDateTime? {
        if (settings.reminderDays.isEmpty()) return null
        val time = DateUtils.parseReminderTime(settings.reminderTime)
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (date.dayOfWeek.value !in settings.reminderDays) continue
            val candidate = date.atTime(time).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    private fun enqueue(context: Context, delayMillis: Long, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, policy, request)
    }
}
