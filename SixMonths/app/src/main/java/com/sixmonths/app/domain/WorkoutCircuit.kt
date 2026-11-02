package com.sixmonths.app.domain

import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.data.model.PlanEntryRow

/** The three blocks the player walks through inside every round. */
enum class TrainingBlock(val label: String) {
    SKILL("Skill"),
    PLANCHE("Planche"),
    NORMAL("Training"),
}

/** One set of one exercise in the round-robin order. [setIndex] is 0-based. */
data class CircuitStep(
    val entry: PlanEntryRow,
    val setIndex: Int,
    val block: TrainingBlock,
) {
    /**
     * Stable key for "this set has already been logged" checks. The plan-row id is used
     * instead of the exercise id: duplicating an exercise creates two rows that must not
     * share a log slot, otherwise resuming would skip the second copy.
     */
    val key: Pair<String, Int> get() = entry.planExerciseId to setIndex
}

/**
 * Workout order: isolated skills first, then a round-robin circuit for normal work.
 *
 * Static skills need a fresh nervous system, so they are not interleaved with the circuit. The
 * whole Skill block is completed first, then the Planche block, then the normal exercises run
 * as a circuit:
 *
 *   Skill:    S1, S2, S3
 *   Planche:  P1, P2
 *   Circuit:  A1, B1, C1, A2, B2, C2, A3
 *
 * Exercises keep their plan order inside a block. Normal exercises with fewer sets drop out of
 * later rounds, so no round is ever empty.
 */
object WorkoutCircuit {

    /**
     * Planche work is a skill, but it gets its own block. A name match wins over the stored
     * category, because "Planche Lean" is routinely filed under Push in the editor.
     */
    fun blockOf(entry: PlanEntryRow): TrainingBlock = blockOf(entry.name, entry.category)

    fun blockOf(name: String, category: String): TrainingBlock {
        val normalized = normalize(name)
        if (PLANCHE_WORDS.any { it in normalized }) return TrainingBlock.PLANCHE
        if (category == ExerciseCategory.SKILL) return TrainingBlock.SKILL
        if (SKILL_WORDS.any { it in normalized }) return TrainingBlock.SKILL
        return TrainingBlock.NORMAL
    }

    /** The complete session in the order the player shows it. */
    fun sequence(entries: List<PlanEntryRow>): List<CircuitStep> {
        if (entries.isEmpty()) return emptyList()
        val grouped = entries.groupBy(::blockOf)
        val steps = ArrayList<CircuitStep>(entries.sumOf { it.sets })

        // Skills are isolated blocks: every set of one exercise before the next, with the
        // long rest the plan stores. They never share a round with normal work.
        listOf(TrainingBlock.SKILL, TrainingBlock.PLANCHE).forEach { block ->
            grouped[block]
                .orEmpty()
                .sortedBy { it.sortOrder }
                .forEach { entry ->
                    for (set in 0 until entry.sets) {
                        steps += CircuitStep(entry = entry, setIndex = set, block = block)
                    }
                }
        }

        // Normal exercises stay a circuit: one set of each per round.
        val normal = grouped[TrainingBlock.NORMAL].orEmpty().sortedBy { it.sortOrder }
        val rounds = normal.maxOfOrNull { it.sets } ?: 0
        for (round in 0 until rounds) {
            normal.forEach { entry ->
                if (round < entry.sets) {
                    steps += CircuitStep(
                        entry = entry,
                        setIndex = round,
                        block = TrainingBlock.NORMAL,
                    )
                }
            }
        }
        return steps
    }

    /** First step of [steps] that has no log row yet, or null when the session is complete. */
    fun firstOpenStep(steps: List<CircuitStep>, logged: Collection<Pair<String, Int>>): CircuitStep? =
        steps.firstOrNull { it.key !in logged }

    fun indexOfFirstOpen(steps: List<CircuitStep>, logged: Collection<Pair<String, Int>>): Int =
        steps.indexOfFirst { it.key !in logged }

    fun completedSteps(steps: List<CircuitStep>, logged: Collection<Pair<String, Int>>): Int =
        steps.count { it.key in logged }

    fun rounds(steps: List<CircuitStep>): Int =
        if (steps.isEmpty()) 1 else steps.maxOf { it.setIndex } + 1

    /** A short "A → B → C" line for the plan preview and the Done screen. */
    fun describe(entries: List<PlanEntryRow>): String =
        sequence(entries).distinctBy { it.entry.planExerciseId }.joinToString(" → ") { it.entry.name }

    private fun normalize(raw: String): String = raw.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex(" +"), " ")

    private val PLANCHE_WORDS = listOf(
        "planche",
        "protraction",
        "protract",
        "maltese",
    )

    private val SKILL_WORDS = listOf(
        "handstand",
        "lever",
        "human flag",
        "muscle up",
        "muscleup",
        "elbow lever",
        "l sit",
        "v sit",
        "press to handstand",
        "skin the cat",
        "one arm",
    )
}
