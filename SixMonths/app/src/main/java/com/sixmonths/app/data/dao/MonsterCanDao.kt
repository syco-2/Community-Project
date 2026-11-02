package com.sixmonths.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sixmonths.app.data.entity.MonsterCanSpecies
import kotlinx.coroutines.flow.Flow

@Dao
interface MonsterCanDao {

    @Query("SELECT * FROM monster_can_species ORDER BY slot ASC")
    fun observeAll(): Flow<List<MonsterCanSpecies>>

    @Query("SELECT * FROM monster_can_species ORDER BY slot ASC")
    suspend fun getAll(): List<MonsterCanSpecies>

    @Query("SELECT COUNT(*) FROM monster_can_species")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(species: List<MonsterCanSpecies>)

    @Query("UPDATE monster_can_species SET theme = :theme WHERE id = :id")
    suspend fun updateTheme(id: String, theme: String)

    @Query("DELETE FROM monster_can_species WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM monster_can_species")
    suspend fun deleteAll()
}
