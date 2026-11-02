package com.sixmonths.app.ui.screens.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.core.model.SessionStatus
import com.sixmonths.app.data.entity.Session
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.data.model.PlanEntryRow
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.domain.CircuitStep
import com.sixmonths.app.domain.TrainingBlock
import com.sixmonths.app.domain.WorkoutCircuit
import com.sixmonths.app.notifications.ReminderScheduler
import com.sixmonths.app.playback.MusicController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Circuit player state: one set at a time, in round-robin order (Skill → Planche → Training,
 * one set of each exercise per round).
 *
 * There is no rest timer. The user logs a set and rests as long as they want; a timed
 * exercise simply shows a stopwatch. Every transition reads one immutable snapshot and writes
 * a clamped index, so Skip, Pause and a slow database write can never leave the player on a
 * missing step (the crash the old rest-timer version produced).
 *
 * Progress is saved in Room as every set is logged. Reopening the app resumes the session at
 * the first set without a log, because nothing here keeps the workout in memory.
 */
class WorkoutViewModel(private val sessionId: String) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val session: Session? = null,
        val day: DaySnapshot? = null,
        val entries: List<PlanEntryRow> = emptyList(),
        val steps: List<CircuitStep> = emptyList(),
        val stepIndex: Int = 0,
        /** Rep-based work: the rep counter. */
        val currentValue: Int = 0,
        /** Timed work: elapsed seconds on the stopwatch. */
        val stopwatchSeconds: Int = 0,
        val stopwatchRunning: Boolean = false,
        /** Seconds left of the lead-in before a timed set's stopwatch starts by itself. */
        val countdownSeconds: Int = 0,
        val allDone: Boolean = false,
        val completed: Boolean = false,
        val finishing: Boolean = false,
        /** True while the set is being written; stops a double tap creating two rows. */
        val logging: Boolean = false,
        val error: String? = null,
    ) {
        val currentStep: CircuitStep? get() = steps.getOrNull(stepIndex)
        val currentEntry: PlanEntryRow? get() = currentStep?.entry
        val currentBlock: TrainingBlock get() = currentStep?.block ?: TrainingBlock.NORMAL
        val isTimeBased: Boolean get() = currentEntry?.unit == ExerciseUnit.SECONDS
        /** 1-based round, e.g. "Round 2 of 3". */
        val round: Int get() = (currentStep?.setIndex ?: 0) + 1
        val rounds: Int get() = WorkoutCircuit.rounds(steps)
        /** How many sets of the current exercise are already done. */
        val currentSetIndex: Int get() = currentStep?.setIndex ?: 0
        val totalSetsPlanned: Int get() = steps.size
        /** Sets already left behind, including skipped ones, for the progress bar. */
        val setsLogged: Int get() = stepIndex.coerceIn(0, steps.size)
        val nextStep: CircuitStep? get() = steps.getOrNull(stepIndex + 1)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Guards against a double tap logging the same set twice. */
    private var loggingInFlight = false

    private var ticker: Job? = null

    init {
        // Opening the player means the workout was started: today's reminders stop here.
        viewModelScope.launch {
            runCatching {
                val context = ServiceLocator.appContext()
                ReminderScheduler.cancel(context)
                ReminderScheduler.schedule(context, ServiceLocator.settingsRepository.current())
            }
        }
        viewModelScope.launch { load() }
        startWorkoutMusic()
    }

    /** Workout start shuffles the playlist; finishing never stops it. */
    private fun startWorkoutMusic() {
        viewModelScope.launch {
            runCatching {
                val settings = ServiceLocator.settingsRepository.current()
                if (!settings.musicWithWorkout) return@runCatching
                MusicController.startForWorkout(
                    ServiceLocator.appContext(),
                    settings.musicPlaylistUris,
                )
            }
        }
    }

    private suspend fun load() {
        runCatching {
            val session = ServiceLocator.sessionDao.getById(sessionId)
            val plan = ServiceLocator.planRepository.loadPlan()
            val day = session?.planDayId?.let { id -> plan.days.firstOrNull { it.planDay.id == id } }
            val entries = day?.entries.orEmpty()
            val steps = WorkoutCircuit.sequence(entries)
            val logs = ServiceLocator.setLogDao.listForSession(sessionId)
            // Older logs predate planExerciseId. Resolve them to the first matching step so
            // resuming an in-flight session still works after the schema upgrade.
            val logged = logs.mapNotNull { log ->
                val planExerciseId = log.planExerciseId.ifBlank {
                    steps.firstOrNull {
                        it.setIndex == log.setIndex && it.entry.exerciseId == log.exerciseId
                    }?.entry?.planExerciseId.orEmpty()
                }
                planExerciseId.takeIf { it.isNotBlank() }?.let { it to log.setIndex }
            }.toSet()

            val firstOpen = WorkoutCircuit.indexOfFirstOpen(steps, logged)
            val allDone = steps.isNotEmpty() && firstOpen < 0
            val index = when {
                steps.isEmpty() -> 0
                allDone -> steps.lastIndex
                else -> firstOpen.coerceIn(0, steps.lastIndex)
            }
            val entry = steps.getOrNull(index)?.entry

            _state.value = UiState(
                loading = false,
                session = session,
                day = day,
                entries = entries,
                steps = steps,
                stepIndex = if (steps.isEmpty()) 0 else index,
                currentValue = entry?.reps ?: 0,
                countdownSeconds = if (!allDone && entry?.unit == ExerciseUnit.SECONDS) {
                    LEAD_IN_SECONDS
                } else {
                    0
                },
                stopwatchRunning = false,
                allDone = allDone,
                completed = session?.status == SessionStatus.COMPLETED,
                error = if (session == null) {
                    "Session not found. Start a new workout from the dashboard."
                } else {
                    null
                },
            )
            if (!allDone && entry?.unit == ExerciseUnit.SECONDS) startTicker()
        }.onFailure { throwable ->
            _state.update {
                it.copy(
                    loading = false,
                    error = throwable.message ?: "Could not load the workout.",
                )
            }
        }
    }

    fun setCurrentValue(value: Int) {
        val entry = _state.value.currentEntry ?: return
        val max = if (entry.unit == ExerciseUnit.SECONDS) 600 else 200
        _state.update { it.copy(currentValue = value.coerceIn(1, max)) }
    }

    /**
     * Pause/resume the stopwatch of a timed set. During the lead-in the same tap skips the
     * wait and starts the stopwatch right away.
     */
    fun toggleTimer() {
        val current = _state.value
        if (!current.isTimeBased || current.allDone || current.logging) return
        if (current.countdownSeconds > 0) {
            _state.update { it.copy(countdownSeconds = 0, stopwatchRunning = true) }
            startTicker()
            return
        }
        val running = !current.stopwatchRunning
        _state.update { it.copy(stopwatchRunning = running) }
        if (running) startTicker() else ticker?.cancel()
    }

    fun resetTimer() {
        _state.update { it.copy(stopwatchSeconds = 0) }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val current = _state.value
                if (current.allDone) continue
                if (current.countdownSeconds > 0) {
                    _state.update { state ->
                        if (state.countdownSeconds <= 0) {
                            state
                        } else {
                            val left = state.countdownSeconds - 1
                            // Reaching zero starts the stopwatch without another tap.
                            state.copy(countdownSeconds = left, stopwatchRunning = left == 0)
                        }
                    }
                } else if (current.stopwatchRunning) {
                    _state.update { state ->
                        if (state.stopwatchRunning) {
                            state.copy(stopwatchSeconds = state.stopwatchSeconds + 1)
                        } else {
                            state
                        }
                    }
                }
            }
        }
    }

    fun logSet() {
        if (loggingInFlight) return
        val current = _state.value
        val step = current.currentStep ?: return
        if (current.allDone || current.logging) return
        val value = if (step.entry.unit == ExerciseUnit.SECONDS) {
            current.stopwatchSeconds.coerceAtLeast(1)
        } else {
            current.currentValue
        }

        val loggedIndex = current.stepIndex
        val loggedKey = step.key
        loggingInFlight = true
        _state.update { it.copy(logging = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                ServiceLocator.workoutRepository.logSet(
                    sessionId = sessionId,
                    exerciseId = step.entry.exerciseId,
                    planExerciseId = step.entry.planExerciseId,
                    setIndex = step.setIndex,
                    repsCompleted = value,
                )
            }
            loggingInFlight = false
            result.onFailure { throwable ->
                _state.update {
                    it.copy(
                        logging = false,
                        error = throwable.message ?: "Could not log the set.",
                    )
                }
                return@launch
            }

            // Re-read the state: a skip may have happened while saving. If the user already
            // moved on, never advance a second time from the new position.
            _state.update { latest ->
                val stillOnLoggedStep = latest.stepIndex == loggedIndex &&
                    latest.currentStep?.key == loggedKey
                if (!stillOnLoggedStep) {
                    latest.copy(logging = false)
                } else {
                    latest.advanceTo(latest.stepIndex + 1)
                }
            }
            val latest = _state.value
            if (latest.stopwatchRunning || latest.countdownSeconds > 0) {
                startTicker()
            } else {
                ticker?.cancel()
            }
        }
    }

    private fun UiState.advanceTo(index: Int): UiState {
        val nextIndex = index.coerceAtLeast(0)
        if (nextIndex > steps.lastIndex) {
            return copy(
                allDone = true,
                logging = false,
                stopwatchRunning = false,
            )
        }
        val next = steps.getOrNull(nextIndex) ?: return copy(allDone = true, logging = false)
        val timed = next.entry.unit == ExerciseUnit.SECONDS
        return copy(
            stepIndex = nextIndex,
            currentValue = next.entry.reps,
            stopwatchSeconds = 0,
            stopwatchRunning = false,
            countdownSeconds = if (timed) LEAD_IN_SECONDS else 0,
            allDone = false,
            logging = false,
            error = null,
        )
    }

    fun finishWorkout() {
        if (_state.value.finishing || _state.value.completed) return
        ticker?.cancel()
        _state.update {
            it.copy(finishing = true, stopwatchRunning = false)
        }
        viewModelScope.launch {
            runCatching {
                ServiceLocator.workoutRepository.completeSession(sessionId)
            }.onSuccess {
                _state.update {
                    it.copy(
                        finishing = false,
                        completed = true,
                        stopwatchRunning = false,
                        countdownSeconds = 0,
                    )
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        finishing = false,
                        error = throwable.message ?: "Could not finish the session.",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }

    private companion object {
        /** Lead-in before the stopwatch of a timed set starts counting. */
        const val LEAD_IN_SECONDS = 30
    }
}
