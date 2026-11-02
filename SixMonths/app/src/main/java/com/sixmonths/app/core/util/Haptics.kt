package com.sixmonths.app.core.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * The app's haptic vocabulary:
 * - [tick]      light tick for normal taps
 * - [thud]      medium thud when a set is logged
 * - [heavy]     heavy confirmation for destructive actions
 * - [heartbeat] heartbeat rhythm for streak milestones and the future-message unlock
 */
class Haptics(
    private val feedback: HapticFeedback,
    private val vibrator: Vibrator?,
) {
    /**
     * 10 ms tap. Goes straight to the vibrator when one exists: Compose's TextHandleMove maps
     * to a no-op on several OEMs, which is why taps felt completely dead.
     */
    fun tick() {
        val device = vibrator
        if (device != null && device.hasVibrator()) {
            val done = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    device.vibrate(VibrationEffect.createOneShot(10L, 90))
                } else {
                    device.vibrate(10L)
                }
            }.isSuccess
            if (done) return
        }
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun thud() {
        val device = vibrator
        if (device != null && device.hasVibrator()) {
            val done = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    device.vibrate(VibrationEffect.createOneShot(18L, 160))
                } else {
                    device.vibrate(18L)
                }
            }.isSuccess
            if (done) return
        }
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /** Kept as an alias so older call sites keep the "confirm" meaning. */
    fun confirm() = thud()

    fun heavy() {
        vibrate(effect = VibrationEffect.createOneShot(220L, 255), fallbackMs = 220L)
    }

    fun heartbeat() {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(
                    longArrayOf(0L, 45L, 110L, 70L),
                    intArrayOf(0, 200, 0, 160),
                    -1,
                )
                device.vibrate(effect)
            } else {
                device.vibrate(longArrayOf(0L, 45L, 110L, 70L), -1)
            }
        }
    }

    private fun vibrate(effect: VibrationEffect, fallbackMs: Long) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        runCatching { device.vibrate(effect) }
            .onFailure { runCatching { device.vibrate(fallbackMs) } }
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val feedback = LocalHapticFeedback.current
    val context = LocalContext.current
    return remember(feedback, context) {
        Haptics(feedback, context.vibratorOrNull())
    }
}

@Suppress("DEPRECATION")
private fun Context.vibratorOrNull(): Vibrator? =
    runCatching { getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }.getOrNull()
