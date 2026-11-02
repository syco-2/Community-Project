package com.sixmonths.app.data.model

import com.sixmonths.app.core.model.DayState

/** One Day Editor row: the plan entry joined with its exercise. */
data class PlanEntryRow(
    val planDayId: String,
    val planExerciseId: String,
    val exerciseId: String,
    val name: String,
    val category: String,
    val unit: String,
    val label: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val sortOrder: Int,
    val notes: String?,
    val formTip: String?,
)

/** A plan day with its ordered entries. */
data class DaySnapshot(
    val planDay: com.sixmonths.app.data.entity.PlanDay,
    val entries: List<PlanEntryRow>,
) {
    val totalSets: Int get() = entries.sumOf { it.sets }
    val totalTrainings: Int get() = entries.size
    val isTrainingDay: Boolean get() = planDay.state == DayState.TRAINING
}

/** The whole week, ordered Monday..Sunday. */
data class PlanSnapshot(
    val days: List<DaySnapshot>,
) {
    val trainingDays: List<DaySnapshot> get() = days.filter { it.isTrainingDay }
    val hasTrainingDays: Boolean get() = trainingDays.isNotEmpty()

    fun dayFor(dayOfWeek: Int): DaySnapshot? = days.firstOrNull { it.planDay.dayOfWeek == dayOfWeek }

    fun entry(planExerciseId: String): PlanEntryRow? =
        days.asSequence().flatMap { it.entries }.firstOrNull { it.planExerciseId == planExerciseId }
}

/**
 * What the Day Editor writes for one row. [exerciseId] is null for a brand new exercise;
 * the repository then reuses a same-named library exercise or creates one.
 */
data class PlanEntryDraft(
    val planExerciseId: String,
    val exerciseId: String?,
    val name: String,
    val category: String,
    val unit: String,
    val label: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val notes: String?,
    val formTip: String?,
)
