package com.sixmonths.app.ui.screens.dayeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.core.model.Weekday
import com.sixmonths.app.core.util.newId
import com.sixmonths.app.data.model.PlanEntryDraft
import com.sixmonths.app.data.model.PlanEntryRow
import com.sixmonths.app.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Day Editor state. Every structural change (add/edit/duplicate/delete/reorder) is saved
 * immediately, so an app kill never loses plan edits.
 */
class DayEditorViewModel(private val dayOfWeek: Int) : ViewModel() {

    data class EditorEntry(
        val planExerciseId: String,
        val exerciseId: String?,
        val name: String,
        val category: String,
        val unit: String,
        val label: String,
        val sets: Int,
        val reps: Int,
        val restSeconds: Int,
        val notes: String?,
        val formTip: String?,
    )

    data class UiState(
        val loading: Boolean = true,
        val dayName: String? = null,
        val dayState: String = DayState.REST,
        val entries: List<EditorEntry> = emptyList(),
        val suggestions: List<String> = emptyList(),
        val savedAt: Long? = null,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val saveMutex = Mutex()

    init {
        viewModelScope.launch {
            ServiceLocator.planRepository.ensureWeek()
            val plan = ServiceLocator.planRepository.loadPlan()
            val day = plan.dayFor(dayOfWeek)
            _state.value = UiState(
                loading = false,
                dayName = day?.planDay?.name,
                dayState = day?.planDay?.state ?: DayState.REST,
                entries = day?.entries?.map { it.toEditorEntry() }.orEmpty(),
                suggestions = runCatching {
                    ServiceLocator.planRepository.exerciseNameSuggestions()
                }.getOrDefault(emptyList()),
            )
        }
    }

    fun saveEntry(originalId: String?, entry: EditorEntry) {
        val current = _state.value.entries
        val updated = if (originalId == null) {
            current + entry
        } else {
            current.map { if (it.planExerciseId == originalId) entry else it }
        }
        apply(updated)
    }

    fun deleteEntry(planExerciseId: String) {
        apply(_state.value.entries.filterNot { it.planExerciseId == planExerciseId })
    }

    fun duplicateEntry(planExerciseId: String) {
        val current = _state.value.entries
        val index = current.indexOfFirst { it.planExerciseId == planExerciseId }
        if (index < 0) return
        val copy = current[index].copy(planExerciseId = newId())
        val updated = current.toMutableList().apply { add(index + 1, copy) }
        apply(updated)
    }

    fun moveEntry(from: Int, to: Int) {
        val current = _state.value.entries
        if (from !in current.indices || to !in current.indices || from == to) return
        val updated = current.toMutableList().apply {
            val item = removeAt(from)
            add(to, item)
        }
        apply(updated)
    }

    fun renameDay(name: String) {
        viewModelScope.launch {
            ServiceLocator.planRepository.setDayName(dayOfWeek, name)
            _state.value = _state.value.copy(dayName = name.trim().takeIf { it.isNotBlank() })
        }
    }

    private fun apply(entries: List<EditorEntry>) {
        val renumbered = entries.mapIndexed { index, entry ->
            if (AUTO_LABEL.matches(entry.label.trim())) {
                entry.copy(label = "Training ${index + 1}")
            } else {
                entry
            }
        }
        _state.value = _state.value.copy(entries = renumbered, error = null)
        val snapshot = renumbered
        viewModelScope.launch {
            saveMutex.withLock {
                runCatching {
                    ServiceLocator.planRepository.saveDayEntries(
                        dayOfWeek = dayOfWeek,
                        drafts = snapshot.map { it.toDraft() },
                    )
                    _state.value = _state.value.copy(
                        savedAt = System.currentTimeMillis(),
                        suggestions = runCatching {
                            ServiceLocator.planRepository.exerciseNameSuggestions()
                        }.getOrDefault(_state.value.suggestions),
                    )
                }.onFailure { throwable ->
                    _state.value = _state.value.copy(
                        error = throwable.message ?: "Could not save the plan.",
                    )
                }
            }
        }
    }

    companion object {
        private val AUTO_LABEL = Regex("Training \\d+")
    }
}

private fun PlanEntryRow.toEditorEntry(): DayEditorViewModel.EditorEntry =
    DayEditorViewModel.EditorEntry(
        planExerciseId = planExerciseId,
        exerciseId = exerciseId,
        name = name,
        category = category.takeIf(ExerciseCategory::isValid) ?: ExerciseCategory.DEFAULT,
        unit = unit.takeIf(ExerciseUnit::isValid) ?: ExerciseUnit.REPS,
        label = label,
        sets = sets,
        reps = reps,
        restSeconds = restSeconds,
        notes = notes,
        formTip = formTip,
    )

private fun DayEditorViewModel.EditorEntry.toDraft(): PlanEntryDraft = PlanEntryDraft(
    planExerciseId = planExerciseId,
    exerciseId = exerciseId,
    name = name,
    category = category,
    unit = unit,
    label = label.ifBlank { "Training" },
    sets = sets,
    reps = reps,
    restSeconds = restSeconds,
    notes = notes,
    formTip = formTip,
)

/** Used by the UI as a default label for a new entry. */
fun defaultTrainingLabel(entryCount: Int): String = "Training ${entryCount + 1}"

/** Weekday name for the top bar; falls back to the weekday number. */
fun dayTitle(dayOfWeek: Int): String =
    if (Weekday.isValid(dayOfWeek)) Weekday.fullName(dayOfWeek) else "Day $dayOfWeek"
