package com.sixmonths.app.domain

import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.core.model.SessionStatus
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.entity.PlanDay
import com.sixmonths.app.data.entity.Session
import com.sixmonths.app.data.entity.SetLog
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.data.model.PlanEntryRow
import com.sixmonths.app.data.model.PlanSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class WeeklyProgressionTest {

    private val weekStart: LocalDate = LocalDate.of(2026, 10, 19) // Monday

    private fun entry(sets: Int = 3, reps: Int = 6, unit: String = ExerciseUnit.REPS) =
        PlanEntryRow(
            planDayId = "day-1",
            planExerciseId = "pe-1",
            exerciseId = "ex-1",
            name = "Handstand Push-ups",
            category = ExerciseCategory.PUSH,
            unit = unit,
            label = "Training 1",
            sets = sets,
            reps = reps,
            restSeconds = 90,
            sortOrder = 0,
            notes = null,
            formTip = null,
        )

    private fun plan(entry: PlanEntryRow = entry()) = PlanSnapshot(
        listOf(
            DaySnapshot(
                planDay = PlanDay(id = "day-1", dayOfWeek = 1, state = DayState.TRAINING),
                entries = listOf(entry),
            ),
        ),
    )

    private fun session(date: LocalDate, id: String = "session-1") = Session(
        id = id,
        planDayId = "day-1",
        startedAt = DateUtils.toEpochMillis(date, LocalTime.of(18, 0)),
        finishedAt = DateUtils.toEpochMillis(date, LocalTime.of(18, 40)),
        status = SessionStatus.COMPLETED,
        durationSeconds = 2_400,
    )

    private fun logs(count: Int, reps: Int, sessionId: String = "session-1") =
        List(count) { index ->
            SetLog(
                id = "log-$index",
                sessionId = sessionId,
                exerciseId = "ex-1",
                setIndex = index,
                repsCompleted = reps,
                completedAt = DateUtils.toEpochMillis(weekStart, LocalTime.of(18, index)),
            )
        }

    @Test
    fun `clean week suggests one more rep`() {
        val result = WeeklyProgression.analyze(
            plan = plan(),
            sessions = listOf(session(weekStart)),
            logsBySession = mapOf("session-1" to logs(count = 3, reps = 6)),
            weekStart = weekStart,
        )

        assertEquals(1, result.size)
        assertEquals(WeeklyAction.INCREASE, result.first().action)
        assertEquals(7, result.first().suggestedReps)
        assertEquals(3, result.first().suggestedSets)
    }

    @Test
    fun `struggling last set keeps the current values`() {
        val result = WeeklyProgression.analyze(
            plan = plan(),
            sessions = listOf(session(weekStart)),
            logsBySession = mapOf("session-1" to logs(count = 3, reps = 4)),
            weekStart = weekStart,
        )

        assertEquals(WeeklyAction.KEEP, result.first().action)
        assertEquals(6, result.first().suggestedReps)
    }

    @Test
    fun `missing sets keep the current values`() {
        val result = WeeklyProgression.analyze(
            plan = plan(),
            sessions = listOf(session(weekStart)),
            logsBySession = mapOf("session-1" to logs(count = 1, reps = 6)),
            weekStart = weekStart,
        )

        assertEquals(WeeklyAction.KEEP, result.first().action)
    }

    @Test
    fun `session without any logged set suggests one step back`() {
        val result = WeeklyProgression.analyze(
            plan = plan(),
            sessions = listOf(session(weekStart)),
            logsBySession = mapOf("session-1" to emptyList()),
            weekStart = weekStart,
        )

        assertEquals(WeeklyAction.REGRESS, result.first().action)
        assertEquals(5, result.first().suggestedReps)
    }

    @Test
    fun `seconds increase in ten second steps`() {
        val result = WeeklyProgression.analyze(
            plan = plan(entry(sets = 3, reps = 45, unit = ExerciseUnit.SECONDS)),
            sessions = listOf(session(weekStart)),
            logsBySession = mapOf("session-1" to logs(count = 3, reps = 45)),
            weekStart = weekStart,
        )

        assertEquals(WeeklyAction.INCREASE, result.first().action)
        assertEquals(55, result.first().suggestedReps)
    }

    @Test
    fun `sessions outside the completed week are ignored`() {
        val result = WeeklyProgression.analyze(
            plan = plan(),
            sessions = listOf(session(weekStart.minusDays(8), id = "old")),
            logsBySession = mapOf("old" to logs(count = 3, reps = 6, sessionId = "old")),
            weekStart = weekStart,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `completed week start is the previous monday`() {
        val today = LocalDate.of(2026, 10, 26) // Monday
        assertEquals(LocalDate.of(2026, 10, 19), WeeklyProgression.completedWeekStart(today))
        assertEquals(
            LocalDate.of(2026, 10, 19),
            WeeklyProgression.completedWeekStart(today.plusDays(3)),
        )
    }
}
