package com.sixmonths.app.ui.screens.settings

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.data.backup.AutoBackupScheduler
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.ui.components.AppIconButton
import com.sixmonths.app.ui.components.AppTextButton
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.theme.sixMonthsColors
import com.sixmonths.app.ui.util.rememberBackupExportPicker
import com.sixmonths.app.ui.util.rememberBackupFolderPicker
import com.sixmonths.app.ui.util.rememberBackupImportPicker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings: the backup (manual archive plus an optional weekly automatic one) and the two
 * switches that change how a workout feels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)

    var pendingImport by remember { mutableStateOf<Uri?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

    var showListDialog by remember { mutableStateOf(false) }
    var listText by remember { mutableStateOf("") }
    var listBusy by remember { mutableStateOf(false) }
    var listMessage by remember { mutableStateOf<String?>(null) }

    val exportPicker = rememberBackupExportPicker { uri ->
        scope.launch {
            backupBusy = true
            backupMessage = null
            val result = ServiceLocator.backupRepository.export(uri)
            backupMessage = result.fold(
                onSuccess = { bytes -> "Backup written ($bytes bytes)." },
                onFailure = { error -> "Export failed: ${error.message}" },
            )
            backupBusy = false
        }
    }

    val importPicker = rememberBackupImportPicker { uri -> pendingImport = uri }

    val folderPicker = rememberBackupFolderPicker { uri ->
        scope.launch {
            ServiceLocator.settingsRepository.setAutoBackupTreeUri(uri.toString())
            ServiceLocator.settingsRepository.setAutoBackupEnabled(true)
            AutoBackupScheduler.sync(
                context,
                ServiceLocator.settingsRepository.current(),
            )
            backupMessage = "Weekly backup into ${folderLabel(uri)}."
        }
    }

    val autoBackup = settings?.autoBackupEnabled == true
    val folderUri = settings?.autoBackupTreeUri

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SixMonthsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Music during workout",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = settings?.musicWithWorkout ?: true,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                ServiceLocator.settingsRepository.setMusicWithWorkout(enabled)
                            }
                        },
                    )
                }
            }

            SixMonthsCard {
                Text(
                    text = "Backup",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTextButton(
                        enabled = !backupBusy,
                        onClick = { exportPicker() },
                    ) {
                        Text("Export")
                    }
                    AppTextButton(
                        enabled = !backupBusy,
                        onClick = { importPicker() },
                    ) {
                        Text("Import")
                    }
                }
                backupMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.sixMonthsColors.textSecondary,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Weekly automatic backup",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = autoBackup,
                        onCheckedChange = { enabled ->
                            if (enabled && folderUri == null) {
                                folderPicker()
                            } else {
                                scope.launch {
                                    ServiceLocator.settingsRepository
                                        .setAutoBackupEnabled(enabled)
                                    AutoBackupScheduler.sync(
                                        context,
                                        ServiceLocator.settingsRepository.current(),
                                    )
                                }
                            }
                        },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppTextButton(onClick = { folderPicker() }) {
                        Text(if (folderUri == null) "Choose folder" else "Change folder")
                    }
                    Text(
                        text = folderUri?.let { folderLabel(Uri.parse(it)) } ?: "No folder",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.sixMonthsColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                val lastBackup = settings?.lastAutoBackupAt ?: 0L
                if (lastBackup > 0L) {
                    Text(
                        text = "Last backup ${formatDate(lastBackup)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.sixMonthsColors.textSecondary,
                    )
                }
            }

            SixMonthsCard {
                AppTextButton(onClick = { showListDialog = true }) {
                    Text("New list")
                }
                listMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.sixMonthsColors.textSecondary,
                    )
                }
            }
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace all data?") },
            confirmButton = {
                AppTextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        backupBusy = true
                        backupMessage = null
                        val result = ServiceLocator.backupRepository.import(uri)
                        backupMessage = result.fold(
                            onSuccess = { "Import complete." },
                            onFailure = { error -> "Import failed: ${error.message}" },
                        )
                        backupBusy = false
                    }
                }) { Text("Import") }
            },
            dismissButton = {
                AppTextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            },
        )
    }

    if (showListDialog) {
        AlertDialog(
            onDismissRequest = { if (!listBusy) showListDialog = false },
            title = { Text("New list") },
            text = {
                OutlinedTextField(
                    value = listText,
                    onValueChange = { listText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    enabled = !listBusy,
                )
            },
            confirmButton = {
                AppTextButton(
                    enabled = listText.isNotBlank() && !listBusy,
                    onClick = {
                        listBusy = true
                        scope.launch {
                            val result = runCatching {
                                ServiceLocator.monsterCatalogRepository.importList(listText)
                            }
                            listBusy = false
                            result.onSuccess { imported ->
                                listMessage = buildString {
                                    append("+${imported.added}")
                                    if (imported.newSections.isNotEmpty()) {
                                        append(" · ")
                                        append(imported.newSections.joinToString(", "))
                                    }
                                }
                                listText = ""
                                showListDialog = false
                            }.onFailure { error ->
                                listMessage = error.message ?: "Could not read the list."
                            }
                        }
                    },
                ) { Text(if (listBusy) "…" else "Add") }
            },
            dismissButton = {
                AppTextButton(onClick = { showListDialog = false }, enabled = !listBusy) {
                    Text("Cancel")
                }
            },
        )
    }
}

/** SAF tree ids look like "primary:Download/Six Months"; the tail is what a user knows. */
private fun folderLabel(uri: Uri): String {
    val raw = uri.lastPathSegment ?: return "Folder"
    return raw.substringAfterLast(':').ifBlank { raw }.substringAfterLast('/')
}

private fun formatDate(epochMillis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(epochMillis))
