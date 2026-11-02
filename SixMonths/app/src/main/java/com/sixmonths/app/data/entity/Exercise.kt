package com.sixmonths.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.ExerciseUnit

/**
 * A single exercise the user created. There are no predefined exercises in the app.
 *
 * [category] is a mandatory string field defaulting to [ExerciseCategory.DEFAULT] (OTHER)
 * and is never null; it is the only input Coach Review uses for balance analysis.
 */
@Entity(
    tableName = "exercises",
    indices = [
        Index(value = ["category"]),
        Index(value = ["name"]),
    ],
)
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = ExerciseCategory.DEFAULT,
    val unit: String = ExerciseUnit.REPS,
    val notes: String? = null,
    val formTip: String? = null,
    val isOptional: Boolean = false,
    val createdAt: Long,
)
