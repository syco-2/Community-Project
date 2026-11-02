package com.sixmonths.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One logged set inside a [Session]. [repsCompleted] holds reps or seconds, matching the
 * exercise unit at the time of logging.
 */
@Entity(
    tableName = "set_logs",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["exerciseId"]),
        Index(value = ["sessionId", "exerciseId", "setIndex"]),
    ],
)
data class SetLog(
    @PrimaryKey val id: String,
    val sessionId: String,
    val exerciseId: String,
    /** The exact plan row. Two rows can share an exercise; only this id is unique. */
    @ColumnInfo(defaultValue = "")
    val planExerciseId: String = "",
    val setIndex: Int,
    val repsCompleted: Int,
    val completedAt: Long,
)
