package com.sixmonths.app.domain

import android.util.Log
import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.SessionStatus
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.dao.PlanDayDao
import com.sixmonths.app.data.dao.SessionDao
import com.sixmonths.app.data.dao.StreakDao
import com.sixmonths.app.data.entity.StreakState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.ZoneId

/**
 * The only source of truth for the streak.
 *
 * Called on app start, on every resume and after a session completes. WorkManager is
 * never involved in timing. Rules live in [StreakCalculator]; this class loads the data,
 * writes the result and detects timezone changes.
 */
class StreakEngine(
    private val planDayDao: PlanDayDao,
    private val sessionDao: SessionDao,
    private val streakDao: StreakDao,
    private val clock: () -> LocalDate = { LocalDate.now() },
    private val cycleStartMillis: suspend () -> Long = { 0L },
) {

    data class Result(
        val state: StreakState,
        val missedTrainingDays: Int,
        val frozenMisses: Int,
        val hasTrainingDays: Boolean,
    )

    private val mutex = Mutex()
    private var lastKnownZone: ZoneId? = null

    suspend fun evaluate(today: LocalDate = clock()): Result = mutex.withLock {
        logTimezoneChangeIfAny()

        val planDays = planDayDao.getAll()
        val trainingWeekdays = planDays
            .asSequence()
            .filter { it.state == DayState.TRAINING }
            .map { it.dayOfWeek }
            .toSet()

        val allCompletedSessions = sessionDao.getAllWithStatus(SessionStatus.COMPLETED)
        val cycleStart = cycleStartMillis()
        val completedSessions = allCompletedSessions.filter { it.startedAt >= cycleStart }
        val completedDates = completedSessions.map { session ->
            DateUtils.toLocalDate(session.finishedAt ?: session.startedAt)
        }.toSet()

        val current = streakDao.getState() ?: StreakState(
            freezeTokens = INITIAL_FREEZE_TOKENS,
            lastEvaluatedDate = today.minusDays(1).toString(),
        )

        val output = StreakCalculator.evaluate(
            StreakInput(
                trainingWeekdays = trainingWeekdays,
                completedDates = completedDates,
                state = current.copy(totalCompletedSessions = allCompletedSessions.size),
                today = today,
            ),
        )

        streakDao.upsert(output.state)

        Result(
            state = output.state,
            missedTrainingDays = output.missedTrainingDays,
            frozenMisses = output.frozenMisses,
            hasTrainingDays = output.hasTrainingDays,
        )
    }

    /** Starts a new six-month cycle with a fresh streak; history and longest streak stay. */
    suspend fun resetForNewCycle() = mutex.withLock {
        val state = streakDao.getState() ?: StreakState()
        streakDao.upsert(
            state.copy(
                currentStreak = 0,
                lastCompletedDate = null,
                lastEvaluatedDate = clock().minusDays(1).toString(),
            ),
        )
    }

    private fun logTimezoneChangeIfAny() {
        val zone = ZoneId.systemDefault()
        val previous = lastKnownZone
        if (previous != null && previous != zone) {
            Log.w(TAG, "Timezone changed from $previous to $zone; streak re-evaluated in local dates")
        }
        lastKnownZone = zone
    }

    companion object {
        private const val TAG = "StreakEngine"

        /**
         * Starting balance. The spec defines freezeTokens but not how they are earned, so
         * the app starts with two tokens and never refills them automatically. Documented
         * in the delivery checkpoint.
         */
        const val INITIAL_FREEZE_TOKENS: Int = 2
    }
}
