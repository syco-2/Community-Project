package com.sixmonths.app.data.backup

import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.core.model.SessionStatus
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.data.prefs.Settings

/**
 * Pure validation of an imported backup. Every problem is collected so the error shown to
 * the user names the first concrete issue instead of crashing mid-import.
 */
object BackupValidator {

    fun errors(backup: BackupFile): List<String> {
        val errors = mutableListOf<String>()

        // v1 files stay importable; their missing fields keep their defaults. Anything
        // newer than this build is rejected before a single row is touched.
        if (backup.backupSchemaVersion !in 1..Settings.BACKUP_SCHEMA_VERSION) {
            errors += "Unsupported backup schema version ${backup.backupSchemaVersion}. " +
                "This build supports versions 1 to ${Settings.BACKUP_SCHEMA_VERSION}."
            return errors
        }

        val exerciseIds = backup.exercises.map { it.id }.toSet()
        if (exerciseIds.size != backup.exercises.size) errors += "Duplicate exercise ids."
        backup.exercises.forEach { exercise ->
            if (exercise.id.isBlank()) errors += "An exercise has no id."
            if (exercise.name.isBlank()) errors += "Exercise ${exercise.id} has no name."
            if (!ExerciseCategory.isValid(exercise.category)) {
                errors += "Exercise '${exercise.name}' has unknown category '${exercise.category}'."
            }
            if (!ExerciseUnit.isValid(exercise.unit)) {
                errors += "Exercise '${exercise.name}' has unknown unit '${exercise.unit}'."
            }
        }

        val dayIds = backup.planDays.map { it.id }.toSet()
        if (dayIds.size != backup.planDays.size) errors += "Duplicate plan day ids."
        val weekdays = backup.planDays.map { it.dayOfWeek }
        if (weekdays.size != weekdays.distinct().size) errors += "Two plan days share a weekday."
        backup.planDays.forEach { day ->
            if (day.dayOfWeek !in 1..7) errors += "Plan day ${day.id} has weekday ${day.dayOfWeek}."
            if (!DayState.isValid(day.state)) errors += "Plan day ${day.id} has unknown state '${day.state}'."
        }

        val planExerciseIds = backup.planExercises.map { it.id }.toSet()
        if (planExerciseIds.size != backup.planExercises.size) errors += "Duplicate plan exercise ids."
        backup.planExercises.forEach { entry ->
            if (entry.planDayId !in dayIds) errors += "Plan entry ${entry.id} points to a missing day."
            if (entry.exerciseId !in exerciseIds) errors += "Plan entry ${entry.id} points to a missing exercise."
            if (entry.sets < 1 || entry.reps < 1) errors += "Plan entry ${entry.id} has invalid sets or reps."
            if (entry.restSeconds < 0) errors += "Plan entry ${entry.id} has negative rest seconds."
            if (entry.sortOrder < 0) errors += "Plan entry ${entry.id} has a negative sort order."
        }

        val sessionIds = backup.sessions.map { it.id }.toSet()
        if (sessionIds.size != backup.sessions.size) errors += "Duplicate session ids."
        backup.sessions.forEach { session ->
            if (!SessionStatus.isValid(session.status)) errors += "Session ${session.id} has unknown status."
            if (session.planDayId != null && session.planDayId !in dayIds) {
                errors += "Session ${session.id} points to a missing plan day."
            }
        }

        val setLogIds = backup.setLogs.map { it.id }.toSet()
        if (setLogIds.size != backup.setLogs.size) errors += "Duplicate set ids."
        backup.setLogs.forEach { log ->
            if (log.sessionId !in sessionIds) errors += "Set ${log.id} points to a missing session."
            if (log.exerciseId !in exerciseIds) errors += "Set ${log.id} points to a missing exercise."
            if (log.repsCompleted < 0) errors += "Set ${log.id} has negative reps."
        }

        val monsterIds = backup.monsters.map { it.id }.toSet()
        if (monsterIds.size != backup.monsters.size) errors += "Duplicate monster ids."
        backup.monsters.forEach { monster ->
            if (monster.id.isBlank()) errors += "A monster has no id."
            if (monster.name.isBlank()) errors += "Monster ${monster.id} has no name."
            if (monster.type.isBlank()) errors += "Monster ${monster.id} has no type."
            if (monster.imageUri.isBlank()) errors += "Monster ${monster.id} has no image."
            if (monster.capturedAt < 0) errors += "Monster ${monster.id} has a negative timestamp."
        }

        backup.streakState?.let { state ->
            if (state.currentStreak < 0 || state.longestStreak < 0 || state.freezeTokens < 0) {
                errors += "Streak state contains negative values."
            }
            listOfNotNull(state.lastCompletedDate, state.lastEvaluatedDate).forEach { iso ->
                if (DateUtils.parseOrNull(iso) == null) {
                    errors += "Streak date '$iso' is not ISO yyyy-MM-dd."
                }
            }
        }

        if (backup.settings.onboardingStep !in 1..Settings.ONBOARDING_STEPS) {
            errors += "Onboarding step ${backup.settings.onboardingStep} is out of range."
        }
        if ((backup.settings.futureMessage?.length ?: 0) > MAX_MESSAGE_LENGTH) {
            errors += "The future message is longer than $MAX_MESSAGE_LENGTH characters."
        }
        if (backup.settings.reminderDays.any { it !in 1..7 }) {
            errors += "Reminder days must be between 1 and 7."
        }
        backup.settings.musicPlaylistUris.forEach { uri ->
            if (!uri.startsWith("content://") && !uri.startsWith("file://")) {
                errors += "Playlist entry '$uri' is not a content or file URI."
            }
        }

        return errors
    }

    private const val MAX_MESSAGE_LENGTH = 4_000
}
