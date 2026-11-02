package com.sixmonths.app.domain

import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.prefs.Settings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The six-month phase that unlocks the future message. */
object SixMonthCycle {

    const val DAYS: Long = 182L

    /** Local date on which the message unlocks, or null before the first cycle started. */
    fun unlockDate(settings: Settings): LocalDate? =
        if (settings.planPhaseStartedAt <= 0L) null
        else DateUtils.toLocalDate(settings.planPhaseStartedAt).plusDays(DAYS)

    fun isUnlocked(settings: Settings, today: LocalDate = DateUtils.today()): Boolean {
        val unlock = unlockDate(settings) ?: return false
        return !today.isBefore(unlock)
    }

    fun daysRemaining(settings: Settings, today: LocalDate = DateUtils.today()): Long {
        val unlock = unlockDate(settings) ?: return DAYS
        return ChronoUnit.DAYS.between(today, unlock).coerceAtLeast(0)
    }
}
