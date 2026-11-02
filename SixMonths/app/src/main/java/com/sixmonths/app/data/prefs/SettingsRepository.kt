package com.sixmonths.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "six_months_settings",
)

/**
 * The one place settings are read and written. Never touches the network.
 *
 * Radio-button/typed setters are intentionally fine grained: onboarding writes
 * [setOnboardingStep] after every step so the wizard resumes after an app kill.
 */
class SettingsRepository(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<Settings> = context.settingsDataStore.data
        .catch { throwable ->
            if (throwable is IOException) emit(emptyPreferences()) else throw throwable
        }
        .map { preferences -> preferences.toSettings() }

    val animationsEnabled: Flow<Boolean> = settings.map { it.animationsEnabled }

    suspend fun current(): Settings = settings.first()

    /** Single write path: read-modify-write inside one DataStore transaction. */
    suspend fun edit(transform: (Settings) -> Settings) {
        context.settingsDataStore.edit { preferences ->
            val current = preferences.toSettings()
            preferences.writeSettings(transform(current))
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) = edit { it.copy(onboardingCompleted = completed) }

    suspend fun setOnboardingStep(step: Int) =
        edit { it.copy(onboardingStep = step.coerceIn(1, Settings.ONBOARDING_STEPS)) }

    suspend fun setReminderTime(time: String) = edit { it.copy(reminderTime = normalizeTime(time)) }

    suspend fun setReminderDays(days: Set<Int>) =
        edit { it.copy(reminderDays = days.filter { day -> day in 1..7 }.toSet()) }

    suspend fun setMusicPlaylistUris(uris: List<String>) = edit { it.copy(musicPlaylistUris = uris.distinct()) }

    suspend fun setPlanPhase(phase: Int) = edit { it.copy(planPhase = phase.coerceAtLeast(1)) }

    suspend fun setPlanPhaseStartedAt(epochMillis: Long) = edit { it.copy(planPhaseStartedAt = epochMillis) }

    suspend fun setCycleStartedAt(epochMillis: Long) = edit { it.copy(cycleStartedAt = epochMillis) }

    suspend fun setAnimationsEnabled(enabled: Boolean) = edit { it.copy(animationsEnabled = enabled) }

    suspend fun setSfxEnabled(enabled: Boolean) = edit { it.copy(sfxEnabled = enabled) }

    suspend fun setMusicWithWorkout(enabled: Boolean) =
        edit { it.copy(musicWithWorkout = enabled) }

    suspend fun setAutoBackupEnabled(enabled: Boolean) =
        edit { it.copy(autoBackupEnabled = enabled) }

    suspend fun setAutoBackupTreeUri(uri: String?) = edit { it.copy(autoBackupTreeUri = uri) }

    suspend fun setLastAutoBackupAt(epochMillis: Long) =
        edit { it.copy(lastAutoBackupAt = epochMillis) }

    /** Null clears the message; an empty string is stored as null too. */
    suspend fun setFutureMessage(message: String?) =
        edit { it.copy(futureMessage = message?.trim()?.takeIf(String::isNotEmpty)) }

    suspend fun setFutureMessageRevealed(revealed: Boolean) =
        edit { it.copy(futureMessageRevealed = revealed) }

    suspend fun setLastProgressionReviewWeek(weekStartIso: String?) =
        edit { it.copy(lastProgressionReviewWeek = weekStartIso) }

    suspend fun setLastTrophyWeek(weekStartIso: String?) =
        edit { it.copy(lastTrophyWeek = weekStartIso) }

    suspend fun setLastReminderDay(isoDate: String?) =
        edit { it.copy(lastReminderDay = isoDate) }

    /**
     * Resets everything except the backup schema version. Used by "re-run onboarding".
     * Training history, the plan and the streak stay in place unless the caller resets
     * them explicitly, so a new cycle never silently deletes progress.
     */
    suspend fun resetForNewOnboarding() = edit {
        it.copy(
            onboardingCompleted = false,
            onboardingStep = 1,
            planPhase = 1,
            planPhaseStartedAt = 0L,
            futureMessageRevealed = false,
            lastProgressionReviewWeek = null,
        )
    }

    private fun normalizeTime(raw: String): String {
        val parts = raw.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return Settings.DEFAULT_REMINDER_TIME
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return Settings.DEFAULT_REMINDER_TIME
        if (hour !in 0..23 || minute !in 0..59) return Settings.DEFAULT_REMINDER_TIME
        return "%02d:%02d".format(hour, minute)
    }

    private fun Preferences.toSettings(): Settings = Settings(
        onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: false,
        onboardingStep = (this[Keys.ONBOARDING_STEP] ?: 1)
            .coerceIn(1, Settings.ONBOARDING_STEPS),
        reminderTime = this[Keys.REMINDER_TIME] ?: Settings.DEFAULT_REMINDER_TIME,
        reminderDays = this[Keys.REMINDER_DAYS]
            ?.mapNotNull { it.toIntOrNull() }
            ?.filter { it in 1..7 }
            ?.toSet()
            ?: emptySet(),
        musicPlaylistUris = this[Keys.MUSIC_PLAYLIST_URIS]
            ?.let { raw -> runCatching { json.decodeFromString<List<String>>(raw) }.getOrNull() }
            ?: emptyList(),
        planPhase = (this[Keys.PLAN_PHASE] ?: 1).coerceAtLeast(1),
        planPhaseStartedAt = this[Keys.PLAN_PHASE_STARTED_AT] ?: 0L,
        cycleStartedAt = this[Keys.CYCLE_STARTED_AT] ?: 0L,
        cycleIsExplicit = this[Keys.CYCLE_IS_EXPLICIT] ?: false,
        animationsEnabled = this[Keys.ANIMATIONS_ENABLED] ?: true,
        sfxEnabled = this[Keys.SFX_ENABLED] ?: true,
        musicWithWorkout = this[Keys.MUSIC_WITH_WORKOUT] ?: true,
        autoBackupEnabled = this[Keys.AUTO_BACKUP_ENABLED] ?: false,
        autoBackupTreeUri = this[Keys.AUTO_BACKUP_TREE],
        lastAutoBackupAt = this[Keys.LAST_AUTO_BACKUP_AT] ?: 0L,
        futureMessage = this[Keys.FUTURE_MESSAGE]?.takeIf { it.isNotBlank() },
        futureMessageRevealed = this[Keys.FUTURE_MESSAGE_REVEALED] ?: false,
        lastProgressionReviewWeek = this[Keys.LAST_PROGRESSION_REVIEW_WEEK],
        lastTrophyWeek = this[Keys.LAST_TROPHY_WEEK],
        lastReminderDay = this[Keys.LAST_REMINDER_DAY],
        backupSchemaVersion = this[Keys.BACKUP_SCHEMA_VERSION] ?: Settings.BACKUP_SCHEMA_VERSION,
    )

    private fun MutablePreferences.writeSettings(settings: Settings) {
        this[Keys.ONBOARDING_COMPLETED] = settings.onboardingCompleted
        this[Keys.ONBOARDING_STEP] = settings.onboardingStep.coerceIn(1, Settings.ONBOARDING_STEPS)
        this[Keys.REMINDER_TIME] = settings.reminderTime
        this[Keys.REMINDER_DAYS] = settings.reminderDays.map(Int::toString).toSet()
        this[Keys.MUSIC_PLAYLIST_URIS] = json.encodeToString(settings.musicPlaylistUris)
        this[Keys.PLAN_PHASE] = settings.planPhase
        this[Keys.PLAN_PHASE_STARTED_AT] = settings.planPhaseStartedAt
        this[Keys.CYCLE_STARTED_AT] = settings.cycleStartedAt
        this[Keys.CYCLE_IS_EXPLICIT] = settings.cycleIsExplicit
        this[Keys.ANIMATIONS_ENABLED] = settings.animationsEnabled
        this[Keys.SFX_ENABLED] = settings.sfxEnabled
        this[Keys.MUSIC_WITH_WORKOUT] = settings.musicWithWorkout
        this[Keys.AUTO_BACKUP_ENABLED] = settings.autoBackupEnabled
        if (settings.autoBackupTreeUri == null) {
            remove(Keys.AUTO_BACKUP_TREE)
        } else {
            this[Keys.AUTO_BACKUP_TREE] = settings.autoBackupTreeUri
        }
        this[Keys.LAST_AUTO_BACKUP_AT] = settings.lastAutoBackupAt
        if (settings.futureMessage == null) {
            remove(Keys.FUTURE_MESSAGE)
        } else {
            this[Keys.FUTURE_MESSAGE] = settings.futureMessage
        }
        this[Keys.FUTURE_MESSAGE_REVEALED] = settings.futureMessageRevealed
        if (settings.lastProgressionReviewWeek == null) {
            remove(Keys.LAST_PROGRESSION_REVIEW_WEEK)
        } else {
            this[Keys.LAST_PROGRESSION_REVIEW_WEEK] = settings.lastProgressionReviewWeek
        }
        if (settings.lastTrophyWeek == null) {
            remove(Keys.LAST_TROPHY_WEEK)
        } else {
            this[Keys.LAST_TROPHY_WEEK] = settings.lastTrophyWeek
        }
        if (settings.lastReminderDay == null) {
            remove(Keys.LAST_REMINDER_DAY)
        } else {
            this[Keys.LAST_REMINDER_DAY] = settings.lastReminderDay
        }
        this[Keys.BACKUP_SCHEMA_VERSION] = settings.backupSchemaVersion
    }

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val ONBOARDING_STEP = intPreferencesKey("onboarding_step")
        val REMINDER_TIME = stringPreferencesKey("reminder_time")
        val REMINDER_DAYS = stringSetPreferencesKey("reminder_days")
        val MUSIC_PLAYLIST_URIS = stringPreferencesKey("music_playlist_uris")
        val PLAN_PHASE = intPreferencesKey("plan_phase")
        val PLAN_PHASE_STARTED_AT = longPreferencesKey("plan_phase_started_at")
        val CYCLE_STARTED_AT = longPreferencesKey("cycle_started_at")
        val CYCLE_IS_EXPLICIT = booleanPreferencesKey("cycle_is_explicit")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
        val SFX_ENABLED = booleanPreferencesKey("sfx_enabled")
        val MUSIC_WITH_WORKOUT = booleanPreferencesKey("music_with_workout")
        val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
        val AUTO_BACKUP_TREE = stringPreferencesKey("auto_backup_tree")
        val LAST_AUTO_BACKUP_AT = longPreferencesKey("last_auto_backup_at")
        val FUTURE_MESSAGE = stringPreferencesKey("future_message")
        val FUTURE_MESSAGE_REVEALED = booleanPreferencesKey("future_message_revealed")
        val LAST_PROGRESSION_REVIEW_WEEK = stringPreferencesKey("last_progression_review_week")
        val LAST_TROPHY_WEEK = stringPreferencesKey("last_trophy_week")
        val LAST_REMINDER_DAY = stringPreferencesKey("last_reminder_day")
        val BACKUP_SCHEMA_VERSION = intPreferencesKey("backup_schema_version")
    }
}
