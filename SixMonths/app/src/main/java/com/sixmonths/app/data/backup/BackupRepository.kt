package com.sixmonths.app.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.sixmonths.app.core.model.SessionStatus
import com.sixmonths.app.data.AppDatabase
import com.sixmonths.app.data.dao.ExerciseDao
import com.sixmonths.app.data.dao.MonsterDao
import com.sixmonths.app.data.dao.PlanDayDao
import com.sixmonths.app.data.dao.PlanExerciseDao
import com.sixmonths.app.data.dao.SessionDao
import com.sixmonths.app.data.dao.SetLogDao
import com.sixmonths.app.data.dao.StreakDao
import com.sixmonths.app.data.entity.Exercise
import com.sixmonths.app.data.entity.Monster
import com.sixmonths.app.data.entity.PlanDay
import com.sixmonths.app.data.entity.PlanExercise
import com.sixmonths.app.data.entity.Session
import com.sixmonths.app.data.entity.SetLog
import com.sixmonths.app.data.entity.StreakState
import com.sixmonths.app.data.prefs.Settings
import com.sixmonths.app.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupException(message: String) : Exception(message)

/**
 * Backup as a ZIP archive: `backup.json` plus every can photo under `images/` and every
 * playlist song under `music/`.
 *
 * Import validates the version and every reference before it writes a single row, then
 * replaces all tables in one Room transaction and only afterwards rewrites the settings.
 * Files are staged first, so a broken archive never leaves a half-restored app behind.
 * Plain JSON exports from older builds still import; they just carry no media.
 */
class BackupRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val exerciseDao: ExerciseDao,
    private val planDayDao: PlanDayDao,
    private val planExerciseDao: PlanExerciseDao,
    private val sessionDao: SessionDao,
    private val setLogDao: SetLogDao,
    private val streakDao: StreakDao,
    private val monsterDao: MonsterDao,
    private val settingsRepository: SettingsRepository,
) {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun export(uri: Uri): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val target = context.contentResolver.openOutputStream(uri)
                ?: throw BackupException("Could not open the selected file for writing.")
            writeArchive(target)
        }
    }

    /**
     * Weekly automatic backup: one archive inside the folder the user picked. The previous
     * archive is deleted first, so the folder holds the current songs and photos once instead
     * of one full copy per week.
     */
    suspend fun exportToFolder(treeUri: Uri, fileName: String): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                deleteExistingArchives(treeUri)
                val document = DocumentsContract.createDocument(
                    context.contentResolver,
                    treeUri,
                    "application/zip",
                    fileName,
                ) ?: throw BackupException("Could not create the backup in the chosen folder.")
                val target = context.contentResolver.openOutputStream(document)
                    ?: throw BackupException("Could not open the backup file for writing.")
                writeArchive(target)
            }
        }

    /** Drops every earlier archive in the folder; only the newest one keeps its media. */
    private fun deleteExistingArchives(treeUri: Uri) {
        val treeDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        runCatching {
            context.contentResolver.query(children, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1) ?: continue
                    if (!name.startsWith(ARCHIVE_PREFIX)) continue
                    val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                        treeUri,
                        cursor.getString(0),
                    )
                    runCatching {
                        DocumentsContract.deleteDocument(context.contentResolver, documentUri)
                    }
                }
            }
        }
    }

    private suspend fun writeArchive(target: OutputStream): Long =
        withContext(Dispatchers.IO) {
            runCatching {
            val settings = settingsRepository.current()
            val monsters = monsterDao.getAll()
            val media = mutableListOf<BackupMediaFile>()

            val counter = CountingOutputStream(BufferedOutputStream(target))
            val zip = ZipOutputStream(counter)
            try {
                // Songs first: the manifest lists exactly the files that made it into the zip.
                settings.musicPlaylistUris.forEachIndexed { index, rawUri ->
                    val trackUri = Uri.parse(rawUri)
                    val entry = "music/${index}_${safeFileName(displayName(trackUri))}"
                    val source = runCatching {
                        context.contentResolver.openInputStream(trackUri)
                    }.getOrNull() ?: return@forEachIndexed
                    zip.putNextEntry(ZipEntry(entry))
                    try {
                        source.use { it.copyTo(zip) }
                    } finally {
                        zip.closeEntry()
                    }
                    media += BackupMediaFile(entry = entry, originalUri = rawUri)
                }

                // Can photos: matched by file name on import, no manifest needed.
                monsters.forEach { monster ->
                    val file = monster.imageUri.toFileOrNull() ?: return@forEach
                    if (!file.isFile) return@forEach
                    zip.putNextEntry(ZipEntry("images/${file.name}"))
                    try {
                        file.inputStream().use { it.copyTo(zip) }
                    } finally {
                        zip.closeEntry()
                    }
                }

                val backup = BackupFile(
                    backupSchemaVersion = Settings.BACKUP_SCHEMA_VERSION,
                    exportedAt = System.currentTimeMillis(),
                    exercises = exerciseDao.getAll().map { it.toBackup() },
                    planDays = planDayDao.getAll().map { it.toBackup() },
                    planExercises = planExerciseDao.getAll().map { it.toBackup() },
                    sessions = (
                        sessionDao.getAllWithStatus(SessionStatus.ACTIVE) +
                            sessionDao.getAllWithStatus(SessionStatus.COMPLETED) +
                            sessionDao.getAllWithStatus(SessionStatus.ABANDONED)
                        ).map { it.toBackup() }
                        .sortedBy { it.startedAt },
                    setLogs = emptyList(), // filled below
                    monsters = monsters.map { it.toBackup() },
                    streakState = streakDao.getState()?.toBackup(),
                    settings = settings.toBackup(),
                    media = media,
                ).let { base -> base.copy(setLogs = collectSetLogs(base.sessions)) }

                zip.putNextEntry(ZipEntry(JSON_ENTRY))
                zip.write(json.encodeToString(backup).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            } finally {
                runCatching { zip.close() }
            }
            counter.count
            }
        }.getOrThrow()

    private suspend fun collectSetLogs(sessions: List<BackupSession>): List<BackupSetLog> =
        sessions.flatMap { session ->
            setLogDao.listForSession(session.id).map { it.toBackup() }
        }

    suspend fun import(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val stream = context.contentResolver.openInputStream(uri)
                ?: throw BackupException("Could not read the selected file.")
            PushbackInputStream(BufferedInputStream(stream), 2).use { input ->
                val head = ByteArray(2)
                val read = input.read(head)
                if (read > 0) input.unread(head, 0, read)
                val isArchive = read == 2 &&
                    head[0] == 'P'.code.toByte() &&
                    head[1] == 'K'.code.toByte()
                if (isArchive) importArchive(input) else importJson(input)
            }
        }
    }

    /** Older builds wrote bare JSON; keep those files importable. */
    private suspend fun importJson(input: InputStream) {
        val text = input.readBytes().toString(Charsets.UTF_8)
        val backup = decode(text)
        requireValid(backup)
        apply(backup, emptyMap(), emptyMap(), staging = null)
    }

    /** One pass: read backup.json and stage every photo and song next to it. */
    private suspend fun importArchive(input: InputStream) {
        val staging = File(context.cacheDir, "restore").apply {
            deleteRecursively()
            mkdirs()
        }
        val stagedImages = mutableMapOf<String, File>()
        val stagedMusic = mutableMapOf<String, File>()
        var payload: String? = null

        try {
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = safeEntryName(entry.name)
                    if (name != null) {
                        when {
                            name == JSON_ENTRY -> payload = zip.readEntryText().toString(Charsets.UTF_8)
                            name.startsWith(IMAGE_PREFIX) -> {
                                val base = File(name).name
                                if (base.isNotEmpty()) {
                                    stagedImages[base] = zip.stageInto(File(staging, "images/$base"))
                                }
                            }
                            name.startsWith(MUSIC_PREFIX) -> {
                                val base = File(name).name
                                if (base.isNotEmpty()) {
                                    stagedMusic[name] = zip.stageInto(File(staging, "music/$base"))
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val backup = decode(
                payload ?: throw BackupException("The backup file has no $JSON_ENTRY."),
            )
            requireValid(backup)
            apply(backup, stagedImages, stagedMusic, staging)
        } finally {
            runCatching { staging.deleteRecursively() }
        }
    }

    private fun decode(text: String): BackupFile = try {
        json.decodeFromString<BackupFile>(text)
    } catch (error: Exception) {
        throw BackupException("Not a valid Six Months backup: ${error.message}")
    }

    private fun requireValid(backup: BackupFile) {
        val problems = BackupValidator.errors(backup)
        if (problems.isNotEmpty()) throw BackupException(problems.first())
    }

    /** Writes the staged files into place, then replaces database rows and settings. */
    private suspend fun apply(
        backup: BackupFile,
        stagedImages: Map<String, File>,
        stagedMusic: Map<String, File>,
        staging: File?,
    ) {
        val cansDirectory = File(context.filesDir, CANS_DIR).apply { mkdirs() }
        val musicDirectory = File(context.filesDir, MUSIC_DIR).apply { mkdirs() }

        val imageUris = stagedImages.mapValues { (name, staged) ->
            val target = File(cansDirectory, name)
            staged.copyTo(target, overwrite = true)
            Uri.fromFile(target).toString()
        }

        val entryUris = stagedMusic.mapValues { (_, staged) ->
            val target = File(musicDirectory, staged.name)
            staged.copyTo(target, overwrite = true)
            Uri.fromFile(target).toString()
        }

        val monsters = backup.monsters.map { monster ->
            val name = monster.imageUri.substringAfterLast('/')
            monster.copy(imageUri = imageUris[name] ?: monster.imageUri).toEntity()
        }

        val playlist = backup.settings.musicPlaylistUris.map { raw ->
            val entry = backup.media.firstOrNull { it.originalUri == raw }?.entry
            entry?.let { entryUris[it] } ?: raw
        }

        database.withTransaction {
            setLogDao.deleteAll()
            sessionDao.deleteAll()
            planExerciseDao.deleteAllRows()
            planDayDao.deleteAll()
            exerciseDao.deleteAll()
            streakDao.deleteAll()
            monsterDao.deleteAll()

            exerciseDao.upsertAll(backup.exercises.map { it.toEntity() })
            planDayDao.upsertAll(backup.planDays.map { it.toEntity() })
            planExerciseDao.upsertAll(backup.planExercises.map { it.toEntity() })
            sessionDao.insertAll(backup.sessions.map { it.toEntity() })
            setLogDao.insertAll(backup.setLogs.map { it.toEntity() })
            monsterDao.insertAll(monsters)
            backup.streakState?.let { streakDao.upsert(it.toEntity()) }
        }

        val currentSettings = settingsRepository.current()
        backup.settings.let { imported ->
            settingsRepository.edit {
                Settings(
                    onboardingCompleted = imported.onboardingCompleted,
                    onboardingStep = imported.onboardingStep,
                    reminderTime = imported.reminderTime,
                    reminderDays = imported.reminderDays,
                    musicPlaylistUris = playlist,
                    planPhase = imported.planPhase,
                    planPhaseStartedAt = imported.planPhaseStartedAt,
                    cycleStartedAt = imported.cycleStartedAt,
                    cycleIsExplicit = imported.cycleIsExplicit,
                    animationsEnabled = imported.animationsEnabled,
                    sfxEnabled = imported.sfxEnabled,
                    musicWithWorkout = imported.musicWithWorkout,
                    autoBackupEnabled = imported.autoBackupEnabled,
                    autoBackupTreeUri = currentSettings.autoBackupTreeUri,
                    lastAutoBackupAt = currentSettings.lastAutoBackupAt,
                    futureMessage = imported.futureMessage,
                    futureMessageRevealed = imported.futureMessageRevealed,
                    lastProgressionReviewWeek = imported.lastProgressionReviewWeek,
                    backupSchemaVersion = Settings.BACKUP_SCHEMA_VERSION,
                )
            }
        }

        // Only sweep a folder the archive actually delivered files for; a legacy JSON must
        // never delete photos that are still referenced on this device.
        if (stagedImages.isNotEmpty()) {
            prune(cansDirectory, monsters.map { it.imageUri.substringAfterLast('/') }.toSet())
        }
        if (stagedMusic.isNotEmpty()) {
            prune(
                musicDirectory,
                playlist.map { Uri.parse(it).path?.substringAfterLast('/') ?: "" }.toSet(),
            )
        }
        staging?.deleteRecursively()
    }

    private fun prune(directory: File, keep: Set<String>) {
        directory.listFiles()?.forEach { file ->
            if (file.name !in keep) runCatching { file.delete() }
        }
    }

    private fun displayName(uri: Uri): String {
        val fromProvider = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()
        return fromProvider
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "track"
    }

    companion object {
        /** Name of the one archive the weekly backup keeps in the chosen folder. */
        const val ARCHIVE_NAME = "six-months-backup.zip"
        private const val ARCHIVE_PREFIX = "six-months-backup"
        const val JSON_ENTRY = "backup.json"
        const val IMAGE_PREFIX = "images/"
        const val MUSIC_PREFIX = "music/"
        const val CANS_DIR = "cans"
        const val MUSIC_DIR = "music"
    }
}

// ----- archive helpers -------------------------------------------------------

private fun safeFileName(raw: String): String {
    val cleaned = raw.map { character ->
        if (character.isLetterOrDigit() || character == '.' || character == '-' ||
            character == '_' || character == ' '
        ) {
            character
        } else {
            '_'
        }
    }.joinToString("")
    return cleaned.take(96).ifBlank { "track" }
}

/** Entry names from an archive are untrusted: no absolute paths, no traversal. */
private fun safeEntryName(name: String): String? {
    if (name.isBlank() || name.startsWith("/") || name.contains("..")) return null
    return name
}

private fun ZipInputStream.stageInto(target: File): File {
    target.parentFile?.mkdirs()
    FileOutputStream(target).use { out -> copyTo(out) }
    return target
}

private fun InputStream.readEntryText(): ByteArray {
    val buffer = ByteArrayOutputStream()
    val chunk = ByteArray(8 * 1024)
    while (true) {
        val read = read(chunk)
        if (read < 0) break
        buffer.write(chunk, 0, read)
        if (buffer.size() > MAX_JSON_BYTES) {
            throw BackupException("The backup file is too large.")
        }
    }
    return buffer.toByteArray()
}

private const val MAX_JSON_BYTES = 16 * 1024 * 1024

private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    var count: Long = 0L
        private set

    override fun write(b: Int) {
        out.write(b)
        count += 1
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        out.write(b, off, len)
        count += len
    }
}

private fun String.toFileOrNull(): File? = runCatching {
    val uri = Uri.parse(this)
    when (uri.scheme) {
        "file", null -> uri.path?.let(::File)
        else -> null
    }
}.getOrNull()

// ----- mapping ---------------------------------------------------------------

private fun Exercise.toBackup() = BackupExercise(
    id = id,
    name = name,
    category = category,
    unit = unit,
    notes = notes,
    formTip = formTip,
    isOptional = isOptional,
    createdAt = createdAt,
)

private fun BackupExercise.toEntity() = Exercise(
    id = id,
    name = name,
    category = category,
    unit = unit,
    notes = notes,
    formTip = formTip,
    isOptional = isOptional,
    createdAt = createdAt,
)

private fun PlanDay.toBackup() = BackupPlanDay(
    id = id,
    dayOfWeek = dayOfWeek,
    state = state,
    name = name,
)

private fun BackupPlanDay.toEntity() = PlanDay(
    id = id,
    dayOfWeek = dayOfWeek,
    state = state,
    name = name,
)

private fun PlanExercise.toBackup() = BackupPlanExercise(
    id = id,
    planDayId = planDayId,
    exerciseId = exerciseId,
    label = label,
    sets = sets,
    reps = reps,
    restSeconds = restSeconds,
    sortOrder = sortOrder,
)

private fun BackupPlanExercise.toEntity() = PlanExercise(
    id = id,
    planDayId = planDayId,
    exerciseId = exerciseId,
    label = label,
    sets = sets,
    reps = reps,
    restSeconds = restSeconds,
    sortOrder = sortOrder,
)

private fun Session.toBackup() = BackupSession(
    id = id,
    planDayId = planDayId,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = status,
    durationSeconds = durationSeconds,
)

private fun BackupSession.toEntity() = Session(
    id = id,
    planDayId = planDayId,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = status,
    durationSeconds = durationSeconds,
)

private fun SetLog.toBackup() = BackupSetLog(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    planExerciseId = planExerciseId,
    setIndex = setIndex,
    repsCompleted = repsCompleted,
    completedAt = completedAt,
)

private fun BackupSetLog.toEntity() = SetLog(
    id = id,
    sessionId = sessionId,
    exerciseId = exerciseId,
    planExerciseId = planExerciseId,
    setIndex = setIndex,
    repsCompleted = repsCompleted,
    completedAt = completedAt,
)

private fun StreakState.toBackup() = BackupStreakState(
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    lastCompletedDate = lastCompletedDate,
    freezeTokens = freezeTokens,
    totalCompletedSessions = totalCompletedSessions,
    lastPenaltyAt = lastPenaltyAt,
    lastEvaluatedDate = lastEvaluatedDate,
    penaltyPending = penaltyPending,
)

private fun Monster.toBackup() = BackupMonster(
    id = id,
    name = name,
    type = type,
    imageUri = imageUri,
    capturedAt = capturedAt,
    speciesId = flavorId,
)

private fun BackupMonster.toEntity() = Monster(
    id = id,
    name = name,
    type = type,
    imageUri = imageUri,
    capturedAt = capturedAt,
    flavorId = speciesId,
)

private fun BackupStreakState.toEntity() = StreakState(
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    lastCompletedDate = lastCompletedDate,
    freezeTokens = freezeTokens,
    totalCompletedSessions = totalCompletedSessions,
    lastPenaltyAt = lastPenaltyAt,
    lastEvaluatedDate = lastEvaluatedDate,
    penaltyPending = penaltyPending,
)

private fun Settings.toBackup() = BackupSettings(
    onboardingCompleted = onboardingCompleted,
    onboardingStep = onboardingStep,
    reminderTime = reminderTime,
    reminderDays = reminderDays,
    musicPlaylistUris = musicPlaylistUris,
    planPhase = planPhase,
    planPhaseStartedAt = planPhaseStartedAt,
    cycleStartedAt = cycleStartedAt,
    cycleIsExplicit = cycleIsExplicit,
    animationsEnabled = animationsEnabled,
    sfxEnabled = sfxEnabled,
    musicWithWorkout = musicWithWorkout,
    autoBackupEnabled = autoBackupEnabled,
    futureMessage = futureMessage,
    futureMessageRevealed = futureMessageRevealed,
    lastProgressionReviewWeek = lastProgressionReviewWeek,
    backupSchemaVersion = backupSchemaVersion,
)
