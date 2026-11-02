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

class CoachAuditTest {

    private fun entry(
        name: String,
        category: String,
        dayOfWeek: Int,
        sets: Int = 3,
        reps: Int = 8,
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
        reps = reps,
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
    fun `skill inside a circuit triggers the exact freshness warning`() {
        val report = CoachAudit.audit(
            plan(
                day(
                    1,
                    listOf(
                        entry("Wall handstand", ExerciseCategory.SKILL, dayOfWeek = 1, order = 1),
                        entry("Dips", ExerciseCategory.PUSH, dayOfWeek = 1, order = 0),
                    ),
                ),
            ),
        )

        assertTrue(report.risks.any { it.detail == CoachAudit.SKILL_CIRCUIT_WARNING })
        assertTrue(report.risks.first { it.id == "skill-freshness" }.critical)
        assertTrue(report.verdict.skills.contains("at risk"))
    }

    @Test
    fun `an isolated skill block with long rest is a strength`() {
        val report = CoachAudit.audit(
            plan(
                day(
                    1,
                    listOf(
                        entry(
                            "Wall handstand",
                            ExerciseCategory.SKILL,
                            dayOfWeek = 1,
                            order = 0,
                            restSeconds = 180,
                        ),
                        entry("Dips", ExerciseCategory.PUSH, dayOfWeek = 1, order = 1),
                    ),
                ),
            ),
        )

        assertFalse(report.risks.any { it.id == "skill-freshness" || it.id == "skill-rest" })
        assertTrue(report.pros.any { it.id == "skill-fresh" })
    }

    @Test
    fun `four push days in a row trigger the tendinitis warning`() {
        val report = CoachAudit.audit(
            plan(
                *((1..4).map { weekday ->
                    day(
                        weekday,
                        listOf(entry("Dips", ExerciseCategory.PUSH, dayOfWeek = weekday)),
                    )
                }.toTypedArray()),
                day(5, emptyList(), state = DayState.REST),
                day(6, emptyList(), state = DayState.REST),
                day(7, emptyList(), state = DayState.REST),
            ),
        )

        assertEquals(4, report.maxConsecutiveLoad)
        val warning = report.risks.first { it.id == "joint-frequency" }
        assertTrue(warning.detail.contains("tendinitis", ignoreCase = true))
        assertTrue(warning.detail.contains("rest day", ignoreCase = true))
    }

    @Test
    fun `more than twelve push sets in one day is a massive push day`() {
        val report = CoachAudit.audit(
            plan(
                day(
                    1,
                    listOf(
                        entry("Dips", ExerciseCategory.PUSH, dayOfWeek = 1, sets = 8, order = 0),
                        entry("Archer push-up", ExerciseCategory.PUSH, dayOfWeek = 1, sets = 6, order = 1),
                    ),
                ),
            ),
        )

        assertEquals(14, report.maxDailyPush)
        assertTrue(report.risks.any { it.detail == CoachAudit.MASSIVE_PUSH_WARNING })
    }

    @Test
    fun `core and legs are acknowledged as strengths`() {
        val report = CoachAudit.audit(
            plan(
                day(
                    1,
                    listOf(
                        entry("Hollow body hold", ExerciseCategory.CORE, dayOfWeek = 1, order = 0),
                        entry("Split squat", ExerciseCategory.LEGS, dayOfWeek = 1, order = 1),
                    ),
                ),
            ),
        )

        assertTrue(report.pros.any { it.id == "core-present" })
        assertTrue(report.pros.any { it.id == "legs-present" })
    }

    @Test
    fun `seven training days get a no-rest warning`() {
        val report = CoachAudit.audit(
            plan(
                *((1..7).map { weekday ->
                    day(
                        weekday,
                        listOf(
                            entry("Push-up", ExerciseCategory.PUSH, dayOfWeek = weekday, sets = 2),
                            entry("Pull-up", ExerciseCategory.PULL, dayOfWeek = weekday, sets = 2),
                            entry("Hollow hold", ExerciseCategory.CORE, dayOfWeek = weekday, sets = 2),
                            entry("Squat", ExerciseCategory.LEGS, dayOfWeek = weekday, sets = 2),
                        ),
                    )
                }.toTypedArray()),
            ),
        )

        assertTrue(report.risks.any { it.id == "no-rest" })
        assertTrue(report.verdict.score < 100)
    }

    @Test
    fun `a fresh, balanced week is a realistic six-month plan`() {
        val report = CoachAudit.audit(
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
                            entry("Pull-up", ExerciseCategory.PULL, dayOfWeek = weekday, sets = 5, order = 2),
                            entry("Hollow hold", ExerciseCategory.CORE, dayOfWeek = weekday, sets = 2, order = 3),
                            entry("Split squat", ExerciseCategory.LEGS, dayOfWeek = weekday, sets = 2, order = 4),
                        ),
                    )
                }.toTypedArray()),
            ),
        )

        assertTrue(report.risks.isEmpty())
        assertEquals(100, report.verdict.score)
        assertTrue(report.verdict.growth.startsWith("Muscle growth: yes"))
        assertTrue(report.verdict.skills.startsWith("Skills: yes"))
        assertTrue(report.rules.all { it.grade == 10 && it.passed })
    }

    @Test
    fun `the report grades freshness, joint frequency and daily volume`() {
        val report = CoachAudit.audit(
            plan(
                day(
                    1,
                    listOf(
                        entry("Dips", ExerciseCategory.PUSH, dayOfWeek = 1, sets = 4, order = 0),
                        entry(
                            "Wall handstand",
                            ExerciseCategory.SKILL,
                            dayOfWeek = 1,
                            sets = 6,
                            order = 1,
                            restSeconds = 180,
                        ),
                        entry("Pike push-up", ExerciseCategory.PUSH, dayOfWeek = 1, sets = 4, order = 2),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("skill-freshness", "joint-frequency", "daily-volume"),
            report.rules.map { it.id },
        )

        // The skill is buried behind a push set, so freshness fails hard.
        val freshness = report.rules.first { it.id == "skill-freshness" }
        assertFalse(freshness.passed)
        assertEquals(2, freshness.grade)

        // 14 push sets on a single day break the 12-set ceiling.
        val volume = report.rules.first { it.id == "daily-volume" }
        assertFalse(volume.passed)
        assertEquals(6, volume.grade)
    }
}
