package com.sixmonths.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An entry of the user's plan on one weekday. [sets] x [reps] is what the Day Editor shows
 * as the "3 x 6" badge; when the exercise unit is SECONDS, [reps] is interpreted as seconds.
 */
@Entity(
    tableName = "plan_exercises",
    foreignKeys = [
        ForeignKey(
            entity = PlanDay::class,
            parentColumns = ["id"],
            childColumns = ["planDayId"],
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
        Index(value = ["planDayId"]),
        Index(value = ["exerciseId"]),
        Index(value = ["planDayId", "sortOrder"]),
    ],
)
data class PlanExercise(
    @PrimaryKey val id: String,
    val planDayId: String,
    val exerciseId: String,
    val label: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val sortOrder: Int,
)
