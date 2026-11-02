package com.sixmonths.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.sixmonths.app.data.entity.PlanDay
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanDayDao {

    @Query("SELECT * FROM plan_days ORDER BY dayOfWeek ASC")
    fun observeAll(): Flow<List<PlanDay>>

    @Query("SELECT * FROM plan_days ORDER BY dayOfWeek ASC")
    suspend fun getAll(): List<PlanDay>

    @Query("SELECT * FROM plan_days WHERE dayOfWeek = :dayOfWeek LIMIT 1")
    suspend fun getByDayOfWeek(dayOfWeek: Int): PlanDay?

    @Query("SELECT * FROM plan_days WHERE id = :id")
    suspend fun getById(id: String): PlanDay?

    @Query("SELECT * FROM plan_days WHERE state = :state ORDER BY dayOfWeek ASC")
    suspend fun getByState(state: String): List<PlanDay>

    @Upsert
    suspend fun upsert(day: PlanDay)

    @Upsert
    suspend fun upsertAll(days: List<PlanDay>)

    @Delete
    suspend fun delete(day: PlanDay)

    @Query("DELETE FROM plan_days")
    suspend fun deleteAll()
}
