package com.sixmonths.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.sixmonths.app.core.model.DayState

/**
 * One row per weekday. [dayOfWeek] is unique: 1 = Monday ... 7 = Sunday.
 */
@Entity(
    tableName = "plan_days",
    indices = [Index(value = ["dayOfWeek"], unique = true)],
)
data class PlanDay(
    @PrimaryKey val id: String,
    val dayOfWeek: Int,
    val state: String = DayState.REST,
    val name: String? = null,
)
