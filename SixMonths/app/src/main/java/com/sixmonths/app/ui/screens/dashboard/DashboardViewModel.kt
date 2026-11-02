package com.sixmonths.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixmonths.app.core.model.SessionStatus
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.entity.StreakState
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.data.model.PlanSnapshot
import com.sixmonths.app.data.prefs.Settings
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.domain.PlanProgression
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate

/** Everything the home screen shows, derived from Room + DataStore flows. */
class DashboardViewModel : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val today: DaySnapshot? = null,
        val plan: PlanSnapshot? = null,
        val streak: StreakState = StreakState(),
        val settings: Settings = Settings(),
        val completedToday: Boolean = false,
        val sessionsThisWeek: Int = 0,
        val plannedThisWeek: Int = 0,
        /** Planned training days whose session is already completed this week. */
        val trainingDaysDoneThisWeek: Int = 0,
        val completedThisPhase: Int = 0,
        val progressionDue: Boolean = false,
    )

    private val sessionsFlow = ServiceLocator.sessionDao
        .observeWithStatus(SessionStatus.COMPLETED, 500)

    val state: StateFlow<UiState> = combine(
        ServiceLocator.planRepository.observePlan(),
        ServiceLocator.streakDao.observeState(),
        ServiceLocator.settingsRepository.settings,
        sessionsFlow,
    ) { plan, streak, settings, sessions ->
        val today = LocalDate.now()
        val todaySnapshot = plan.dayFor(today.dayOfWeek.value)
        val sessionDates = sessions.map { session ->
            session to DateUtils.toLocalDate(session.finishedAt ?: session.startedAt)
        }
        val weekStart = today.with(DayOfWeek.MONDAY)
        val weekEnd = weekStart.plusDays(6)
        val phaseStart = settings.planPhaseStartedAt
            .takeIf { it > 0L }
            ?.let { DateUtils.toLocalDate(it) }

        UiState(
            loading = false,
            today = todaySnapshot,
            plan = plan,
            streak = streak ?: StreakState(),
            settings = settings,
            completedToday = todaySnapshot != null && sessionDates.any { (session, date) ->
                date == today && session.planDayId == todaySnapshot.planDay.id
            },
            sessionsThisWeek = sessionDates.count { (_, date) -> date in weekStart..weekEnd },
            plannedThisWeek = plan.trainingDays.size,
            trainingDaysDoneThisWeek = sessionDates
                .filter { (_, date) -> date in weekStart..weekEnd }
                .mapNotNull { (session, _) -> session.planDayId }
                .toSet()
                .count { planDayId -> plan.trainingDays.any { it.planDay.id == planDayId } },
            completedThisPhase = if (phaseStart == null) {
                0
            } else {
                sessionDates.count { (_, date) -> date >= phaseStart }
            },
            progressionDue = PlanProgression.isDue(settings),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState(),
    )
}
