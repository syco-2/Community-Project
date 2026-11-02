package com.sixmonths.app.data.repository

import com.sixmonths.app.core.model.SessionStatus
import com.sixmonths.app.core.util.newId
import com.sixmonths.app.data.dao.SessionDao
import com.sixmonths.app.data.dao.SetLogDao
import com.sixmonths.app.data.entity.Session
import com.sixmonths.app.data.entity.SetLog
import com.sixmonths.app.domain.StreakEngine
import kotlinx.coroutines.flow.Flow

/** Starts, logs and completes training sessions and hands the result to the streak engine. */
class WorkoutRepository(
    private val sessionDao: SessionDao,
    private val setLogDao: SetLogDao,
    private val streakEngine: StreakEngine,
) {

    suspend fun startSession(planDayId: String?): Session {
        val active = sessionDao.getLatestWithStatus(SessionStatus.ACTIVE)
        if (active != null) {
            if (active.planDayId == planDayId) return active
            abandonSession(active.id)
        }
        val session = Session(
            id = newId(),
            planDayId = planDayId,
            startedAt = System.currentTimeMillis(),
            status = SessionStatus.ACTIVE,
        )
        sessionDao.insert(session)
        return session
    }

    suspend fun logSet(
        sessionId: String,
        exerciseId: String,
        planExerciseId: String,
        setIndex: Int,
        repsCompleted: Int,
    ): SetLog {
        val entry = SetLog(
            id = newId(),
            sessionId = sessionId,
            exerciseId = exerciseId,
            planExerciseId = planExerciseId,
            setIndex = setIndex,
            repsCompleted = repsCompleted.coerceAtLeast(0),
            completedAt = System.currentTimeMillis(),
        )
        setLogDao.insert(entry)
        return entry
    }

    suspend fun completeSession(sessionId: String): StreakEngine.Result? {
        val session = sessionDao.getById(sessionId) ?: return null
        if (session.status != SessionStatus.COMPLETED) {
            val now = System.currentTimeMillis()
            sessionDao.update(
                session.copy(
                    status = SessionStatus.COMPLETED,
                    finishedAt = now,
                    durationSeconds = ((now - session.startedAt) / 1000L).toInt().coerceAtLeast(0),
                ),
            )
        }
        return streakEngine.evaluate()
    }

    suspend fun abandonSession(sessionId: String) {
        val session = sessionDao.getById(sessionId) ?: return
        if (session.status != SessionStatus.ACTIVE) return
        val now = System.currentTimeMillis()
        sessionDao.update(
            session.copy(
                status = SessionStatus.ABANDONED,
                finishedAt = now,
                durationSeconds = ((now - session.startedAt) / 1000L).toInt().coerceAtLeast(0),
            ),
        )
    }

    fun observeSession(sessionId: String): Flow<Session?> = sessionDao.observeById(sessionId)

    fun observeSessionSets(sessionId: String): Flow<List<SetLog>> = setLogDao.observeForSession(sessionId)

    suspend fun latestCompletedSession(): Session? =
        sessionDao.getLatestWithStatus(SessionStatus.COMPLETED)

    suspend fun completedSessionsBetween(from: Long, to: Long): List<Session> =
        sessionDao.getWithStatusBetween(SessionStatus.COMPLETED, from, to)

    /** Active and completed sessions in a window: used to stop reminders once training starts. */
    suspend fun sessionsBetween(from: Long, to: Long): List<Session> =
        sessionDao.getWithStatusBetween(SessionStatus.ACTIVE, from, to) +
            sessionDao.getWithStatusBetween(SessionStatus.COMPLETED, from, to)

    suspend fun completedSessionsSince(from: Long): List<Session> =
        sessionDao.getWithStatusBetween(SessionStatus.COMPLETED, from, Long.MAX_VALUE)
}
