package com.sixmonths.app.ui.components

import androidx.annotation.RawRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.sixmonths.app.playback.SoundFx
import com.sixmonths.app.ui.theme.animationsEnabled

/**
 * The three studio animations that ship with the app: the streak flame, the weekly trophy
 * and the monster scanner. Everything Lottie lives here so the screens only pass a resource.
 */
object HouseAnimation {
    val FIRE = com.sixmonths.app.R.raw.fire
    val TROPHY = com.sixmonths.app.R.raw.trophy
    val SCANNER = com.sixmonths.app.R.raw.scanner
}

/**
 * Plays one of the bundled Lottie files. With animations switched off in Settings the first
 * frame is shown instead, so the UI keeps its shape and never spins.
 */
@Composable
fun HouseLottie(
    @RawRes rawRes: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    iterations: Int = LottieConstants.IterateForever,
    speed: Float = 1f,
) {
    val enabled = animationsEnabled
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations,
        speed = speed,
        isPlaying = enabled,
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        contentScale = ContentScale.Fit,
        modifier = modifier
            .semantics { contentDescription?.let { this.contentDescription = it } }
            .alpha(if (composition == null) 0f else 1f),
    )
}

/**
 * Full-screen celebration for the two big milestones. The animation scales up with a spring,
 * confetti is optional, and one tap anywhere closes it. A success hit plays once, together
 * with the animation — this is one of the only two sound moments left in the app.
 */
@Composable
fun CelebrationOverlay(
    title: String,
    @RawRes rawRes: Int,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    withConfetti: Boolean = false,
    onShown: () -> Unit = {},
) {
    val animate = animationsEnabled
    val enter by animateFloatAsState(
        targetValue = 1f,
        animationSpec = if (animate) {
            spring(dampingRatio = 0.45f, stiffness = 180f)
        } else {
            tween(durationMillis = 0)
        },
        label = "celebrationScale",
    )
    val fade by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = if (animate) 260 else 0, easing = FastOutSlowInEasing),
        label = "celebrationFade",
    )
    val interaction = remember { MutableInteractionSource() }

    LaunchedEffect(rawRes) {
        SoundFx.success()
        onShown()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = interaction, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        if (withConfetti && animate) {
            ConfettiOverlay(modifier = Modifier.fillMaxSize()) {}
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .graphicsLayer {
                    scaleX = (0.6f + 0.4f * enter)
                    scaleY = (0.6f + 0.4f * enter)
                    alpha = fade
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            HouseLottie(
                rawRes = rawRes,
                contentDescription = title,
                modifier = Modifier.size(320.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            subtitle?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The Monster Energy scan overlay: a translucent scrim plus the scanner animation, laid over
 * the captured photo while the flavour is being processed.
 */
@Composable
fun ScannerOverlay(
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HouseLottie(
                rawRes = HouseAnimation.SCANNER,
                contentDescription = "Scanning",
                modifier = Modifier
                    .size(190.dp)
                    .clip(MaterialTheme.shapes.large),
            )
            label?.let { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}
