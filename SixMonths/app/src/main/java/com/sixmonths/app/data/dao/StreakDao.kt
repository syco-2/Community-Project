package com.sixmonths.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.sixmonths.app.data.entity.StreakState
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakDao {

    @Query("SELECT * FROM streak_state WHERE id = ${StreakState.SINGLETON_ID}")
    fun observeState(): Flow<StreakState?>

    @Query("SELECT * FROM streak_state WHERE id = ${StreakState.SINGLETON_ID}")
    suspend fun getState(): StreakState?

    @Upsert
    suspend fun upsert(state: StreakState)

    @Query("DELETE FROM streak_state")
    suspend fun deleteAll()
}
