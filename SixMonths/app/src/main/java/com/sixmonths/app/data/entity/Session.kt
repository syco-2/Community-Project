package com.sixmonths.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sixmonths.app.core.model.SessionStatus

/**
 * A training session. [planDayId] is null for free sessions; it is set to null when the
 * plan day behind a session is deleted (history is kept, never cascaded away).
 * All timestamps are epoch millis; streak decisions use ISO local dates derived from them.
 */
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = PlanDay::class,
            parentColumns = ["id"],
            childColumns = ["planDayId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["planDayId"]),
        Index(value = ["startedAt"]),
        Index(value = ["status"]),
    ],
)
data class Session(
    @PrimaryKey val id: String,
    val planDayId: String?,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: String = SessionStatus.ACTIVE,
    val durationSeconds: Int? = null,
)
