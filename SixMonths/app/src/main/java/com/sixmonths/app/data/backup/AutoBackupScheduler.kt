package com.sixmonths.app.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.sixmonths.app.data.prefs.Settings
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The optional weekly backup: one run every seven days, first at the next Sunday 04:00.
 *
 * KEEP instead of UPDATE on purpose — the schedule is synced on every resume, and replacing
 * the work each time would keep pushing the run into the future, so the backup would never
 * happen for someone who opens the app daily. The worker re-reads the settings, so a changed
 * folder needs no rescheduling.
 */
object AutoBackupScheduler {

    private const val WORK_NAME = "six_months_weekly_backup"

    fun sync(context: Context, settings: Settings) {
        val tree = settings.autoBackupTreeUri
        if (!settings.autoBackupEnabled || tree.isNullOrBlank()) {
            cancel(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMillis(), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Next Sunday 04:00 local time; WorkManager may drift a little, which is fine here. */
    internal fun initialDelayMillis(now: ZonedDateTime = ZonedDateTime.now()): Long {
        var target = now.toLocalDate().atTime(4, 0).atZone(now.zone)
        if (!target.isAfter(now)) target = target.plusDays(1)
        while (target.dayOfWeek != DayOfWeek.SUNDAY) target = target.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(0L)
    }
}
