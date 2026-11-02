package com.sixmonths.app.domain

import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.core.model.SessionStatus
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.entity.Session
import com.sixmonths.app.data.entity.SetLog
import com.sixmonths.app.data.model.PlanEntryRow
import com.sixmonths.app.data.model.PlanSnapshot
import java.time.DayOfWeek
import java.time.LocalDate

enum class WeeklyAction { INCREASE, KEEP, REGRESS }

data class WeeklyEntrySuggestion(
    val entry: PlanEntryRow,
    val dayOfWeek: Int,
    val completedSets: Int,
    val plannedSets: Int,
    val lastSetReps: Int,
    val action: WeeklyAction,
    val suggestedSets: Int,
    val suggestedReps: Int,
) {
    val unitLabel: String
        get() = if (entry.unit == ExerciseUnit.SECONDS) "s" else ""

    val headline: String
        get() = when (action) {
            WeeklyAction.INCREASE -> "Increase to ${suggestedSets} × ${suggestedReps}${unitLabel}"
            WeeklyAction.REGRESS -> "Regress to ${suggestedSets} × ${suggestedReps}${unitLabel}"
            WeeklyAction.KEEP -> "Keep ${entry.sets} × ${entry.reps}${unitLabel}"
        }

    val detail: String
        get() = when (action) {
            WeeklyAction.INCREASE ->
                "All $plannedSets sets were completed cleanly; the last set hit the target."
            WeeklyAction.KEEP ->
                "Completed $completedSets of $plannedSets sets, last set $lastSetReps${unitLabel}. Hold this step."
            WeeklyAction.REGRESS ->
                "No set was logged for this training. One step back keeps the session doable."
        }
}

/**
 * Rule-based progression for one completed Monday–Sunday week. Inputs are only stored
 * sessions, set logs and plan targets; nothing is inferred and nothing is applied without
 * the user confirming each row.
 */
object WeeklyProgression {

    /** Start (Monday) of the last fully completed week. */
    fun completedWeekStart(today: LocalDate): LocalDate {
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val start = if (thisMonday.isAfter(today)) thisMonday.minusWeeks(1) else thisMonday
        return start.minusWeeks(1)
    }

    fun analyze(
        plan: PlanSnapshot,
        sessions: List<Session>,
        logsBySession: Map<String, List<SetLog>>,
        weekStart: LocalDate,
    ): List<WeeklyEntrySuggestion> {
        val weekEnd = weekStart.plusDays(7)
        val completedSessions = sessions.filter { session ->
            session.status == SessionStatus.COMPLETED &&
                DateUtils.toLocalDate(session.startedAt).let { it >= weekStart && it < weekEnd }
        }

        return plan.days.flatMap { day ->
            day.entries.mapNotNull { entry ->
                val session = completedSessions
                    .filter { it.planDayId == day.planDay.id }
                    .maxByOrNull { it.startedAt }
                    ?: return@mapNotNull null

                val logs = logsBySession[session.id]
                    .orEmpty()
                    .filter { it.exerciseId == entry.exerciseId }
                    .sortedBy { it.setIndex }
                if (logs.isEmpty() && session.status != SessionStatus.COMPLETED) return@mapNotNull null

                val completedSets = logs.size
                val lastReps = logs.lastOrNull()?.repsCompleted ?: 0
                val step = if (entry.unit == ExerciseUnit.SECONDS) 10 else 1
                val action = when {
                    completedSets >= entry.sets && lastReps >= entry.reps -> WeeklyAction.INCREASE
                    completedSets == 0 -> WeeklyAction.REGRESS
                    else -> WeeklyAction.KEEP
                }
                val suggestedReps = when (action) {
                    WeeklyAction.INCREASE -> (entry.reps + step).coerceAtMost(
                        if (entry.unit == ExerciseUnit.SECONDS) 600 else 100,
                    )
                    WeeklyAction.REGRESS -> (entry.reps - step).coerceAtLeast(
                        if (entry.unit == ExerciseUnit.SECONDS) 10 else 1,
                    )
                    WeeklyAction.KEEP -> entry.reps
                }
                WeeklyEntrySuggestion(
                    entry = entry,
                    dayOfWeek = day.planDay.dayOfWeek,
                    completedSets = completedSets,
                    plannedSets = entry.sets,
                    lastSetReps = lastReps,
                    action = action,
                    suggestedSets = entry.sets,
                    suggestedReps = suggestedReps,
                )
            }
        }
    }
}
