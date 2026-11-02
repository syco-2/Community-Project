package com.sixmonths.app.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/** ACTION_OPEN_DOCUMENT for audio, with persistable read permission per file. */
@Composable
fun rememberAudioPicker(onPicked: (List<Uri>) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
            }
            currentOnPicked(uris)
        }
    }
    return { launcher.launch(arrayOf("audio/*")) }
}

/** ACTION_CREATE_DOCUMENT for the backup archive (backup.json, photos and songs). */
@Composable
fun rememberBackupExportPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) currentOnPicked(uri)
    }
    return { launcher.launch("six-months-backup.zip") }
}

/** ACTION_OPEN_DOCUMENT for the backup import; accepts the archive and old JSON files. */
@Composable
fun rememberBackupImportPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) currentOnPicked(uri)
    }
    return {
        launcher.launch(arrayOf("application/zip", "application/json", "text/plain", "*/*"))
    }
}

/** ACTION_OPEN_DOCUMENT_TREE for the weekly automatic backup folder. */
@Composable
fun rememberBackupFolderPicker(onPicked: (Uri) -> Unit): () -> Unit {
    val context = LocalContext.current
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            currentOnPicked(uri)
        }
    }
    return { launcher.launch(null) }
}

fun Uri.displayFileName(context: Context): String {
    val fromProvider = runCatching {
        context.contentResolver.query(
            this,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()
    return fromProvider
        ?: lastPathSegment?.substringAfterLast('/')
        ?: toString()
}
