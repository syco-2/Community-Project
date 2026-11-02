package com.sixmonths.app.ui.screens.music

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.core.util.rememberHaptics
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.playback.MusicController
import com.sixmonths.app.ui.components.AppIconButton
import com.sixmonths.app.ui.components.AppTextButton
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.components.SwipeToDeleteRow
import com.sixmonths.app.ui.components.TabBarScrollTrackerForColumn
import com.sixmonths.app.ui.components.springPress
import com.sixmonths.app.ui.icons.AppIcons
import com.sixmonths.app.ui.theme.sixMonthsColors
import com.sixmonths.app.ui.util.displayFileName
import com.sixmonths.app.ui.util.rememberAudioPicker
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Music tab: the playlist, one large play/pause button, previous and next. Nothing else.
 *
 * Songs come from the system file picker (SAF, persisted read permission) and playback runs in
 * a Media3 MediaSessionService, so it keeps playing with the screen off. A row leaves the list
 * with a swipe to the left; a long press picks it up and drops it into another slot.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val musicState by MusicController.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        MusicController.connect(context)
    }

    val uris = settings?.musicPlaylistUris.orEmpty()
    val titles = remember(uris) {
        uris.map { rawUri -> Uri.parse(rawUri).displayFileName(context) }
    }

    var notificationAsked by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    fun askForNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || notificationAsked) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        notificationAsked = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun playPlaylist(startIndex: Int = 0) {
        if (uris.isEmpty()) return
        askForNotificationPermissionOnce()
        MusicController.connect(context)
        MusicController.play(uris, startIndex, titles)
    }

    val picker = rememberAudioPicker { picked ->
        scope.launch {
            val current = ServiceLocator.settingsRepository.current().musicPlaylistUris
            ServiceLocator.settingsRepository.setMusicPlaylistUris(
                current + picked.map { it.toString() },
            )
        }
    }

    fun moveTrack(from: Int, to: Int) {
        if (from == to) return
        scope.launch {
            val current = ServiceLocator.settingsRepository.current().musicPlaylistUris
            if (from !in current.indices || to !in current.indices) return@launch
            val reordered = current.toMutableList().apply { add(to, removeAt(from)) }
            ServiceLocator.settingsRepository.setMusicPlaylistUris(reordered)
            MusicController.moveItem(from, to)
        }
    }

    fun removeTrack(rawUri: String) {
        scope.launch {
            val current = ServiceLocator.settingsRepository.current().musicPlaylistUris
            val index = current.indexOf(rawUri)
            if (index < 0) return@launch
            ServiceLocator.settingsRepository.setMusicPlaylistUris(
                current.filterNot { it == rawUri },
            )
            MusicController.removeItem(index)
            haptics.tick()
        }
    }

    // Long-press pick-up: the row follows the finger and lands in the slot below it.
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }

    val scrollState = rememberScrollState()
    TabBarScrollTrackerForColumn(scrollState)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Music", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    if (onBack != null) {
                        AppIconButton(onClick = onBack) {
                            Icon(AppIcons.Headphones, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    AppTextButton(onClick = picker) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SixMonthsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransportButton(
                        icon = AppIcons.Shuffle,
                        contentDescription = if (musicState.shuffle) "Shuffle on" else "Shuffle off",
                        enabled = uris.isNotEmpty(),
                        toggled = musicState.shuffle,
                        onClick = {
                            MusicController.connect(context)
                            if (musicState.shuffle) {
                                MusicController.setShuffle(false)
                            } else {
                                MusicController.setShuffle(true)
                                if (uris.isNotEmpty()) {
                                    playPlaylist(Random.nextInt(uris.size))
                                }
                            }
                        },
                    )
                    TransportButton(
                        icon = AppIcons.Previous,
                        contentDescription = "Previous",
                        enabled = uris.isNotEmpty(),
                        onClick = MusicController::previous,
                    )
                    TransportButton(
                        icon = if (musicState.isPlaying) AppIcons.Pause else AppIcons.Play,
                        contentDescription = if (musicState.isPlaying) "Pause" else "Play",
                        enabled = uris.isNotEmpty(),
                        primary = true,
                        onClick = {
                            if (uris.isNotEmpty() && !MusicController.hasItems()) {
                                playPlaylist(0)
                            } else {
                                MusicController.togglePlayPause()
                            }
                        },
                    )
                    TransportButton(
                        icon = AppIcons.Next,
                        contentDescription = "Next",
                        enabled = uris.isNotEmpty(),
                        onClick = MusicController::next,
                    )
                }
            }

            if (uris.isEmpty()) {
                Text(
                    text = "No tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                SixMonthsCard {
                    val rowHeight = 48.dp
                    val rowSpacing = 8.dp
                    val rowPitchPx = with(LocalDensity.current) {
                        (rowHeight + rowSpacing).toPx()
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(rowSpacing),
                    ) {
                        uris.forEachIndexed { index, rawUri ->
                            key(rawUri, index) {
                                val name = titles.getOrNull(index) ?: "Track ${index + 1}"
                                val playing = musicState.itemIndex == index && musicState.isPlaying
                                val dragging = dragIndex == index
                                val interaction = remember(rawUri) { MutableInteractionSource() }
                                SwipeToDeleteRow(
                                    onDelete = { removeTrack(rawUri) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(rowHeight)
                                        .graphicsLayer {
                                            if (dragging) {
                                                translationY = dragOffset
                                                scaleX = 1.04f
                                                scaleY = 1.04f
                                            }
                                        }
                                        .zIndex(if (dragging) 1f else 0f),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(rowHeight)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .springPress(interaction, true)
                                            .clickable(
                                                interactionSource = interaction,
                                                indication = null,
                                            ) { playPlaylist(index) }
                                            .pointerInput(index, uris.size) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        dragIndex = index
                                                        dragOffset = 0f
                                                        haptics.tick()
                                                    },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        dragOffset += dragAmount.y
                                                    },
                                                    onDragEnd = {
                                                        val from = dragIndex
                                                        dragIndex = null
                                                        if (from != null) {
                                                            val slots = (dragOffset / rowPitchPx)
                                                                .roundToInt()
                                                            dragOffset = 0f
                                                            moveTrack(
                                                                from,
                                                                (from + slots).coerceIn(
                                                                    0,
                                                                    uris.lastIndex,
                                                                ),
                                                            )
                                                        }
                                                    },
                                                    onDragCancel = {
                                                        dragIndex = null
                                                        dragOffset = 0f
                                                    },
                                                )
                                            }
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (playing) {
                                                        MaterialTheme.colorScheme.primary
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                    },
                                                ),
                                        )
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (playing) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TransportButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    primary: Boolean = false,
    toggled: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val filled = primary || toggled
    Box(
        modifier = Modifier
            .size(if (primary) 76.dp else 52.dp)
            .clip(CircleShape)
            .background(
                if (filled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .springPress(interaction, enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(if (primary) 32.dp else 22.dp),
            tint = if (filled) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
