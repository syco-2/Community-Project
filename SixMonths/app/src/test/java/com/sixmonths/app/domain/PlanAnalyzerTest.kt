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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanAnalyzerTest {

    private fun entry(
        name: String,
        category: String,
        sets: Int = 3,
        reps: Int = 8,
        unit: String = ExerciseUnit.REPS,
        dayOfWeek: Int,
        order: Int = 0,
    ) = PlanEntryRow(
        planDayId = "day-$dayOfWeek",
        planExerciseId = "pe-$dayOfWeek-$name",
        exerciseId = "ex-$name",
        name = name,
        category = category,
        unit = unit,
        label = "Training",
        sets = sets,
        reps = reps,
        restSeconds = 90,
        sortOrder = order,
        notes = null,
        formTip = null,
    )

    private fun day(dayOfWeek: Int, entries: List<PlanEntryRow>, state: String = DayState.TRAINING) =
        DaySnapshot(
            planDay = PlanDay(id = "day-$dayOfWeek", dayOfWeek = dayOfWeek, state = state),
            entries = entries,
        )

    private fun plan(vararg days: DaySnapshot) = PlanSnapshot(days.toList())

    private fun balancedWeek(days: Int = 3): PlanSnapshot = plan(
        *((1..days).map { weekday ->
            day(
                weekday,
                listOf(
                    entry("Bench press", ExerciseCategory.PUSH, sets = 4, dayOfWeek = weekday, order = 0),
                    entry("Pull-up", ExerciseCategory.PULL, sets = 4, dayOfWeek = weekday, order = 1),
                    entry("Squat", ExerciseCategory.LEGS, sets = 4, dayOfWeek = weekday, order = 2),
                    entry("Hanging leg raise", ExerciseCategory.CORE, sets = 3, dayOfWeek = weekday, order = 3),
                ),
            )
        }.toTypedArray()),
    )

    @Test
    fun `empty plan has no stimulus and no skill work`() {
        val result = PlanAnalyzer.analyze(plan())
        assertEquals(0, result.trainingDays)
        assertEquals(0, result.weeklySets)
        assertEquals(AnalysisLevel.LOW, result.hypertrophy.level)
        assertEquals(AnalysisLevel.LOW, result.skills.level)
        assertEquals(0, result.overall.grade)
        assertTrue(result.skillRows.isEmpty())
        assertTrue(result.hypertrophy.bullets.any { it.contains("No training day") })
        assertTrue(result.warnings.any { it.title == "No training day" })
    }

    @Test
    fun `balanced three-day week scores a strong hypertrophy grade`() {
        val result = PlanAnalyzer.analyze(balancedWeek())

        assertEquals(3, result.trainingDays)
        assertEquals(45, result.weeklySets)
        val push = result.muscleGroups.first { it.group == "Push" }
        assertEquals(12, push.weeklySets)
        assertEquals(3, push.daysPerWeek)
        assertEquals(AnalysisLevel.STRONG, result.hypertrophy.level)
        assertTrue(result.hypertrophy.score >= 85)
        assertEquals(10, result.hypertrophy.grade)
        assertEquals(100, result.balance.score)
    }

    @Test
    fun `one short day is flagged as too little volume`() {
        val result = PlanAnalyzer.analyze(
            plan(day(1, listOf(entry("Bench press", ExerciseCategory.PUSH, sets = 2, dayOfWeek = 1)))),
        )
        assertEquals(1, result.trainingDays)
        assertEquals(AnalysisLevel.LOW, result.hypertrophy.level)
        assertTrue(result.hypertrophy.bullets.any { it.contains("Push") })
        assertTrue(result.hypertrophy.bullets.any { it.contains("single exercise") })
    }

    @Test
    fun `low rep strength sets count less than full range sets`() {
        val heavy = PlanAnalyzer.analyze(
            plan(day(1, listOf(entry("Bench press", ExerciseCategory.PUSH, sets = 4, reps = 3, dayOfWeek = 1)))),
        )
        val fullRange = PlanAnalyzer.analyze(
            plan(day(1, listOf(entry("Bench press", ExerciseCategory.PUSH, sets = 4, reps = 8, dayOfWeek = 1)))),
        )
        val heavyPush = heavy.muscleGroups.first { it.group == "Push" }
        val fullPush = fullRange.muscleGroups.first { it.group == "Push" }
        assertTrue(heavyPush.effectiveSets < fullPush.effectiveSets)
        assertTrue(heavy.hypertrophy.score < fullRange.hypertrophy.score)
    }

    @Test
    fun `handstand plus planche on their target frequency is a ten`() {
        val days = listOf(
            day(1, listOf(entry("Wall handstand", ExerciseCategory.SKILL, sets = 3, reps = 40, unit = ExerciseUnit.SECONDS, dayOfWeek = 1))),
            day(2, listOf(entry("Tuck planche lean", ExerciseCategory.SKILL, sets = 3, dayOfWeek = 2))),
            day(3, listOf(entry("Wall handstand", ExerciseCategory.SKILL, sets = 3, reps = 40, unit = ExerciseUnit.SECONDS, dayOfWeek = 3))),
            day(4, listOf(entry("Tuck planche lean", ExerciseCategory.SKILL, sets = 3, dayOfWeek = 4))),
            day(5, listOf(entry("Wall handstand", ExerciseCategory.SKILL, sets = 3, reps = 40, unit = ExerciseUnit.SECONDS, dayOfWeek = 5))),
        )
        val result = PlanAnalyzer.analyze(plan(*days.toTypedArray()))

        val handstand = result.skillRows.first { it.key == "handstand" }
        val planche = result.skillRows.first { it.key == "planche" }
        assertTrue(handstand.onTrack)
        assertTrue(planche.onTrack)
        assertEquals(3, handstand.daysPerWeek)
        assertEquals(2, planche.daysPerWeek)
        assertTrue(handstand.timeline.contains("Six months"))
        assertEquals(AnalysisLevel.STRONG, result.skills.level)
        assertEquals(10, result.skills.grade)
        assertFalse(result.warnings.any { it.title == "Handstand is missing" })
    }

    @Test
    fun `planche without a handstand base is capped`() {
        val result = PlanAnalyzer.analyze(
            plan(
                day(1, listOf(entry("Planche lean", ExerciseCategory.PUSH, sets = 3, dayOfWeek = 1))),
                day(4, listOf(entry("Planche lean", ExerciseCategory.PUSH, sets = 3, dayOfWeek = 4))),
            ),
        )
        val planche = result.skillRows.firstOrNull { it.key == "planche" }
        assertNotNull(planche)
        assertTrue(planche!!.onTrack)
        assertTrue(result.skills.score <= 60)
        assertTrue(result.warnings.any { it.title == "Handstand is missing" })
    }

    @Test
    fun `push heavy plan gets an imbalance warning and a low balance grade`() {
        val result = PlanAnalyzer.analyze(
            plan(
                day(1, listOf(
                    entry("Dips", ExerciseCategory.PUSH, sets = 5, dayOfWeek = 1),
                    entry("Pseudo planche push-up", ExerciseCategory.PUSH, sets = 5, dayOfWeek = 1, order = 1),
                    entry("Chin-up", ExerciseCategory.PULL, sets = 2, dayOfWeek = 1, order = 2),
                )),
            ),
        )
        assertTrue(result.warnings.any { it.title == "Push / pull imbalance" })
        assertTrue(result.balance.score < 60)
        assertTrue(result.balance.headline.contains("push", ignoreCase = true))
    }

    @Test
    fun `straight arm work without wrist prep lowers joint health`() {
        val result = PlanAnalyzer.analyze(
            plan(
                day(1, listOf(entry("Wall handstand", ExerciseCategory.SKILL, sets = 3, dayOfWeek = 1))),
                day(2, listOf(entry("Planche lean", ExerciseCategory.PUSH, sets = 3, dayOfWeek = 2))),
                day(3, listOf(entry("Wall handstand", ExerciseCategory.SKILL, sets = 3, dayOfWeek = 3))),
                day(4, listOf(entry("Planche lean", ExerciseCategory.PUSH, sets = 3, dayOfWeek = 4))),
            ),
        )
        assertTrue(result.warnings.any { it.title == "Wrists are unprepared" })
        assertTrue(result.joints.score < 85)
        assertEquals(0f, result.joints.metrics.first { it.label == "Wrist prep" }.fraction ?: 1f, 0f)
    }

    @Test
    fun `excessive weekly push volume is called out`() {
        val result = PlanAnalyzer.analyze(
            plan(
                *((1..6).map { weekday ->
                    day(weekday, listOf(entry("Dips", ExerciseCategory.PUSH, sets = 5, dayOfWeek = weekday)))
                }.toTypedArray()),
            ),
        )
        assertTrue(result.warnings.any { it.title == "Push volume is high" })
        assertTrue(result.remove.any { it.contains("Push") })
    }

    @Test
    fun `skill work filed outside the skill category is reported honestly`() {
        val result = PlanAnalyzer.analyze(
            plan(
                day(
                    1,
                    listOf(
                        entry("Planche lean", ExerciseCategory.PUSH, sets = 2, dayOfWeek = 1),
                        entry("Bench press", ExerciseCategory.PUSH, sets = 4, dayOfWeek = 1, order = 1),
                    ),
                ),
            ),
        )
        assertEquals("Planche", result.skillRows.single().skill)
        assertFalse(result.skillRows.single().onTrack)
        assertTrue(result.skills.bullets.any { it.contains("filed outside the Skill category") })
    }

    @Test
    fun `rest days never count as training days`() {
        val result = PlanAnalyzer.analyze(
            plan(
                day(1, listOf(entry("Bench press", ExerciseCategory.PUSH, sets = 4, dayOfWeek = 1))),
                day(2, listOf(entry("Bench press", ExerciseCategory.PUSH, sets = 4, dayOfWeek = 2)), DayState.REST),
            ),
        )
        assertEquals(1, result.trainingDays)
        assertEquals(4, result.weeklySets)
    }

    @Test
    fun `mobility work is ignored instead of inflating push volume`() {
        val result = PlanAnalyzer.analyze(
            plan(
                day(1, listOf(entry("Shoulder mobility", ExerciseCategory.MOBILITY, sets = 5, dayOfWeek = 1))),
            ),
        )
        assertTrue(result.muscleGroups.all { it.weeklySets == 0 })
        assertEquals(0, result.hypertrophy.score)
    }

    @Test
    fun `analysis is deterministic and every grade is in range`() {
        val snapshot = balancedWeek() // deterministic input
        val first = PlanAnalyzer.analyze(snapshot)
        val second = PlanAnalyzer.analyze(snapshot)
        assertEquals(first.hypertrophy.score, second.hypertrophy.score)
        assertEquals(first.skills.score, second.skills.score)
        assertEquals(first.overall.score, second.overall.score)
        assertEquals(first.skillRows, second.skillRows)
        first.cards.forEach { card ->
            assertTrue(card.score in 0..100)
            assertTrue(card.grade in 0..10)
        }
    }
}
