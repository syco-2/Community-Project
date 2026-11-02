package com.sixmonths.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sixmonths.app.di.ServiceLocator

/**
 * Writes one dated archive into the folder the user picked. Reads the settings again on every
 * run, so a folder change or an opt-out needs no rescheduling.
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = ServiceLocator.settingsRepository.current()
        val tree = settings.autoBackupTreeUri
        if (!settings.autoBackupEnabled || tree.isNullOrBlank()) return Result.success()

        return ServiceLocator.backupRepository
            .exportToFolder(Uri.parse(tree), BackupRepository.ARCHIVE_NAME)
            .fold(
                onSuccess = {
                    ServiceLocator.settingsRepository
                        .setLastAutoBackupAt(System.currentTimeMillis())
                    Result.success()
                },
                onFailure = {
                    if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
                },
            )
    }

    private companion object {
        /** One retry per run; a missing folder grant should not spin forever. */
        const val MAX_ATTEMPTS = 2
    }
}
