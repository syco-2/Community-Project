package com.sixmonths.app.domain

import com.sixmonths.app.data.prefs.Settings
import java.util.concurrent.TimeUnit

enum class ProgressionAction { ADD_REP, ADD_SET, DELOAD }

data class ProgressionSuggestion(
    val action: ProgressionAction,
    val title: String,
    val detail: String,
)

/**
 * Suggestions after a completed phase. The phase length is 6 weeks; progression is never
 * applied automatically, the user confirms the suggested change first.
 */
object PlanProgression {

    const val PHASE_LENGTH_DAYS: Long = 42L

    fun isDue(settings: Settings, now: Long = System.currentTimeMillis()): Boolean {
        if (!settings.onboardingCompleted) return false
        if (settings.planPhaseStartedAt <= 0L) return false
        val elapsed = now - settings.planPhaseStartedAt
        return elapsed >= TimeUnit.DAYS.toMillis(PHASE_LENGTH_DAYS)
    }

    fun suggestion(completedSessionsInPhase: Int): ProgressionSuggestion = when {
        completedSessionsInPhase >= 16 -> ProgressionSuggestion(
            action = ProgressionAction.ADD_SET,
            title = "Add one set",
            detail = "You completed $completedSessionsInPhase sessions this phase. Add a set to every entry to keep progressing.",
        )

        completedSessionsInPhase >= 8 -> ProgressionSuggestion(
            action = ProgressionAction.ADD_REP,
            title = "Add reps or seconds",
            detail = "You completed $completedSessionsInPhase sessions this phase. Add 1 rep (or 10 seconds) to every entry.",
        )

        else -> ProgressionSuggestion(
            action = ProgressionAction.DELOAD,
            title = "Deload this phase",
            detail = "Only $completedSessionsInPhase sessions were completed this phase. Reducing reps by ~20% may help you restart.",
        )
    }
}
