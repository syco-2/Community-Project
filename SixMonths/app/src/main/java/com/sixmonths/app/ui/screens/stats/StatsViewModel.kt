package com.sixmonths.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixmonths.app.core.model.SessionStatus
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.entity.Session
import com.sixmonths.app.data.entity.StreakState
import com.sixmonths.app.di.ServiceLocator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate

class StatsViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val streak: StreakState = StreakState(),
        val totalSessions: Int = 0,
        val totalSets: Int = 0,
        val totalReps: Int = 0,
        val totalDurationSeconds: Int = 0,
        val heatmap: Map<LocalDate, Int> = emptyMap(),
        val weeklyCounts: List<Pair<LocalDate, Int>> = emptyList(),
        val recentSessions: List<Session> = emptyList(),
        val plannedTrainingDays: Int = 0,
    )

    val state: StateFlow<UiState> = combine(
        ServiceLocator.streakDao.observeState(),
        ServiceLocator.sessionDao.observeWithStatus(SessionStatus.COMPLETED, 400),
        ServiceLocator.setLogDao.observeTotalSets(),
        ServiceLocator.setLogDao.observeTotalReps(),
        ServiceLocator.planRepository.observePlan(),
    ) { streak, sessions, totalSets, totalReps, plan ->
        val today = LocalDate.now()
        val thisMonday = today.with(DayOfWeek.MONDAY)
        val weeks = (7 downTo 0).map { offset -> thisMonday.minusWeeks(offset.toLong()) }

        val dates = sessions.map { session ->
            session to DateUtils.toLocalDate(session.finishedAt ?: session.startedAt)
        }
        val heatmap = dates.groupingBy { it.second }.eachCount()

        UiState(
            loading = false,
            streak = streak ?: StreakState(),
            totalSessions = sessions.size,
            totalSets = totalSets,
            totalReps = totalReps,
            totalDurationSeconds = sessions.sumOf { it.durationSeconds ?: 0 },
            heatmap = heatmap,
            weeklyCounts = weeks.map { monday ->
                val end = monday.plusDays(6)
                monday to dates.count { (_, date) -> date >= monday && date <= end }
            },
            recentSessions = sessions.take(8),
            plannedTrainingDays = plan.trainingDays.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )
}
