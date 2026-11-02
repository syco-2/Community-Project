package com.sixmonths.app.data.prefs

/**
 * User settings, persisted with DataStore Preferences.
 *
 * The defaults are the ones the app ships with on a clean install: onboarding not done,
 * animations on, no reminder days. [backupSchemaVersion] is the version written into JSON
 * exports.
 */
data class Settings(
    val onboardingCompleted: Boolean = false,
    val onboardingStep: Int = 1,
    val reminderTime: String = "19:00",
    val reminderDays: Set<Int> = emptySet(),
    val musicPlaylistUris: List<String> = emptyList(),
    val planPhase: Int = 1,
    val planPhaseStartedAt: Long = 0L,
    /** Start of the current six-month cycle; sessions before it never count for the streak. */
    val cycleStartedAt: Long = 0L,
    /**
     * True only when a new cycle was started explicitly after the future-message reveal.
     * Editing the plan or finishing onboarding again must never hide old sessions.
     */
    val cycleIsExplicit: Boolean = false,
    val animationsEnabled: Boolean = true,
    val sfxEnabled: Boolean = true,
    /** Workout start shuffles the playlist; nothing ever lowers its volume. */
    val musicWithWorkout: Boolean = true,
    val autoBackupEnabled: Boolean = false,
    /** SAF folder the weekly backup is written into; null until the user picks one. */
    val autoBackupTreeUri: String? = null,
    val lastAutoBackupAt: Long = 0L,
    val futureMessage: String? = null,
    val futureMessageRevealed: Boolean = false,
    /** ISO Monday of the last completed week whose progression review was resolved. */
    val lastProgressionReviewWeek: String? = null,
    /** ISO Monday of the last week the trophy celebration was shown. */
    val lastTrophyWeek: String? = null,
    /** ISO date of the last day a training reminder was posted. At most one per day. */
    val lastReminderDay: String? = null,
    val backupSchemaVersion: Int = BACKUP_SCHEMA_VERSION,
) {
    companion object {
        /**
         * Schema 3 adds `monsters[].speciesId`, schema 4 adds `setLogs[].planExerciseId`,
         * schema 5 wraps everything in a ZIP with the can photos and the playlist songs.
         * Older files still import; missing fields keep their defaults.
         */
        const val BACKUP_SCHEMA_VERSION: Int = 5
        const val ONBOARDING_STEPS: Int = 7
        const val DEFAULT_REMINDER_TIME: String = "19:00"
    }
}
