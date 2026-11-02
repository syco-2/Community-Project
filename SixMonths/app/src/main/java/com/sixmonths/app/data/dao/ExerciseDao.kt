package com.sixmonths.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.sixmonths.app.data.entity.Exercise
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun getById(id: String): Exercise?

    @Query("SELECT * FROM exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): Exercise?

    @Query("SELECT * FROM exercises WHERE category = :category ORDER BY name COLLATE NOCASE ASC")
    suspend fun getByCategory(category: String): List<Exercise>

    /** Distinct names of exercises the user already created, for autocomplete. */
    @Query("SELECT DISTINCT name FROM exercises ORDER BY name COLLATE NOCASE ASC")
    suspend fun getDistinctNames(): List<String>

    @Upsert
    suspend fun upsert(exercise: Exercise)

    @Upsert
    suspend fun upsertAll(exercises: List<Exercise>)

    @Delete
    suspend fun delete(exercise: Exercise)

    @Query("DELETE FROM exercises WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM exercises")
    suspend fun deleteAll()
}
