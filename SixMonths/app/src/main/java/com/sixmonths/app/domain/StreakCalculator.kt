package com.sixmonths.app.domain

import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.entity.StreakState
import java.time.LocalDate

data class StreakInput(
    val trainingWeekdays: Set<Int>,
    val completedDates: Set<LocalDate>,
    val state: StreakState,
    val today: LocalDate,
)

data class StreakOutput(
    val state: StreakState,
    val missedTrainingDays: Int,
    val frozenMisses: Int,
    val unprotectedMisses: Int,
    val hasTrainingDays: Boolean,
)

/**
 * Pure streak rules. No Android, no Room, no clock: every input is explicit, which makes
 * the tricky midnight-rollover and freeze-token behaviour unit testable.
 */
object StreakCalculator {

    fun evaluate(input: StreakInput): StreakOutput {
        var state = input.state

        if (input.trainingWeekdays.isEmpty()) {
            // No TRAINING days configured: the streak is paused.
            return StreakOutput(state, 0, 0, 0, hasTrainingDays = false)
        }

        val start = DateUtils.parseOrNull(state.lastEvaluatedDate)
            ?: DateUtils.parseOrNull(state.lastCompletedDate)
            ?: input.today.minusDays(1)

        var cursor = start.plusDays(1)
        var missed = 0
        var frozen = 0
        var unprotected = 0

        while (cursor.isBefore(input.today)) {
            if (cursor.dayOfWeek.value in input.trainingWeekdays) {
                if (cursor in input.completedDates) {
                    if (state.lastCompletedDate != cursor.toString()) {
                        state = state.withStreakDay(cursor.toString())
                    }
                } else {
                    missed += 1
                    if (state.freezeTokens > 0) {
                        frozen += 1
                        state = state.copy(freezeTokens = state.freezeTokens - 1)
                    } else {
                        unprotected += 1
                        state = state.copy(currentStreak = 0)
                    }
                }
            }
            state = state.copy(lastEvaluatedDate = cursor.toString())
            cursor = cursor.plusDays(1)
        }

        // A session that finished today might not be folded in yet (process death).
        val todayIso = input.today.toString()
        if (input.today.dayOfWeek.value in input.trainingWeekdays &&
            input.today in input.completedDates &&
            state.lastCompletedDate != todayIso
        ) {
            state = state.withStreakDay(todayIso)
        }

        return StreakOutput(
            state = state,
            missedTrainingDays = missed,
            frozenMisses = frozen,
            unprotectedMisses = unprotected,
            hasTrainingDays = true,
        )
    }

    private fun StreakState.withStreakDay(isoDate: String): StreakState {
        val next = currentStreak + 1
        return copy(
            currentStreak = next,
            longestStreak = maxOf(longestStreak, next),
            lastCompletedDate = isoDate,
        )
    }
}
