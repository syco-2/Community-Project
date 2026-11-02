package com.sixmonths.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Single-row table holding the streak state (always [SINGLETON_ID]).
 *
 * The spec lists this state as part of the data model but also mentions DataStore for
 * "settings and streak state". It is implemented here as a Room row so that the exact
 * field list lives in one place and so streak updates share transactions with session
 * logging. DataStore holds [com.sixmonths.app.data.prefs.Settings] only.
 *
 * [lastCompletedDate] is an ISO local date ("yyyy-MM-dd"), never a UTC timestamp.
 *
 * Two internal bookkeeping fields are added to the specified list:
 * - [lastEvaluatedDate]: newest day the missed-day check already processed. Keeps the
 *   evaluation idempotent (no double freeze consumption when the app opens twice a day).
 * - [penaltyPending]: set when an unprotected missed training day was found and cleared
 *   when Penalty Mode is acknowledged, so a kill during the penalty cannot skip it.
 */
@Entity(tableName = "streak_state")
data class StreakState(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastCompletedDate: String? = null,
    val lastEvaluatedDate: String? = null,
    val freezeTokens: Int = 0,
    val penaltyPending: Boolean = false,
    val totalCompletedSessions: Int = 0,
    val lastPenaltyAt: Long? = null,
) {
    companion object {
        const val SINGLETON_ID: Int = 1
    }
}
