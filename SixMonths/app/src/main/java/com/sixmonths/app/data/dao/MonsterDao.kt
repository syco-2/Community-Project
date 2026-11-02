package com.sixmonths.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sixmonths.app.data.entity.Monster
import kotlinx.coroutines.flow.Flow

@Dao
interface MonsterDao {

    @Query("SELECT * FROM monsters ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<Monster>>

    @Query("SELECT * FROM monsters ORDER BY capturedAt DESC")
    suspend fun getAll(): List<Monster>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(monster: Monster)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(monsters: List<Monster>)

    @Query("DELETE FROM monsters WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Moves a capture onto the flavour that stays in the cabinet. */
    @Query("UPDATE monsters SET speciesId = :newFlavorId WHERE speciesId = :oldFlavorId")
    suspend fun reassignFlavor(oldFlavorId: String, newFlavorId: String)

    @Query("DELETE FROM monsters")
    suspend fun deleteAll()
}
