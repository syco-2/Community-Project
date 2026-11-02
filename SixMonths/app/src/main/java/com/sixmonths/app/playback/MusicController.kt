package com.sixmonths.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random

/**
 * Thin client around the MediaSessionService. One controller for the whole app, connected
 * on demand by the Music screen.
 */
object MusicController {

    data class UiState(
        val connected: Boolean = false,
        val isPlaying: Boolean = false,
        val title: String? = null,
        val itemIndex: Int = -1,
        val error: String? = null,
        val shuffle: Boolean = false,
    )

    private var controller: MediaController? = null

    /** Kept outside the controller so it survives a reconnect and applies to the next queue. */
    private var shuffleEnabled = false

    /** A tap on Play before the service finished connecting must not be swallowed. */
    private var pendingPlay: Triple<List<String>, Int, List<String>>? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _state.value = _state.value.copy(
                title = mediaItem?.mediaMetadata?.title?.toString() ?: _state.value.title,
                itemIndex = controller?.currentMediaItemIndex ?: -1,
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            _state.value = _state.value.copy(error = error.message)
        }
    }

    fun connect(context: Context) {
        if (controller != null) {
            _state.value = _state.value.copy(connected = true)
            return
        }
        val appContext = context.applicationContext
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener(
            {
                val mediaController = runCatching { future.get() }.getOrNull()
                controller = mediaController
                if (mediaController != null) {
                    mediaController.addListener(listener)
                    _state.value = UiState(
                        connected = true,
                        isPlaying = mediaController.isPlaying,
                        title = mediaController.mediaMetadata.title?.toString(),
                        itemIndex = mediaController.currentMediaItemIndex,
                        shuffle = shuffleEnabled,
                    )
                    pendingPlay?.let { (uris, startIndex, titles) ->
                        pendingPlay = null
                        play(uris, startIndex, titles)
                    }
                } else {
                    _state.value = _state.value.copy(
                        connected = false,
                        error = "Music service could not be started.",
                    )
                }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun play(
        uris: List<String>,
        startIndex: Int = 0,
        titles: List<String> = emptyList(),
    ) {
        if (uris.isEmpty()) return
        val mediaController = controller
        if (mediaController == null) {
            pendingPlay = Triple(uris, startIndex, titles)
            return
        }
        val items = uris.mapIndexed { index, rawUri ->
            MediaItem.Builder()
                .setUri(Uri.parse(rawUri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(titles.getOrNull(index) ?: rawUri.substringAfterLast('/'))
                        .build(),
                )
                .build()
        }
        mediaController.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        mediaController.repeatMode = Player.REPEAT_MODE_ALL
        mediaController.shuffleModeEnabled = shuffleEnabled
        mediaController.prepare()
        mediaController.play()
    }

    /** Shuffle keeps repeating the playlist forever, just in a random order. */
    fun setShuffle(enabled: Boolean) {
        shuffleEnabled = enabled
        _state.value = _state.value.copy(shuffle = enabled)
        controller?.shuffleModeEnabled = enabled
    }

    /** Drops a song the user swiped away from the live queue. */
    fun removeItem(index: Int) {
        val mediaController = controller ?: return
        if (index in 0 until mediaController.mediaItemCount) {
            mediaController.removeMediaItem(index)
        }
    }

    /** Reorders the live queue so a long-press drag in the list keeps playing the same song. */
    fun moveItem(from: Int, to: Int) {
        val mediaController = controller ?: return
        if (from == to) return
        if (from !in 0 until mediaController.mediaItemCount) return
        mediaController.moveMediaItem(from, to)
    }

    /** True while the player is prepared and something is loaded. */
    fun hasItems(): Boolean = (controller?.mediaItemCount ?: 0) > 0

    fun togglePlayPause() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) mediaController.pause() else mediaController.play()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    /**
     * Workout start: one random song out of the playlist, shuffle on, so every following song
     * is random too and the queue repeats until the user stops it.
     */
    fun startForWorkout(context: Context, uris: List<String>) {
        if (uris.isEmpty()) return
        val mediaController = controller
        if (mediaController != null && mediaController.isPlaying) return
        connect(context)
        setShuffle(true)
        val titles = uris.map { raw ->
            runCatching { Uri.parse(raw).displayName(context) }.getOrNull().orEmpty()
        }
        play(uris, Random.nextInt(uris.size), titles)
    }

    private fun Uri.displayName(context: Context): String = runCatching {
        context.contentResolver.query(
            this,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
        ?: lastPathSegment?.substringAfterLast('/')
        ?: toString()
}
