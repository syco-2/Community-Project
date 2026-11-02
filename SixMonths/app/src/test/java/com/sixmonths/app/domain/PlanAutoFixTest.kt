package com.sixmonths.app.domain

import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.data.entity.PlanDay
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.data.model.PlanEntryRow
import com.sixmonths.app.data.model.PlanSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAutoFixTest {

    private fun entry(
        name: String,
        category: String,
        dayOfWeek: Int,
        sets: Int = 3,
        restSeconds: Int = 90,
        order: Int = 0,
    ) = PlanEntryRow(
        planDayId = "day-$dayOfWeek",
        planExerciseId = "pe-$dayOfWeek-$name",
        exerciseId = "ex-$name",
        name = name,
        category = category,
        unit = ExerciseUnit.REPS,
        label = "Training",
        sets = sets,
        reps = 8,
        restSeconds = restSeconds,
        sortOrder = order,
        notes = null,
        formTip = null,
    )

    private fun day(
        dayOfWeek: Int,
        entries: List<PlanEntryRow>,
        state: String = DayState.TRAINING,
    ) = DaySnapshot(
        planDay = PlanDay(id = "day-$dayOfWeek", dayOfWeek = dayOfWeek, state = state),
        entries = entries,
    )

    private fun plan(vararg days: DaySnapshot) = PlanSnapshot(days.toList())

    @Test
    fun `skills move to the front with three minutes of rest`() {
        val fix = PlanAutoFix.build(
            plan(
                day(
                    1,
                    listOf(
                        entry("Dips", ExerciseCategory.PUSH, dayOfWeek = 1, order = 0),
                        entry(
                            "Wall handstand",
                            ExerciseCategory.SKILL,
                            dayOfWeek = 1,
                            order = 1,
                            restSeconds = 60,
                        ),
                    ),
                ),
            ),
        )

        val rows = fix.entriesByDay.getValue("day-1").sortedBy { it.sortOrder }
        assertEquals("ex-Wall handstand", rows.first().exerciseId)
        assertEquals(180, rows.first().restSeconds)
        assertEquals(1, fix.movedSkills)
    }

    @Test
    fun `a muscle group is capped at twelve sets per day`() {
        val fix = PlanAutoFix.build(
            plan(
                day(
                    1,
                    listOf(
                        entry("Dips", ExerciseCategory.PUSH, dayOfWeek = 1, sets = 8, order = 0),
                        entry("Archer push-up", ExerciseCategory.PUSH, dayOfWeek = 1, sets = 7, order = 1),
                    ),
                ),
            ),
        )

        val rows = fix.entriesByDay.getValue("day-1").sortedBy { it.sortOrder }
        assertEquals(12, rows.sumOf { it.sets })
        assertEquals(8, rows[0].sets)
        assertEquals(4, rows[1].sets)
        assertEquals(1, fix.cappedDays)
    }

    @Test
    fun `seven training days become one rest day`() {
        val fix = PlanAutoFix.build(
            plan(
                *((1..7).map { weekday ->
                    day(
                        weekday,
                        listOf(entry("Push-up", ExerciseCategory.PUSH, dayOfWeek = weekday)),
                    )
                }.toTypedArray()),
            ),
        )

        assertNotNull(fix.restDayOfWeek)
        assertEquals(1, fix.days.count { it.state == DayState.REST })
        assertTrue(fix.summary.any { it.contains("rest day", ignoreCase = true) })
    }

    @Test
    fun `four push days in a row get a rest day`() {
        val fix = PlanAutoFix.build(
            plan(
                *((1..4).map { weekday ->
                    day(
                        weekday,
                        listOf(entry("Push-up", ExerciseCategory.PUSH, dayOfWeek = weekday)),
                    )
                }.toTypedArray()),
            ),
        )

        assertNotNull(fix.restDayOfWeek)
        assertTrue(fix.restDayOfWeek in 1..4)
        assertEquals(
            1,
            fix.days.count { it.state == DayState.REST },
        )
        assertTrue(fix.summary.any { it.contains("rest day", ignoreCase = true) })
    }

    @Test
    fun `a clean week is left alone`() {
        val fix = PlanAutoFix.build(
            plan(
                *((1..3).map { weekday ->
                    day(
                        weekday,
                        listOf(
                            entry(
                                "Wall handstand",
                                ExerciseCategory.SKILL,
                                dayOfWeek = weekday,
                                sets = 2,
                                order = 0,
                                restSeconds = 180,
                            ),
                            entry("Dips", ExerciseCategory.PUSH, dayOfWeek = weekday, sets = 3, order = 1),
                            entry("Pull-up", ExerciseCategory.PULL, dayOfWeek = weekday, sets = 3, order = 2),
                        ),
                    )
                }.toTypedArray()),
            ),
        )

        assertEquals(0, fix.movedSkills)
        assertEquals(0, fix.cappedDays)
        assertEquals(null, fix.restDayOfWeek)
        assertTrue(fix.summary.any { it.contains("No automatic fixes") })
    }
}
