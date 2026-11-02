package com.sixmonths.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sixmonths.app.data.entity.Session
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: Session)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(sessions: List<Session>)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()

    @Update
    suspend fun update(session: Session)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): Session?

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: String): Flow<Session?>

    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY startedAt DESC LIMIT 1")
    suspend fun getLatestWithStatus(status: String): Session?

    @Query("SELECT * FROM sessions ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY startedAt DESC LIMIT :limit")
    fun observeWithStatus(status: String, limit: Int): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE status = :status ORDER BY startedAt ASC")
    suspend fun getAllWithStatus(status: String): List<Session>

    @Query("SELECT COUNT(*) FROM sessions WHERE status = :status")
    fun observeCountWithStatus(status: String): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM sessions WHERE status = :status AND startedAt >= :from AND startedAt < :to",
    )
    suspend fun countWithStatusBetween(status: String, from: Long, to: Long): Int

    @Query(
        "SELECT * FROM sessions WHERE status = :status AND startedAt >= :from AND startedAt < :to " +
            "ORDER BY startedAt DESC",
    )
    suspend fun getWithStatusBetween(status: String, from: Long, to: Long): List<Session>
}
