package com.sixmonths.app.ui.screens.monsters
import com.sixmonths.app.ui.components.AppTextButton
import com.sixmonths.app.ui.components.AppIconButton

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.core.util.rememberHaptics
import com.sixmonths.app.data.repository.CabinetEntry
import com.sixmonths.app.data.repository.MonsterRepository
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.playback.SoundFx
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.ScannerOverlay
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.components.TabBarScrollTracker
import com.sixmonths.app.ui.theme.animationsEnabled
import com.sixmonths.app.ui.theme.sixMonthsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.coroutines.resume

/**
 * The Monsters tab: a 2D display cabinet of real Monster Energy cans.
 *
 * Manual by design. Tap "Add can", take a photo, type the flavour printed on the can. The
 * photo is centre-cropped to a clean square, stored in app-private storage and dropped into
 * its cabinet slot with a springy landing, a particle burst, the pop SFX and a medium haptic.
 * Slots you have not collected are sleek silhouettes; collected slots show your photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonstersScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val repository = ServiceLocator.monsterRepository
    val scope = rememberCoroutineScope()

    val captures by repository.observeAll()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val catalogRevision by repository.catalogRevision
        .collectAsStateWithLifecycle(initialValue = 0)

    val cabinet by produceState(
        initialValue = emptyList<CabinetEntry>(),
        captures,
        catalogRevision,
    ) {
        value = withContext(Dispatchers.IO) {
            runCatching { repository.cabinet() }.getOrDefault(emptyList())
        }
    }

    var capturing by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var unlockedKey by remember { mutableStateOf<String?>(null) }
    var burstTrigger by remember { mutableIntStateOf(0) }
    var selectedEntry by remember { mutableStateOf<CabinetEntry?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyGridState()

    TabBarScrollTracker(gridState = gridState)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        capturing = granted
    }

    fun startAdd() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            capturing = true
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (capturing) {
        CameraCapture(
            repository = repository,
            onCaptured = { photo ->
                capturing = false
                pendingPhoto = photo
            },
            onError = { error ->
                capturing = false
                message = error
            },
            onClose = { capturing = false },
            modifier = modifier,
        )
        return
    }

    val photoToName = pendingPhoto
    if (photoToName != null) {
        NameCanDialog(
            photo = photoToName,
            suggestionsFor = { typed -> repository.suggestions(typed) },
            saving = saving,
            onDismiss = {
                repository.discard(photoToName)
                pendingPhoto = null
                saving = false
            },
            onConfirm = { typedName ->
                saving = true
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) { repository.addCapture(photoToName, typedName) }
                    }
                    saving = false
                    pendingPhoto = null
                    result.onSuccess { monster ->
                        val key = monster.flavorId.ifBlank { monster.id }
                        unlockedKey = key
                        burstTrigger += 1
                        haptics.thud()
                        message = monster.name
                        // The grid snapshot above is one insert old; look the fresh slot up.
                        val index = runCatching {
                            withContext(Dispatchers.IO) {
                                repository.cabinet().indexOfFirst { it.key == key }
                            }
                        }.getOrDefault(-1)
                        if (index >= 0) {
                            delay(140)
                            gridState.animateScrollToItem(index)
                        }
                    }.onFailure { error ->
                        repository.discard(photoToName)
                        message = error.message ?: "Could not add that can."
                    }
                }
            },
        )
    }

    val collected = cabinet.count { it.unlocked }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Monstr",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = CircleShape,
                textStyle = MaterialTheme.typography.bodySmall,
                placeholder = {
                    Text("Search", style = MaterialTheme.typography.bodySmall)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
            )
            Text(
                text = "$collected / ${cabinet.size}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }

        PrimaryAction(text = "Add can", onClick = ::startAdd)

        if (permissionDenied) {
            SixMonthsCard {
                Text(
                    text = "Camera blocked",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                AppTextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Try again")
                }
            }
        }

        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.sixMonthsColors.success,
            )
        }


        val visible = remember(cabinet, query) {
            val needle = query.trim()
            if (needle.isEmpty()) {
                cabinet
            } else {
                cabinet.filter { it.name.contains(needle, ignoreCase = true) }
            }
        }

        // The cabinet is one shelf per flavour section: "Classic", "Ultra", "Java", ...
        val sections = remember(visible) { visible.groupBy { it.theme } }

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            sections.forEach { (theme, entries) ->
                if (theme.isNotBlank()) {
                    item(key = "section-$theme", span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(title = theme)
                    }
                }
                items(entries, key = { it.key }) { entry ->
                    CabinetSlot(
                        entry = entry,
                        dropIn = entry.key == unlockedKey,
                        burstTrigger = if (entry.key == unlockedKey) burstTrigger else 0,
                        onLongClick = {
                            if (entry.unlocked) {
                                haptics.tick()
                                selectedEntry = entry
                            }
                        },
                    )
                }
            }
        }
    }

    val sheetEntry = selectedEntry
    if (sheetEntry != null && sheetEntry.unlocked) {
        ModalBottomSheet(
            onDismissRequest = { if (!deleting) selectedEntry = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        val photo = sheetEntry.imageUri
                        if (photo != null) {
                            CanPhoto(
                                imageUri = photo,
                                contentDescription = sheetEntry.name,
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = sheetEntry.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = sheetEntry.capturedAt?.let { millis ->
                                "Added ${DateUtils.formatShort(DateUtils.toLocalDate(millis))}"
                            } ?: "Your own photo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.sixMonthsColors.textMuted,
                        )
                    }
                }
                AppTextButton(
                    enabled = !deleting,
                    onClick = {
                        val captureId = sheetEntry.captureId ?: return@AppTextButton
                        deleting = true
                        scope.launch {
                            val result = runCatching {
                                withContext(Dispatchers.IO) { repository.deleteCapture(captureId) }
                            }
                            deleting = false
                            result.onSuccess {
                                if (unlockedKey == sheetEntry.key) unlockedKey = null
                                selectedEntry = null
                                haptics.tick()
                                message = null
                            }.onFailure { error ->
                                message = error.message ?: "Could not remove that can."
                            }
                        }
                    },
                ) {
                    Text(
                        text = if (deleting) "Deleting…" else "Delete",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                AppTextButton(onClick = { selectedEntry = null }, enabled = !deleting) {
                    Text("Cancel")
                }
            }
        }
    }
}

/** Section title with a hairline running to the right edge of the grid. */
@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.sixMonthsColors.textMuted.copy(alpha = 0.35f)),
        )
    }
}

/** One display slot: a dark silhouette while empty, the cropped photo once collected. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CabinetSlot(
    entry: CabinetEntry,
    dropIn: Boolean,
    burstTrigger: Int,
    onLongClick: () -> Unit,
) {
    val accent = remember(entry.color) {
        runCatching { Color(android.graphics.Color.parseColor(entry.color)) }
            .getOrDefault(Color(0xFF2F80ED))
    }
    val drop = remember(entry.key) { Animatable(if (dropIn) -1f else 0f) }
    val bounce = remember(entry.key) { Animatable(1f) }
    val animate = animationsEnabled

    LaunchedEffect(dropIn, burstTrigger, animate) {
        if (!dropIn || burstTrigger == 0 || !animate) {
            drop.snapTo(0f)
            bounce.snapTo(1f)
            return@LaunchedEffect
        }
        drop.snapTo(-0.55f)
        bounce.snapTo(0.86f)
        drop.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessLow),
        )
        bounce.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.44f, stiffness = Spring.StiffnessMediumLow),
        )
    }

    Column(
        modifier = Modifier
            .aspectRatio(0.68f)
            .graphicsLayer {
                translationY = drop.value * size.height * 0.55f
                scaleX = bounce.value
                scaleY = bounce.value
            }
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(
                enabled = entry.unlocked,
                onClick = {},
                onLongClick = onLongClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    if (entry.unlocked) {
                        accent.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
                    },
                )
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (entry.unlocked && entry.imageUri != null) {
                CanPhoto(
                    imageUri = entry.imageUri,
                    contentDescription = entry.name,
                    accent = accent,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                EnergyCan(
                    color = accent,
                    silhouette = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (entry.unlocked && burstTrigger > 0) {
                UnlockBurst(
                    color = accent,
                    trigger = burstTrigger,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (entry.unlocked) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 3.dp, start = 2.dp, end = 2.dp),
            )
        }
    }
}

private sealed interface CanPhotoState {
    data object Loading : CanPhotoState
    data object Missing : CanPhotoState
    data class Ready(val bitmap: ImageBitmap) : CanPhotoState
}

/**
 * Decodes the stored 720 px square at screen size. Sampling keeps the whole 40-slot grid well
 * under a few MB of bitmaps. A missing file (restored backup, deleted cache) falls back to
 * the silhouette instead of spinning forever.
 */
@Composable
private fun CanPhoto(
    imageUri: String,
    contentDescription: String?,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val targetPx = remember(density) { (110 * density.density).toInt().coerceAtLeast(120) }
    val state by produceState<CanPhotoState>(CanPhotoState.Loading, imageUri, targetPx) {
        value = withContext(Dispatchers.IO) {
            decodeSquare(imageUri, targetPx)?.let(CanPhotoState::Ready) ?: CanPhotoState.Missing
        }
    }
    when (val current = state) {
        is CanPhotoState.Ready -> Image(
            bitmap = current.bitmap,
            contentDescription = contentDescription,
            modifier = modifier.clip(RoundedCornerShape(18.dp)),
            contentScale = ContentScale.Crop,
        )

        CanPhotoState.Missing -> EnergyCan(
            color = accent,
            silhouette = true,
            modifier = modifier,
        )

        CanPhotoState.Loading -> Box(
            modifier = modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun decodeSquare(imageUri: String, targetPx: Int): ImageBitmap? {
    val path = runCatching { Uri.parse(imageUri).path }.getOrNull() ?: return null
    return decodeSampled(path, targetPx)
}

/** Power-of-two sampling, so a 12 MP camera frame never lands in memory at full size. */
private fun decodeSampled(path: String, targetPx: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
    val bitmap: Bitmap = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        },
    ) ?: return null
    return bitmap.asImageBitmap()
}

/** A 2D energy can silhouette. Empty slots stay dark; it is the promise of a full shelf. */
@Composable
private fun EnergyCan(
    color: Color,
    silhouette: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val bodyWidth = size.width * 0.56f
        val bodyHeight = size.height * 0.82f
        val left = (size.width - bodyWidth) / 2f
        val top = (size.height - bodyHeight) / 2f
        val corner = bodyWidth * 0.22f

        val bodyColor = if (silhouette) Color(0xFF1B2430) else color
        val rimColor = if (silhouette) Color(0xFF243040) else Color.White.copy(alpha = 0.55f)

        drawRoundRect(
            brush = if (silhouette) {
                Brush.verticalGradient(listOf(bodyColor, bodyColor))
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        bodyColor.copy(alpha = 0.98f),
                        bodyColor.copy(alpha = 0.80f),
                        bodyColor,
                    ),
                    startY = top,
                    endY = top + bodyHeight,
                )
            },
            topLeft = Offset(left, top),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(corner),
        )

        drawRoundRect(
            color = rimColor,
            topLeft = Offset(left, top),
            size = Size(bodyWidth, bodyHeight * 0.045f),
            cornerRadius = CornerRadius(corner / 2f),
        )
        drawRoundRect(
            color = rimColor.copy(alpha = 0.5f),
            topLeft = Offset(left, top + bodyHeight * 0.955f),
            size = Size(bodyWidth, bodyHeight * 0.045f),
            cornerRadius = CornerRadius(corner / 2f),
        )

        if (!silhouette) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.0f),
                        Color.White.copy(alpha = 0.30f),
                        Color.White.copy(alpha = 0.0f),
                    ),
                    startX = left + bodyWidth * 0.12f,
                    endX = left + bodyWidth * 0.44f,
                ),
                topLeft = Offset(left + bodyWidth * 0.12f, top + bodyHeight * 0.06f),
                size = Size(bodyWidth * 0.32f, bodyHeight * 0.88f),
            )
        }

        repeat(3) { index ->
            val startX = left + bodyWidth * (0.30f + index * 0.16f)
            val path = Path().apply {
                moveTo(startX, top + bodyHeight * 0.20f)
                lineTo(startX - bodyWidth * 0.05f, top + bodyHeight * 0.62f)
                lineTo(startX + bodyWidth * 0.02f, top + bodyHeight * 0.62f)
                lineTo(startX + bodyWidth * 0.06f, top + bodyHeight * 0.20f)
                close()
            }
            drawPath(
                path = path,
                color = if (silhouette) {
                    Color.White.copy(alpha = 0.05f)
                } else {
                    Color.White.copy(alpha = 0.28f)
                },
            )
        }
    }
}

/** Particle burst in the can's own colour, fired once per add. */
@Composable
private fun UnlockBurst(color: Color, trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember(trigger) { Animatable(0f) }
    val animate = animationsEnabled
    LaunchedEffect(trigger, animate) {
        if (trigger <= 0 || !animate) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
        )
    }
    Canvas(modifier = modifier) {
        val value = progress.value
        if (value <= 0.001f) return@Canvas
        repeat(14) { index ->
            val angle = (index * (360f / 14f) + 7f) * (Math.PI / 180f)
            val distance = size.minDimension * (0.18f + 0.42f * value)
            drawCircle(
                color = color.copy(alpha = (1f - value).coerceIn(0f, 1f)),
                radius = (3.dp.toPx() * (1f - value * 0.6f)).coerceAtLeast(1f),
                center = Offset(
                    x = center.x + cos(angle).toFloat() * distance,
                    y = center.y + sin(angle).toFloat() * distance,
                ),
            )
        }
    }
}

/** Cropped preview + flavour name. Suggestion chips come from the shipped catalog. */
@Composable
private fun NameCanDialog(
    photo: File,
    suggestionsFor: suspend (String) -> List<String>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var suggestions by remember { mutableStateOf(emptyList<String>()) }

    val density = LocalDensity.current
    val previewPx = remember(density) { (220 * density.density).toInt().coerceAtLeast(320) }
    val preview by produceState<ImageBitmap?>(initialValue = null, photo.absolutePath, previewPx) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeSampled(photo.absolutePath, previewPx) }.getOrNull()
        }
    }

    LaunchedEffect(name) {
        delay(140)
        if (name.trim().length < 2) {
            suggestions = emptyList()
        } else {
            suggestions = runCatching { suggestionsFor(name) }.getOrDefault(emptyList())
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = "Name this can",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val image = preview
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = "Your can photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                    // Scanner Lottie runs over the captured photo while it is being processed.
                    if (saving) {
                        ScannerOverlay()
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 48) name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !saving,
                    label = { Text("Flavour") },
                    placeholder = { Text("Ultra Watermelon") },
                )
                if (suggestions.isNotEmpty() && !saving) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { suggestion ->
                            item {
                                AssistChip(
                                    onClick = { name = suggestion },
                                    label = { Text(suggestion) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppTextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.isNotBlank() && !saving,
            ) {
                Text(if (saving) "…" else "Add")
            }
        },
        dismissButton = {
            AppTextButton(onClick = onDismiss, enabled = !saving) { Text("Cancel") }
        },
    )
}

@Composable
private fun CameraCapture(
    repository: MonsterRepository,
    onCaptured: (File) -> Unit,
    onError: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = rememberHaptics()
    var busy by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture: ImageCapture = remember {
        // Rotation is read from the JPEG's EXIF tag when the photo is squared, so the camera
        // output can stay untouched here.
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    val cameraProvider = remember { CameraProviderHolder(context) }

    LaunchedEffect(Unit) {
        val provider = cameraProvider.get()
        if (provider == null) {
            cameraError = "No camera available on this device."
            return@LaunchedEffect
        }
        runCatching {
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
            )
        }.onFailure {
            cameraError = "Could not start the camera: ${it.message}"
        }
    }

    DisposableEffect(cameraProvider) {
        onDispose { cameraProvider.release() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f)),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Add a can",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            AppIconButton(
                onClick = onClose,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close camera")
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            cameraError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.sixMonthsColors.warning,
                    textAlign = TextAlign.Center,
                )
            }
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary),
                contentAlignment = Alignment.Center,
            ) {
                AppIconButton(
                    enabled = !busy && cameraError == null,
                    onClick = {
                        busy = true
                        SoundFx.scan()
                        val file = repository.newPhotoFile()
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(
                            options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    busy = false
                                    onCaptured(file)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    busy = false
                                    file.delete()
                                    onError("Capture failed: ${exception.message}")
                                }
                            },
                        )
                    },
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSecondary),
                    )
                }
            }
        }

        // Scanner animation over the live camera image while the JPEG is being written.
        if (busy) {
            ScannerOverlay(label = null)
        }
    }
}

/** Keeps the ProcessCameraProvider future outside recomposition and releases it on exit. */
private class CameraProviderHolder(private val context: Context) {
    private var provider: ProcessCameraProvider? = null
    private var loading = false

    suspend fun get(): ProcessCameraProvider? {
        provider?.let { return it }
        if (loading) return null
        loading = true
        val resolved = suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    val value = runCatching { future.get() }.getOrNull()
                    if (continuation.isActive) continuation.resume(value)
                },
                ContextCompat.getMainExecutor(context),
            )
        }
        provider = resolved
        loading = false
        return resolved
    }

    fun release() {
        provider?.unbindAll()
        provider = null
    }
}
