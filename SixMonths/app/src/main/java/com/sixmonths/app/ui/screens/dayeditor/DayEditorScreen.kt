package com.sixmonths.app.ui.screens.dayeditor
import com.sixmonths.app.ui.components.AppTextButton
import com.sixmonths.app.ui.components.AppIconButton

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.core.util.newId
import com.sixmonths.app.core.util.rememberHaptics
import com.sixmonths.app.data.prefs.Settings
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.domain.CategorySuggestions
import com.sixmonths.app.ui.components.CategoryChipRow
import com.sixmonths.app.ui.components.EmptyIllustration
import com.sixmonths.app.ui.components.EmptyState
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.SetsRepsBadge
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.components.SwipeToDeleteRow
import com.sixmonths.app.ui.components.TabBarScrollTrackerForColumn
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.NumberPickerDialog
import com.sixmonths.app.ui.components.NumberPickerField
import com.sixmonths.app.ui.theme.sixMonthsColors
import com.sixmonths.app.ui.util.viewModelFactory
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Day Editor: numbered trainings, SETS × REPS badges, drag-to-reorder, duplicate, delete
 * and the add/edit bottom sheet with the mandatory category chip picker.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun DayEditorScreen(
    dayOfWeek: Int,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val viewModel: DayEditorViewModel = viewModel(
        key = "day-editor-$dayOfWeek",
        factory = viewModelFactory { DayEditorViewModel(dayOfWeek) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)

    var showSheet by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<DayEditorViewModel.EditorEntry?>(null) }
    var showRename by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    TabBarScrollTrackerForColumn(scrollState = scrollState)

    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState("day-card-$dayOfWeek"),
                animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }

    Scaffold(
        modifier = sharedModifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.dayName ?: dayTitle(dayOfWeek),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = DayState.displayName(state.dayState) + " day",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.sixMonthsColors.textMuted,
                        )
                    }
                },
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    AppTextButton(onClick = { showRename = true }) {
                        Text(text = "Rename")
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
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = DateUtils.relativeLabel(DateUtils.nextOccurrence(dayOfWeek)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )

            if (state.loading) {
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.sixMonthsColors.textSecondary,
                )
            } else {
                if (state.entries.isEmpty()) {
                    EmptyDayCard()
                } else {
                    state.entries.forEachIndexed { index, entry ->
                        SwipeToDeleteRow(
                            onDelete = {
                                viewModel.deleteEntry(entry.planExerciseId)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Training deleted",
                                        actionLabel = "Undo",
                                        withDismissAction = false,
                                        duration = SnackbarDuration.Short,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        // The row is addressed by its original id, so undo
                                        // restores the entry in place instead of appending it.
                                        viewModel.saveEntry(
                                            originalId = entry.planExerciseId,
                                            entry = entry,
                                        )
                                    }
                                }
                            },
                        ) {
                            EntryRow(
                                entry = entry,
                                index = index,
                                entries = state.entries,
                                onMove = viewModel::moveEntry,
                                onEdit = {
                                    editingEntry = entry
                                    showSheet = true
                                },
                                onDuplicate = { viewModel.duplicateEntry(entry.planExerciseId) },
                            )
                        }
                    }

                }

                PrimaryAction(
                    text = "+ Add training",
                    onClick = {
                        editingEntry = null
                        showSheet = true
                    },
                )

                state.error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.sixMonthsColors.danger,
                    )
                }

            }
        }
    }

    if (showSheet) {
        EntrySheet(
            existing = editingEntry,
            entryCount = state.entries.size,
            suggestions = state.suggestions,
            onDismiss = { showSheet = false },
            onSave = { entry ->
                viewModel.saveEntry(originalId = editingEntry?.planExerciseId, entry = entry)
                showSheet = false
            },
        )
    }

    if (showRename) {
        RenameDayDialog(
            currentName = state.dayName.orEmpty(),
            onDismiss = { showRename = false },
            onConfirm = { name ->
                viewModel.renameDay(name)
                showRename = false
            },
        )
    }
}

@Composable
private fun EmptyDayCard() {
    SixMonthsCard {
        EmptyState(
            illustration = EmptyIllustration.DUMBBELL,
            title = "No exercises yet",
            message = null,
        )
    }
}

@Composable
private fun EntryRow(
    entry: DayEditorViewModel.EditorEntry,
    index: Int,
    entries: List<DayEditorViewModel.EditorEntry>,
    onMove: (Int, Int) -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val haptics = rememberHaptics()
    val latestEntries by rememberUpdatedState(entries)
    var accumulatedDrag by remember(entry.planExerciseId) { mutableFloatStateOf(0f) }
    var menuOpen by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val rowHeightPx = with(density) { 76.dp.toPx() }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .pointerInput(entry.planExerciseId) {
                        detectVerticalDragGestures(
                            onDragStart = { accumulatedDrag = 0f },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedDrag += dragAmount
                                val currentIndex = latestEntries.indexOfFirst {
                                    it.planExerciseId == entry.planExerciseId
                                }
                                if (currentIndex < 0) return@detectVerticalDragGestures
                                if (accumulatedDrag > rowHeightPx && currentIndex < latestEntries.lastIndex) {
                                    onMove(currentIndex, currentIndex + 1)
                                    accumulatedDrag -= rowHeightPx
                                    haptics.tick()
                                } else if (accumulatedDrag < -rowHeightPx && currentIndex > 0) {
                                    onMove(currentIndex, currentIndex - 1)
                                    accumulatedDrag += rowHeightPx
                                    haptics.tick()
                                }
                            },
                            onDragEnd = { accumulatedDrag = 0f },
                            onDragCancel = { accumulatedDrag = 0f },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "≡",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.sets} sets · ${ExerciseCategory.displayName(entry.category)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }

            SetsRepsBadge(sets = entry.sets, reps = entry.reps, unit = entry.unit)

            Box {
                AppTextButton(onClick = { menuOpen = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp)) {
                    Text(text = "⋯", style = MaterialTheme.typography.titleMedium)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        enabled = index > 0,
                        onClick = {
                            menuOpen = false
                            onMove(index, index - 1)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        enabled = index < entries.lastIndex,
                        onClick = {
                            menuOpen = false
                            onMove(index, index + 1)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = {
                            menuOpen = false
                            onDuplicate()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EntrySheet(
    existing: DayEditorViewModel.EditorEntry?,
    entryCount: Int,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (DayEditorViewModel.EditorEntry) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var label by remember {
        mutableStateOf(existing?.label ?: defaultTrainingLabel(entryCount))
    }
    var category by remember { mutableStateOf(existing?.category ?: ExerciseCategory.DEFAULT) }
    var unit by remember { mutableStateOf(existing?.unit ?: ExerciseUnit.REPS) }
    var sets by remember { mutableIntStateOf(existing?.sets ?: 3) }
    var reps by remember { mutableIntStateOf(existing?.reps ?: 8) }
    // Rest is no longer a product feature; the column is kept for backup compatibility.
    val restSeconds = existing?.restSeconds ?: 0
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var formTip by remember { mutableStateOf(existing?.formTip.orEmpty()) }
    var categoryManuallySet by remember { mutableStateOf(existing != null) }
    // Which number dialog is open: "sets" or "reps". One at a time.
    var openPicker by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(name) {
        if (categoryManuallySet) return@LaunchedEffect
        category = CategorySuggestions.suggest(name) ?: ExerciseCategory.DEFAULT
    }

    when (openPicker) {
        "sets" -> NumberPickerDialog(
            title = "Sets",
            value = sets,
            range = 1..10,
            onConfirm = {
                sets = it
                openPicker = null
            },
            onDismiss = { openPicker = null },
        )

        "reps" -> NumberPickerDialog(
            title = if (unit == ExerciseUnit.SECONDS) "Seconds" else "Reps",
            value = reps,
            range = if (unit == ExerciseUnit.SECONDS) 5..600 else 1..100,
            step = if (unit == ExerciseUnit.SECONDS) 5 else 1,
            unitLabel = if (unit == ExerciseUnit.SECONDS) "s" else "",
            onConfirm = {
                reps = it
                openPicker = null
            },
            onDismiss = { openPicker = null },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (existing == null) "Add training" else "Edit training",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Exercise name") },
                singleLine = true,
            )

            val matches = remember(name, suggestions) {
                if (name.isBlank()) {
                    emptyList()
                } else {
                    suggestions.filter { it.contains(name, ignoreCase = true) && !it.equals(name, true) }
                        .take(4)
                }
            }
            if (matches.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    matches.forEach { match ->
                        AssistChip(
                            onClick = { name = match },
                            label = { Text(text = match, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Label") },
                singleLine = true,
            )

            Text(
                text = "Category",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.sixMonthsColors.textSecondary,
            )
            CategoryChipRow(
                selected = category,
                onSelect = {
                    categoryManuallySet = true
                    category = it
                },
            )
            Text(
                text = "Selected: ${ExerciseCategory.displayName(category)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = unit == ExerciseUnit.REPS,
                    onClick = {
                        unit = ExerciseUnit.REPS
                        if (reps > 100) reps = 8
                    },
                    label = { Text("Reps") },
                )
                FilterChip(
                    selected = unit == ExerciseUnit.SECONDS,
                    onClick = {
                        unit = ExerciseUnit.SECONDS
                        if (reps < 5) reps = 30
                    },
                    label = { Text("Seconds") },
                )
            }

            NumberPickerField(
                label = "Sets",
                valueText = "$sets",
                onClick = { openPicker = "sets" },
            )
            NumberPickerField(
                label = if (unit == ExerciseUnit.SECONDS) "Seconds" else "Reps",
                valueText = if (unit == ExerciseUnit.SECONDS) "${reps}s" else "$reps",
                onClick = { openPicker = "reps" },
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Notes (optional)") },
                minLines = 2,
            )
            OutlinedTextField(
                value = formTip,
                onValueChange = { formTip = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Form tip (optional)") },
                minLines = 2,
            )

            PrimaryAction(
                text = if (existing == null) "Add training" else "Save changes",
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        DayEditorViewModel.EditorEntry(
                            planExerciseId = existing?.planExerciseId ?: newId(),
                            exerciseId = existing?.exerciseId,
                            name = name.trim(),
                            category = category,
                            unit = unit,
                            label = label.trim().ifBlank { defaultTrainingLabel(entryCount) },
                            sets = sets,
                            reps = reps,
                            restSeconds = restSeconds,
                            notes = notes.trim().takeIf { it.isNotBlank() },
                            formTip = formTip.trim().takeIf { it.isNotBlank() },
                        ),
                    )
                },
            )
        }
    }

}

@Composable
private fun RenameDayDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename day") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Day name") },
            )
        },
        confirmButton = {
            AppTextButton(onClick = { onConfirm(text) }) { Text("Save") }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Snaps a stored value (e.g. 8 reps) onto the closest step of the wheel. */
private fun nearestValue(values: List<Int>, value: Int): Int =
    values.minByOrNull { abs(it - value) } ?: value
