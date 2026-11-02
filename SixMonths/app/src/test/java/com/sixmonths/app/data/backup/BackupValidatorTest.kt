package com.sixmonths.app.data.backup

import com.sixmonths.app.data.prefs.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupValidatorTest {

    private fun validBackup(): BackupFile = BackupFile(
        backupSchemaVersion = 1,
        exportedAt = 1_700_000_000_000L,
        exercises = listOf(
            BackupExercise(
                id = "ex1",
                name = "Push-up",
                category = "PUSH",
                unit = "REPS",
                createdAt = 1L,
            ),
        ),
        planDays = listOf(
            BackupPlanDay(id = "day1", dayOfWeek = 1, state = "TRAINING"),
        ),
        planExercises = listOf(
            BackupPlanExercise(
                id = "pe1",
                planDayId = "day1",
                exerciseId = "ex1",
                label = "Training 1",
                sets = 3,
                reps = 8,
                restSeconds = 90,
                sortOrder = 0,
            ),
        ),
        sessions = listOf(
            BackupSession(
                id = "s1",
                planDayId = "day1",
                startedAt = 1L,
                finishedAt = 2L,
                status = "COMPLETED",
                durationSeconds = 1,
            ),
        ),
        setLogs = listOf(
            BackupSetLog(
                id = "log1",
                sessionId = "s1",
                exerciseId = "ex1",
                setIndex = 0,
                repsCompleted = 8,
                completedAt = 2L,
            ),
        ),
        streakState = BackupStreakState(
            currentStreak = 1,
            longestStreak = 1,
            freezeTokens = 2,
            totalCompletedSessions = 1,
        ),
        settings = BackupSettings(
            onboardingCompleted = true,
            onboardingStep = 6,
            reminderTime = "19:00",
            reminderDays = setOf(1),
            musicPlaylistUris = emptyList(),
            planPhase = 1,
            planPhaseStartedAt = 0L,
            animationsEnabled = true,
            backupSchemaVersion = 1,
        ),
    )

    @Test
    fun `valid backup has no errors`() {
        assertEquals(emptyList<String>(), BackupValidator.errors(validBackup()))
    }

    @Test
    fun `unknown schema version is rejected with a clear message`() {
        val errors = BackupValidator.errors(validBackup().copy(backupSchemaVersion = 99))
        assertTrue(errors.first().contains("Unsupported backup schema version 99"))
    }

    @Test
    fun `unknown category is rejected`() {
        val backup = validBackup().let { file ->
            file.copy(exercises = file.exercises.map { it.copy(category = "CARDIO") })
        }
        assertTrue(BackupValidator.errors(backup).any { it.contains("unknown category") })
    }

    @Test
    fun `duplicate weekday is rejected`() {
        val backup = validBackup().let { file ->
            file.copy(
                planDays = file.planDays + BackupPlanDay(
                    id = "day2",
                    dayOfWeek = 1,
                    state = "REST",
                ),
            )
        }
        assertTrue(BackupValidator.errors(backup).any { it.contains("share a weekday") })
    }

    @Test
    fun `missing foreign key is rejected`() {
        val backup = validBackup().let { file ->
            file.copy(
                planExercises = file.planExercises.map { it.copy(exerciseId = "missing") },
            )
        }
        assertTrue(BackupValidator.errors(backup).any { it.contains("missing exercise") })
    }

    @Test
    fun `orphan set log is rejected`() {
        val backup = validBackup().let { file ->
            file.copy(setLogs = file.setLogs.map { it.copy(sessionId = "missing") })
        }
        assertTrue(BackupValidator.errors(backup).any { it.contains("missing session") })
    }

    @Test
    fun `negative streak values are rejected`() {
        val backup = validBackup().let { file ->
            file.copy(
                streakState = file.streakState?.copy(freezeTokens = -1),
            )
        }
        assertTrue(BackupValidator.errors(backup).any { it.contains("negative") })
    }

    @Test
    fun `invalid reminder day is rejected`() {
        val backup = validBackup().let { file ->
            file.copy(settings = file.settings.copy(reminderDays = setOf(9)))
        }
        assertTrue(BackupValidator.errors(backup).any { it.contains("Reminder days") })
    }

    @Test
    fun `invalid onboarding step is rejected`() {
        val backup = validBackup().let { file ->
            file.copy(settings = file.settings.copy(onboardingStep = 0))
        }
        assertTrue(BackupValidator.errors(backup).any { it.contains("Onboarding step") })
    }

    @Test
    fun `schema 2 json without speciesId still decodes and validates`() {
        val payload = """
            {
              "backupSchemaVersion": 2,
              "exportedAt": 1700000000000,
              "monsters": [
                {
                  "id": "m1",
                  "name": "Old Capture",
                  "type": "#2F80ED",
                  "imageUri": "file:///data/user/0/com.sixmonths.app/files/monsters/m1.jpg",
                  "capturedAt": 1700000000000
                }
              ]
            }
        """.trimIndent()

        val file = Json { ignoreUnknownKeys = true }.decodeFromString<BackupFile>(payload)
        assertEquals(2, file.backupSchemaVersion)
        assertEquals("", file.monsters.single().speciesId)
        assertEquals(emptyList<String>(), BackupValidator.errors(file))
    }

    @Test
    fun `species id survives an export round trip`() {
        val backup = validBackup().copy(
            backupSchemaVersion = 3,
            monsters = listOf(
                BackupMonster(
                    id = "m1",
                    name = "Dusk Wraith",
                    type = "#2F80ED",
                    imageUri = "file:///data/user/0/com.sixmonths.app/files/monsters/m1.jpg",
                    capturedAt = 5L,
                    speciesId = "m001",
                ),
            ),
        )
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val restored = json.decodeFromString<BackupFile>(json.encodeToString(backup))
        assertEquals("m001", restored.monsters.single().speciesId)
        assertEquals(3, restored.backupSchemaVersion)
        assertEquals(emptyList<String>(), BackupValidator.errors(restored))
    }

    @Test
    fun `plan exercise id in set logs survives an export round trip`() {
        val backup = validBackup().copy(
            backupSchemaVersion = Settings.BACKUP_SCHEMA_VERSION,
            setLogs = listOf(
                BackupSetLog(
                    id = "s1",
                    sessionId = "session-1",
                    exerciseId = "exercise-1",
                    planExerciseId = "plan-exercise-7",
                    setIndex = 0,
                    repsCompleted = 8,
                    completedAt = 10L,
                ),
            ),
        )
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val restored = json.decodeFromString<BackupFile>(json.encodeToString(backup))
        assertEquals("plan-exercise-7", restored.setLogs.single().planExerciseId)
    }

    @Test
    fun `a backup from a newer build is rejected`() {
        val newer = Settings.BACKUP_SCHEMA_VERSION + 1
        val backup = validBackup().copy(backupSchemaVersion = newer)
        assertTrue(
            BackupValidator.errors(backup).first()
                .contains("Unsupported backup schema version $newer"),
        )
    }
}
