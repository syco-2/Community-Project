package com.sixmonths.app.data.repository

import androidx.room.withTransaction
import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.core.model.Weekday
import com.sixmonths.app.core.util.newId
import com.sixmonths.app.data.AppDatabase
import com.sixmonths.app.data.dao.ExerciseDao
import com.sixmonths.app.data.dao.PlanDayDao
import com.sixmonths.app.data.dao.PlanExerciseDao
import com.sixmonths.app.data.entity.Exercise
import com.sixmonths.app.data.entity.PlanDay
import com.sixmonths.app.data.entity.PlanExercise
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.data.model.PlanEntryDraft
import com.sixmonths.app.data.model.PlanSnapshot
import com.sixmonths.app.domain.AutoFixPlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The plan: seven [PlanDay] rows plus their [PlanExercise] entries and the exercise
 * library. Every write path that touches more than one table runs in one transaction.
 */
class PlanRepository(
    private val database: AppDatabase,
    private val exerciseDao: ExerciseDao,
    private val planDayDao: PlanDayDao,
    private val planExerciseDao: PlanExerciseDao,
) {

    fun observePlan(): Flow<PlanSnapshot> = combine(
        planDayDao.observeAll(),
        planExerciseDao.observeAllEntries(),
    ) { days, rows ->
        PlanSnapshot(
            days.sortedBy { it.dayOfWeek }.map { day ->
                DaySnapshot(
                    planDay = day,
                    entries = rows.filter { it.planDayId == day.id }.sortedBy { it.sortOrder },
                )
            },
        )
    }

    suspend fun loadPlan(): PlanSnapshot {
        val days = planDayDao.getAll().sortedBy { it.dayOfWeek }
        return PlanSnapshot(
            days.map { day -> DaySnapshot(day, planExerciseDao.getEntries(day.id)) },
        )
    }

    suspend fun trainingDays(): List<PlanDay> =
        planDayDao.getByState(DayState.TRAINING).sortedBy { it.dayOfWeek }

    private val ensureWeekMutex = Mutex()

    /** Creates the seven weekday rows on first run; never overwrites existing ones. */
    suspend fun ensureWeek() = ensureWeekMutex.withLock {
        val existing = planDayDao.getAll()
        if (existing.size >= Weekday.ALL.size) return@withLock
        val known = existing.map { it.dayOfWeek }.toSet()
        val missing = Weekday.ALL
            .filter { it !in known }
            .map { dayOfWeek -> PlanDay(id = newId(), dayOfWeek = dayOfWeek, state = DayState.REST) }
        if (missing.isNotEmpty()) {
            planDayDao.upsertAll(missing)
        }
    }

    suspend fun setDayState(dayOfWeek: Int, state: String) {
        if (!DayState.isValid(state) || !Weekday.isValid(dayOfWeek)) return
        val day = planDayDao.getByDayOfWeek(dayOfWeek) ?: return
        planDayDao.upsert(day.copy(state = state))
    }

    suspend fun setDayName(dayOfWeek: Int, name: String?) {
        val day = planDayDao.getByDayOfWeek(dayOfWeek) ?: return
        planDayDao.upsert(day.copy(name = name?.trim()?.takeIf { it.isNotBlank() }))
    }

    /** Replaces all entries of one weekday in one transaction. */
    suspend fun saveDayEntries(dayOfWeek: Int, drafts: List<PlanEntryDraft>) {
        val day = planDayDao.getByDayOfWeek(dayOfWeek) ?: return
        val cleaned = drafts.filter { it.name.isNotBlank() }
        database.withTransaction {
            planExerciseDao.deleteForDay(day.id)
            cleaned.forEachIndexed { index, draft ->
                val exercise = resolveExercise(draft)
                planExerciseDao.upsert(
                    PlanExercise(
                        id = draft.planExerciseId,
                        planDayId = day.id,
                        exerciseId = exercise.id,
                        label = draft.label.ifBlank { "Training ${index + 1}" },
                        sets = draft.sets.coerceIn(1, 20),
                        reps = draft.reps.coerceIn(1, 999),
                        restSeconds = draft.restSeconds.coerceIn(0, 900),
                        sortOrder = index,
                    ),
                )
            }
        }
    }

    /**
     * Writes a coach fix set atomically: day states first, then the corrected entries of every
     * affected day. The user either gets the whole improved week or the untouched original.
     */
    suspend fun applyPlanFix(fix: AutoFixPlan) = database.withTransaction {
        fix.days.forEach { planDayDao.upsert(it) }
        fix.entriesByDay.forEach { (planDayId, entries) ->
            planExerciseDao.deleteForDay(planDayId)
            if (entries.isNotEmpty()) planExerciseDao.upsertAll(entries)
        }
    }

    /** Same-named exercises are reused so the autocomplete library stays clean. */
    private suspend fun resolveExercise(draft: PlanEntryDraft): Exercise {
        val category = draft.category.takeIf(ExerciseCategory::isValid) ?: ExerciseCategory.DEFAULT
        val unit = draft.unit.takeIf(ExerciseUnit::isValid) ?: ExerciseUnit.REPS
        val name = draft.name.trim()
        val existing = draft.exerciseId?.let { exerciseDao.getById(it) }
            ?: exerciseDao.getByName(name)
        return if (existing != null) {
            val updated = existing.copy(
                name = name,
                category = category,
                unit = unit,
                notes = draft.notes?.trim()?.takeIf { it.isNotBlank() },
                formTip = draft.formTip?.trim()?.takeIf { it.isNotBlank() },
            )
            exerciseDao.upsert(updated)
            updated
        } else {
            val created = Exercise(
                id = newId(),
                name = name,
                category = category,
                unit = unit,
                notes = draft.notes?.trim()?.takeIf { it.isNotBlank() },
                formTip = draft.formTip?.trim()?.takeIf { it.isNotBlank() },
                isOptional = false,
                createdAt = System.currentTimeMillis(),
            )
            exerciseDao.upsert(created)
            created
        }
    }

    /** Names the user already used, for the autocomplete field. */
    suspend fun exerciseNameSuggestions(): List<String> = exerciseDao.getDistinctNames()

    /** Applies one accepted weekly-progression row. Nothing else is touched. */
    suspend fun updateEntry(planExerciseId: String, sets: Int, reps: Int) {
        val entry = planExerciseDao.getById(planExerciseId) ?: return
        planExerciseDao.upsert(
            entry.copy(
                sets = sets.coerceIn(1, 20),
                reps = reps.coerceIn(1, 999),
            ),
        )
    }

    // ----- Plan progression (Part 4) --------------------------------------------

    suspend fun increaseReps() = database.withTransaction {
        planDayDao.getAll().forEach { day ->
            planExerciseDao.getEntries(day.id).forEach { row ->
                val newValue = if (row.unit == ExerciseUnit.SECONDS) {
                    (row.reps + 10).coerceAtMost(600)
                } else {
                    (row.reps + 1).coerceAtMost(100)
                }
                planExerciseDao.updateReps(row.planExerciseId, newValue)
            }
        }
    }

    suspend fun increaseSets() = database.withTransaction {
        planDayDao.getAll().forEach { day ->
            planExerciseDao.getEntries(day.id).forEach { row ->
                planExerciseDao.updateSets(row.planExerciseId, (row.sets + 1).coerceAtMost(10))
            }
        }
    }

    suspend fun deload() = database.withTransaction {
        planDayDao.getAll().forEach { day ->
            planExerciseDao.getEntries(day.id).forEach { row ->
                val newValue = if (row.unit == ExerciseUnit.SECONDS) {
                    (row.reps - 10).coerceAtLeast(10)
                } else {
                    (row.reps - 2).coerceAtLeast(1)
                }
                planExerciseDao.updateReps(row.planExerciseId, newValue)
            }
        }
    }
}
