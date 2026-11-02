package com.sixmonths.app.domain

import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.data.model.PlanEntryRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutCircuitTest {

    private fun row(
        name: String,
        category: String = ExerciseCategory.PUSH,
        sets: Int = 3,
        order: Int = 0,
        id: String = name,
    ) = PlanEntryRow(
        planDayId = "day",
        planExerciseId = "pe-$id",
        exerciseId = "ex-$id",
        name = name,
        category = category,
        unit = ExerciseUnit.REPS,
        label = "Training",
        sets = sets,
        reps = 8,
        restSeconds = 90,
        sortOrder = order,
        notes = null,
        formTip = null,
    )

    @Test
    fun `blocks run skill then planche then normal`() {
        val entries = listOf(
            row("Bench press", order = 0),
            row("Planche lean", order = 1),
            row("Handstand hold", category = ExerciseCategory.SKILL, order = 2),
        )
        val sequence = WorkoutCircuit.sequence(entries)
        assertEquals(TrainingBlock.SKILL, sequence[0].block)
        assertEquals(TrainingBlock.SKILL, sequence[2].block)
        assertEquals(TrainingBlock.PLANCHE, sequence[3].block)
        assertEquals(TrainingBlock.PLANCHE, sequence[5].block)
        assertEquals(TrainingBlock.NORMAL, sequence[6].block)
    }

    @Test
    fun `skills are complete isolated blocks before the normal circuit`() {
        val entries = listOf(
            row("Skill", category = ExerciseCategory.SKILL, sets = 3, order = 0),
            row("Planche", sets = 2, order = 1),
            row("Push", sets = 2, order = 2),
        )
        val sequence = WorkoutCircuit.sequence(entries)

        assertEquals(
            listOf(
                "Skill" to 0,
                "Skill" to 1,
                "Skill" to 2,
                "Planche" to 0,
                "Planche" to 1,
                "Push" to 0,
                "Push" to 1,
            ),
            sequence.map { it.entry.name to it.setIndex },
        )
    }

    @Test
    fun `normal exercises still run one set of each per round`() {
        val entries = listOf(
            row("Push", sets = 3, order = 0),
            row("Pull", sets = 2, order = 1),
        )
        assertEquals(
            listOf(
                "Push" to 0,
                "Pull" to 0,
                "Push" to 1,
                "Pull" to 1,
                "Push" to 2,
            ),
            WorkoutCircuit.sequence(entries).map { it.entry.name to it.setIndex },
        )
    }

    @Test
    fun `plan order is kept inside a block`() {
        val entries = listOf(
            row("Second", order = 1, sets = 1, id = "second"),
            row("First", order = 0, sets = 1, id = "first"),
        )
        assertEquals(
            listOf("First", "Second"),
            WorkoutCircuit.sequence(entries).map { it.entry.name },
        )
    }

    @Test
    fun `planche is detected from the name even when filed as push`() {
        assertEquals(
            TrainingBlock.PLANCHE,
            WorkoutCircuit.blockOf("Tuck Planche Push-up", ExerciseCategory.PUSH),
        )
        assertEquals(
            TrainingBlock.PLANCHE,
            WorkoutCircuit.blockOf("Scapula protraction", ExerciseCategory.OTHER),
        )
        assertEquals(
            TrainingBlock.SKILL,
            WorkoutCircuit.blockOf("Front lever raise", ExerciseCategory.PULL),
        )
        assertEquals(
            TrainingBlock.NORMAL,
            WorkoutCircuit.blockOf("Weighted dips", ExerciseCategory.PUSH),
        )
    }

    @Test
    fun `rounds follow the longest exercise`() {
        val entries = listOf(
            row("A", sets = 5),
            row("B", sets = 2),
        )
        assertEquals(5, WorkoutCircuit.rounds(WorkoutCircuit.sequence(entries)))
        assertEquals(7, WorkoutCircuit.sequence(entries).size)
    }

    @Test
    fun `resuming skips sets that already have a log`() {
        val entries = listOf(
            row("Skill", category = ExerciseCategory.SKILL, sets = 2),
            row("Push", sets = 2),
        )
        val steps = WorkoutCircuit.sequence(entries)
        val logged = setOf(steps[0].key, steps[1].key)

        val next = WorkoutCircuit.firstOpenStep(steps, logged)
        assertNotNull(next)
        assertEquals("Push", next!!.entry.name)
        assertEquals(0, next.setIndex)
        assertEquals(2, WorkoutCircuit.completedSteps(steps, logged))

        val done = WorkoutCircuit.firstOpenStep(steps, steps.map { it.key }.toSet())
        assertNull(done)
        assertEquals(-1, WorkoutCircuit.indexOfFirstOpen(steps, steps.map { it.key }.toSet()))
    }

    @Test
    fun `duplicated exercise rows keep separate log slots`() {
        val entries = listOf(
            row("Push-up", sets = 2, id = "a"),
            row("Push-up", sets = 2, id = "b"),
        )
        val steps = WorkoutCircuit.sequence(entries)

        assertEquals(4, steps.size)
        assertEquals(4, steps.map { it.key }.distinct().size)

        val logged = setOf(steps[0].key, steps[1].key)
        val open = WorkoutCircuit.indexOfFirstOpen(steps, logged)
        assertEquals(2, open)
        assertEquals(steps[2].key, steps[open].key)
    }

    @Test
    fun `empty plans produce no steps`() {
        assertEquals(emptyList<CircuitStep>(), WorkoutCircuit.sequence(emptyList()))
        assertEquals(1, WorkoutCircuit.rounds(emptyList()))
        assertEquals("", WorkoutCircuit.describe(emptyList()))
    }
}
