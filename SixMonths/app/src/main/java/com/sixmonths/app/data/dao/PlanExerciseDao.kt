package com.sixmonths.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.sixmonths.app.data.entity.PlanExercise
import com.sixmonths.app.data.model.PlanEntryRow
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanExerciseDao {

    @Query("SELECT * FROM plan_exercises WHERE planDayId = :planDayId ORDER BY sortOrder ASC")
    fun observeForDay(planDayId: String): Flow<List<PlanExercise>>

    @Query("SELECT * FROM plan_exercises WHERE planDayId = :planDayId ORDER BY sortOrder ASC")
    suspend fun listForDay(planDayId: String): List<PlanExercise>

    @Query("SELECT * FROM plan_exercises ORDER BY planDayId ASC, sortOrder ASC")
    suspend fun getAll(): List<PlanExercise>

    @Query("SELECT * FROM plan_exercises ORDER BY planDayId ASC, sortOrder ASC")
    fun observeAll(): Flow<List<PlanExercise>>

    /** Joined rows for the UI: plan entry + exercise name/category/unit/notes/form tip. */
    @Query(
        "SELECT pe.planDayId AS planDayId, pe.id AS planExerciseId, pe.exerciseId AS exerciseId, " +
            "e.name AS name, e.category AS category, e.unit AS unit, pe.label AS label, " +
            "pe.sets AS sets, pe.reps AS reps, pe.restSeconds AS restSeconds, " +
            "pe.sortOrder AS sortOrder, e.notes AS notes, e.formTip AS formTip " +
            "FROM plan_exercises pe INNER JOIN exercises e ON e.id = pe.exerciseId " +
            "ORDER BY pe.planDayId ASC, pe.sortOrder ASC",
    )
    fun observeAllEntries(): Flow<List<PlanEntryRow>>

    @Query(
        "SELECT pe.planDayId AS planDayId, pe.id AS planExerciseId, pe.exerciseId AS exerciseId, " +
            "e.name AS name, e.category AS category, e.unit AS unit, pe.label AS label, " +
            "pe.sets AS sets, pe.reps AS reps, pe.restSeconds AS restSeconds, " +
            "pe.sortOrder AS sortOrder, e.notes AS notes, e.formTip AS formTip " +
            "FROM plan_exercises pe INNER JOIN exercises e ON e.id = pe.exerciseId " +
            "WHERE pe.planDayId = :planDayId ORDER BY pe.sortOrder ASC",
    )
    suspend fun getEntries(planDayId: String): List<PlanEntryRow>

    @Query("SELECT * FROM plan_exercises WHERE id = :id")
    suspend fun getById(id: String): PlanExercise?

    @Query("SELECT COUNT(*) FROM plan_exercises WHERE planDayId = :planDayId")
    suspend fun countForDay(planDayId: String): Int

    @Upsert
    suspend fun upsert(entry: PlanExercise)

    @Upsert
    suspend fun upsertAll(entries: List<PlanExercise>)

    @Delete
    suspend fun delete(entry: PlanExercise)

    @Query("DELETE FROM plan_exercises WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM plan_exercises WHERE planDayId = :planDayId")
    suspend fun deleteForDay(planDayId: String)

    @Query("DELETE FROM plan_exercises")
    suspend fun deleteAllRows()

    @Query("UPDATE plan_exercises SET reps = :reps WHERE id = :id")
    suspend fun updateReps(id: String, reps: Int)

    @Query("UPDATE plan_exercises SET sets = :sets WHERE id = :id")
    suspend fun updateSets(id: String, sets: Int)

    /** Replaces a whole day in one transaction, used by the Day Editor and by import. */
    @Transaction
    suspend fun replaceForDay(planDayId: String, entries: List<PlanExercise>) {
        deleteForDay(planDayId)
        if (entries.isNotEmpty()) {
            upsertAll(entries)
        }
    }
}
