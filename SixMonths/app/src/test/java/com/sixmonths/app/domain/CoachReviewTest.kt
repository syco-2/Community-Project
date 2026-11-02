package com.sixmonths.app.domain

import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.data.entity.PlanDay
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.data.model.PlanEntryRow
import com.sixmonths.app.data.model.PlanSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachReviewTest {

    private fun entry(
        id: String,
        name: String,
        category: String,
        sets: Int,
    ) = PlanEntryRow(
        planDayId = "day",
        planExerciseId = "pe-$id",
        exerciseId = "ex-$id",
        name = name,
        category = category,
        unit = ExerciseUnit.REPS,
        label = "Training 1",
        sets = sets,
        reps = 8,
        restSeconds = 90,
        sortOrder = 0,
        notes = null,
        formTip = null,
    )

    private fun day(dayOfWeek: Int, state: String, vararg entries: PlanEntryRow): DaySnapshot =
        DaySnapshot(
            planDay = PlanDay(id = "day-$dayOfWeek", dayOfWeek = dayOfWeek, state = state),
            entries = entries.toList(),
        )

    @Test
    fun `empty plan has no training days`() {
        val result = CoachReview.analyze(PlanSnapshot(emptyList()))

        assertFalse(result.hasTrainingDays)
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `push pull imbalance above forty percent is flagged`() {
        val plan = PlanSnapshot(
            listOf(
                day(
                    1,
                    DayState.TRAINING,
                    entry("p", "Push-up", ExerciseCategory.PUSH, 10),
                    entry("q", "Pull-up", ExerciseCategory.PULL, 2),
                ),
            ),
        )

        val result = CoachReview.analyze(plan)
        assertTrue(result.suggestions.any { it.id == "balance_imbalance" })
    }

    @Test
    fun `missing pull volume is flagged`() {
        val plan = PlanSnapshot(
            listOf(day(1, DayState.TRAINING, entry("p", "Push-up", ExerciseCategory.PUSH, 5))),
        )

        val result = CoachReview.analyze(plan)
        assertTrue(result.suggestions.any { it.id == "balance_zero_pull" })
    }

    @Test
    fun `missing skill core and mobility coverage is flagged`() {
        val plan = PlanSnapshot(
            listOf(
                day(
                    1,
                    DayState.TRAINING,
                    entry("p", "Push-up", ExerciseCategory.PUSH, 4),
                    entry("q", "Pull-up", ExerciseCategory.PULL, 4),
                ),
            ),
        )

        val result = CoachReview.analyze(plan)
        val ids = result.suggestions.map { it.id }
        assertTrue(ids.contains("coverage_skill"))
        assertTrue(ids.contains("coverage_core"))
        assertTrue(ids.contains("coverage_mobility"))
    }

    @Test
    fun `unclassified exercises are listed instead of guessed`() {
        val plan = PlanSnapshot(
            listOf(
                day(
                    1,
                    DayState.TRAINING,
                    entry("o", "Weird move", ExerciseCategory.OTHER, 3),
                    entry("p", "Push-up", ExerciseCategory.PUSH, 3),
                    entry("q", "Pull-up", ExerciseCategory.PULL, 3),
                ),
            ),
        )

        val result = CoachReview.analyze(plan)
        assertEquals(listOf("Weird move"), result.unclassifiedExercises)
        assertTrue(result.suggestions.any { it.id == "data_quality_other" })
    }

    @Test
    fun `same dominant category on consecutive training days is flagged`() {
        val plan = PlanSnapshot(
            listOf(
                day(1, DayState.TRAINING, entry("p1", "Push-up", ExerciseCategory.PUSH, 6)),
                day(2, DayState.TRAINING, entry("p2", "Dips", ExerciseCategory.PUSH, 6)),
                day(4, DayState.REST),
            ),
        )

        val result = CoachReview.analyze(plan)
        assertTrue(result.suggestions.any { it.id.startsWith("pattern_consecutive") })
        assertTrue(result.suggestions.any { it.id.startsWith("recovery") })
    }

    @Test
    fun `rest days are not analyzed as training days`() {
        val plan = PlanSnapshot(
            listOf(
                day(1, DayState.TRAINING, entry("p", "Push-up", ExerciseCategory.PUSH, 3)),
                day(2, DayState.REST, entry("r", "Mystery", ExerciseCategory.OTHER, 9)),
                day(3, DayState.OPTIONAL, entry("o", "Stretch", ExerciseCategory.MOBILITY, 2)),
            ),
        )

        val result = CoachReview.analyze(plan)
        assertEquals(1, result.trainingDayCount)
        assertTrue(result.suggestions.any { it.id == "balance_zero_pull" })
    }
}
