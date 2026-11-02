package com.sixmonths.app.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupFile(
    val backupSchemaVersion: Int,
    val exportedAt: Long,
    val exercises: List<BackupExercise> = emptyList(),
    val planDays: List<BackupPlanDay> = emptyList(),
    val planExercises: List<BackupPlanExercise> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
    val setLogs: List<BackupSetLog> = emptyList(),
    val monsters: List<BackupMonster> = emptyList(),
    val streakState: BackupStreakState? = null,
    val settings: BackupSettings = BackupSettings(),
    /** Songs and can photos stored beside backup.json inside the archive. */
    val media: List<BackupMediaFile> = emptyList(),
)

@Serializable
data class BackupMediaFile(
    /** Entry name inside the archive, e.g. "music/3_Ultra Blue.mp3". */
    val entry: String,
    /** URI the entry belongs to, so the playlist can be rewired after a restore. */
    val originalUri: String,
)

@Serializable
data class BackupExercise(
    val id: String,
    val name: String,
    val category: String,
    val unit: String,
    val notes: String? = null,
    val formTip: String? = null,
    val isOptional: Boolean = false,
    val createdAt: Long,
)

@Serializable
data class BackupPlanDay(
    val id: String,
    val dayOfWeek: Int,
    val state: String,
    val name: String? = null,
)

@Serializable
data class BackupPlanExercise(
    val id: String,
    val planDayId: String,
    val exerciseId: String,
    val label: String,
    val sets: Int,
    val reps: Int,
    val restSeconds: Int,
    val sortOrder: Int,
)

@Serializable
data class BackupSession(
    val id: String,
    val planDayId: String? = null,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: String,
    val durationSeconds: Int? = null,
)

@Serializable
data class BackupSetLog(
    val id: String,
    val sessionId: String,
    val exerciseId: String,
    /** Added in schema 4; empty in older backups and resolved on resume. */
    val planExerciseId: String = "",
    val setIndex: Int,
    val repsCompleted: Int,
    val completedAt: Long,
)

@Serializable
data class BackupMonster(
    val id: String,
    val name: String,
    val type: String,
    val imageUri: String,
    val capturedAt: Long,
    /** Cabinet species id. Blank in schema 1–2 backups and for pre-cabinet captures. */
    val speciesId: String = "",
)

@Serializable
data class BackupStreakState(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastCompletedDate: String? = null,
    val freezeTokens: Int,
    val totalCompletedSessions: Int,
    val lastPenaltyAt: Long? = null,
    val lastEvaluatedDate: String? = null,
    val penaltyPending: Boolean = false,
)

@Serializable
data class BackupSettings(
    val onboardingCompleted: Boolean = false,
    val onboardingStep: Int = 1,
    val reminderTime: String = "19:00",
    val reminderDays: Set<Int> = emptySet(),
    val musicPlaylistUris: List<String> = emptyList(),
    val planPhase: Int = 1,
    val planPhaseStartedAt: Long = 0L,
    val cycleStartedAt: Long = 0L,
    val cycleIsExplicit: Boolean = false,
    val animationsEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    val musicWithWorkout: Boolean = true,
    val autoBackupEnabled: Boolean = false,
    val futureMessage: String? = null,
    val futureMessageRevealed: Boolean = false,
    val lastProgressionReviewWeek: String? = null,
    val backupSchemaVersion: Int = 1,
)
