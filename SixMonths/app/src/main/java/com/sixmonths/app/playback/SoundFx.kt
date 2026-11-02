package com.sixmonths.app.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.sixmonths.app.R

enum class SoundKind { POP, SUCCESS, SCAN }

/**
 * Central short-SFX player, backed by SoundPool.
 *
 * The files in `res/raw` are the original CC0 sounds from
 * https://github.com/wyzowl/sfx-kit (see THIRD_PARTY_NOTICES.md for the exact mapping).
 *
 * Two things used to make the app completely silent:
 * 1. The samples were routed to [AudioAttributes.USAGE_ASSISTANCE_SONIFICATION], which is the
 *    system-sound stream. Phones with "touch sounds" off mute that stream entirely, no matter
 *    the media volume. Playback now uses [AudioAttributes.USAGE_MEDIA], the stream the user
 *    actually controls, with sonification content type so audio focus is not stolen.
 * 2. SoundPool loads asynchronously. Playing before the load finished silently returned 0, so
 *    the first taps of every session were lost. [play] now queues the request and plays it the
 *    moment [SoundPool.OnLoadCompleteListener] confirms the sample.
 *
 * There is no click sound: buttons are silent, only the three milestone animations and a
 * completed set make a sound. [enabled] mirrors Settings.sfxEnabled; when false it is a no-op.
 */
object SoundFx {

    private const val MAX_STREAMS = 6
    private const val MAX_QUEUED = 4

    /** SoundPool accepts 0.5x .. 2.0x playback rate. */
    private const val MIN_RATE = 0.5f
    private const val MAX_RATE = 2.0f

    @Volatile
    var enabled: Boolean = true

    private class Sample(val kind: SoundKind) {
        @Volatile var soundId: Int = 0
        @Volatile var loaded: Boolean = false
    }

    private data class Queued(val kind: SoundKind, val volume: Float, val rate: Float)

    private val samples = SoundKind.entries.associateWith { Sample(it) }
    private val queue = ArrayDeque<Queued>()

    @Volatile
    private var soundPool: SoundPool? = null

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val pool = SoundPool.Builder()
                .setMaxStreams(MAX_STREAMS)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                val sample = samples.values.firstOrNull { it.soundId == sampleId } ?: return@setOnLoadCompleteListener
                if (status != 0) {
                    // Leave loaded = false; the next play queues instead of playing id 0.
                    return@setOnLoadCompleteListener
                }
                sample.loaded = true
                flush(sample)
            }

            val app = context.applicationContext
            samples.getValue(SoundKind.POP).soundId = pool.load(app, R.raw.sfx_pop, 1)
            samples.getValue(SoundKind.SUCCESS).soundId = pool.load(app, R.raw.sfx_success, 1)
            samples.getValue(SoundKind.SCAN).soundId = pool.load(app, R.raw.sfx_scan, 1)

            soundPool = pool
            initialized = true
        }
    }

    /**
     * @param rate playback speed, which also shifts the pitch. Used for the rising
     *   "Pop" on consecutive sets (1.0x, 1.1x, 1.21x, …).
     */
    fun play(kind: SoundKind, volume: Float = 0.65f, rate: Float = 1f) {
        if (!enabled) return
        val pool = soundPool ?: return
        val sample = samples[kind] ?: return
        val safeRate = rate.coerceIn(MIN_RATE, MAX_RATE)

        if (sample.loaded) {
            pool.play(sample.soundId, volume, volume, 1, 0, safeRate)
        } else {
            synchronized(queue) {
                if (queue.size >= MAX_QUEUED) queue.removeFirst()
                queue.addLast(Queued(kind, volume, safeRate))
            }
        }
    }

    /** Plays the newest queued request for a sample that just finished loading. */
    private fun flush(sample: Sample) {
        val pool = soundPool ?: return
        val queued = synchronized(queue) {
            val match = queue.lastOrNull { it.kind == sample.kind } ?: return
            queue.removeAll { it.kind == sample.kind }
            match
        }
        pool.play(sample.soundId, queued.volume, queued.volume, 1, 0, queued.rate)
    }

    /** Consecutive sets raise the pitch: pass 1.1f, 1.21f, … for the 2nd, 3rd, … set. */
    fun pop(pitch: Float = 1f) = play(SoundKind.POP, 0.55f, pitch)

    /** Played with the Fire and Trophy animations only. */
    fun success() = play(SoundKind.SUCCESS, 0.7f)

    /** Played with the Monster scanner animation only. */
    fun scan() = play(SoundKind.SCAN, 0.6f)

    /** 1.0x for the first set, +10 % pitch for every following set. Capped at 2.0x. */
    fun popForSet(setNumber: Int): Float {
        val steps = (setNumber - 1).coerceAtLeast(0)
        var rate = 1f
        repeat(steps.coerceAtMost(12)) { rate *= 1.1f }
        return rate.coerceAtMost(MAX_RATE)
    }
}
