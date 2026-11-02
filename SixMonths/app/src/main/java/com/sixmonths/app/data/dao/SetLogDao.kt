package com.sixmonths.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sixmonths.app.data.entity.SetLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SetLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(log: SetLog)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(logs: List<SetLog>)

    @Delete
    suspend fun delete(log: SetLog)

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY completedAt ASC")
    fun observeForSession(sessionId: String): Flow<List<SetLog>>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY completedAt ASC")
    suspend fun listForSession(sessionId: String): List<SetLog>

    @Query(
        "SELECT * FROM set_logs WHERE sessionId = :sessionId AND exerciseId = :exerciseId " +
            "ORDER BY setIndex ASC",
    )
    suspend fun listForExercise(sessionId: String, exerciseId: String): List<SetLog>

    @Query("SELECT COALESCE(SUM(repsCompleted), 0) FROM set_logs")
    fun observeTotalReps(): Flow<Int>

    @Query("SELECT COUNT(*) FROM set_logs")
    fun observeTotalSets(): Flow<Int>

    @Query("DELETE FROM set_logs WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: String)

    @Query("DELETE FROM set_logs")
    suspend fun deleteAll()
}
