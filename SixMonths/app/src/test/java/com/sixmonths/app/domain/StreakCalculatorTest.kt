package com.sixmonths.app.domain

import com.sixmonths.app.data.entity.StreakState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    // 2026-10-26 is a Monday; the same week has Wed 21 and Fri 23.
    private val today: LocalDate = LocalDate.of(2026, 10, 26)
    private val trainingWeekdays = setOf(1, 3, 5)

    private fun state(
        currentStreak: Int = 0,
        longestStreak: Int = 0,
        lastCompletedDate: String? = null,
        lastEvaluatedDate: String? = null,
        freezeTokens: Int = 0,
    ) = StreakState(
        currentStreak = currentStreak,
        longestStreak = longestStreak,
        lastCompletedDate = lastCompletedDate,
        lastEvaluatedDate = lastEvaluatedDate,
        freezeTokens = freezeTokens,
    )

    @Test
    fun `no history and no training on today does nothing`() {
        val output = StreakCalculator.evaluate(
            StreakInput(
                trainingWeekdays = trainingWeekdays,
                completedDates = emptySet(),
                state = state(lastEvaluatedDate = "2026-10-25"),
                today = today,
            ),
        )

        assertEquals(0, output.state.currentStreak)
        assertEquals(0, output.missedTrainingDays)
        assertTrue(output.hasTrainingDays)
    }

    @Test
    fun `missed training day without token resets the streak`() {
        val output = StreakCalculator.evaluate(
            StreakInput(
                trainingWeekdays = trainingWeekdays,
                completedDates = setOf(LocalDate.of(2026, 10, 19)),
                state = state(
                    currentStreak = 5,
                    longestStreak = 6,
                    lastCompletedDate = "2026-10-19",
                    lastEvaluatedDate = "2026-10-19",
                ),
                today = today,
            ),
        )

        assertEquals(2, output.missedTrainingDays)
        assertEquals(2, output.unprotectedMisses)
        assertEquals(0, output.state.currentStreak)
        assertEquals(6, output.state.longestStreak)
    }

    @Test
    fun `freeze token preserves the streak for one missed day`() {
        val output = StreakCalculator.evaluate(
            StreakInput(
                trainingWeekdays = setOf(1, 3, 5),
                completedDates = setOf(LocalDate.of(2026, 10, 19)),
                state = state(
                    currentStreak = 5,
                    lastCompletedDate = "2026-10-19",
                    lastEvaluatedDate = "2026-10-19",
                    freezeTokens = 2,
                ),
                today = today,
            ),
        )

        // Wed 21 missed (token), Fri 23 missed (token), no unprotected miss.
        assertEquals(2, output.frozenMisses)
        assertEquals(0, output.unprotectedMisses)
        assertEquals(0, output.state.freezeTokens)
        assertEquals(5, output.state.currentStreak)
    }

    @Test
    fun `completed missed days are folded into the streak`() {
        val output = StreakCalculator.evaluate(
            StreakInput(
                trainingWeekdays = trainingWeekdays,
                completedDates = setOf(
                    LocalDate.of(2026, 10, 21),
                    LocalDate.of(2026, 10, 23),
                ),
                state = state(
                    currentStreak = 1,
                    longestStreak = 1,
                    lastCompletedDate = "2026-10-19",
                    lastEvaluatedDate = "2026-10-19",
                ),
                today = today,
            ),
        )

        assertEquals(3, output.state.currentStreak)
        assertEquals(3, output.state.longestStreak)
        assertEquals("2026-10-23", output.state.lastCompletedDate)
        assertEquals(0, output.missedTrainingDays)
    }

    @Test
    fun `session completed today increments once`() {
        val input = StreakInput(
            trainingWeekdays = trainingWeekdays,
            completedDates = setOf(
                LocalDate.of(2026, 10, 23),
                LocalDate.of(2026, 10, 26),
            ),
            state = state(
                currentStreak = 2,
                longestStreak = 4,
                lastCompletedDate = "2026-10-23",
                lastEvaluatedDate = "2026-10-23",
            ),
            today = today,
        )

        val first = StreakCalculator.evaluate(input)
        assertEquals(3, first.state.currentStreak)
        assertEquals("2026-10-26", first.state.lastCompletedDate)
        assertEquals(4, first.state.longestStreak)

        val second = StreakCalculator.evaluate(input.copy(state = first.state))
        assertEquals(3, second.state.currentStreak)
    }

    @Test
    fun `evaluation is idempotent for the same day`() {
        val input = StreakInput(
            trainingWeekdays = trainingWeekdays,
            completedDates = setOf(LocalDate.of(2026, 10, 19)),
            state = state(
                currentStreak = 4,
                lastCompletedDate = "2026-10-19",
                lastEvaluatedDate = "2026-10-19",
                freezeTokens = 1,
            ),
            today = today,
        )

        val first = StreakCalculator.evaluate(input)
        val second = StreakCalculator.evaluate(input.copy(state = first.state))

        assertEquals(first.state.currentStreak, second.state.currentStreak)
        assertEquals(first.state.freezeTokens, second.state.freezeTokens)
        assertEquals(0, second.missedTrainingDays)
    }

    @Test
    fun `a rest day never breaks the streak`() {
        val output = StreakCalculator.evaluate(
            StreakInput(
                trainingWeekdays = setOf(1),
                completedDates = emptySet(),
                state = state(
                    currentStreak = 3,
                    lastCompletedDate = "2026-10-19",
                    lastEvaluatedDate = "2026-10-25",
                ),
                today = today,
            ),
        )

        assertEquals(3, output.state.currentStreak)
        assertEquals(0, output.missedTrainingDays)
    }

    @Test
    fun `zero training days pauses the streak`() {
        val output = StreakCalculator.evaluate(
            StreakInput(
                trainingWeekdays = emptySet(),
                completedDates = emptySet(),
                state = state(currentStreak = 7),
                today = today,
            ),
        )

        assertEquals(7, output.state.currentStreak)
        assertFalse(output.hasTrainingDays)
    }

    @Test
    fun `completion on a non training weekday is ignored`() {
        val output = StreakCalculator.evaluate(
            StreakInput(
                trainingWeekdays = trainingWeekdays,
                completedDates = setOf(LocalDate.of(2026, 10, 27)),
                state = state(
                    currentStreak = 2,
                    lastCompletedDate = "2026-10-23",
                    lastEvaluatedDate = "2026-10-26",
                ),
                today = LocalDate.of(2026, 10, 27),
            ),
        )

        assertEquals(2, output.state.currentStreak)
        assertEquals("2026-10-23", output.state.lastCompletedDate)
    }
}
