package com.sixmonths.app.ui.screens.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.data.prefs.Settings
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.ui.components.PlaceholderScaffold
import com.sixmonths.app.ui.components.PlaylistEditor
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.util.rememberAudioPicker
import kotlinx.coroutines.launch

/** Onboarding step 5: optional SAF music import. */
@Composable
fun OnboardingMusicScreen(
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val settings by ServiceLocator.settingsRepository.settings
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ServiceLocator.settingsRepository.setOnboardingStep(5)
    }

    val uris = settings?.musicPlaylistUris.orEmpty()

    val picker = rememberAudioPicker { uriList ->
        scope.launch {
            val current = ServiceLocator.settingsRepository.current().musicPlaylistUris
            ServiceLocator.settingsRepository.setMusicPlaylistUris(
                current + uriList.map { it.toString() },
            )
        }
    }

    PlaceholderScaffold(
        title = "Your music",
        summary = null,
        onBack = onBack,
        progress = 5 to 7,
        footer = {
            PrimaryAction(text = "Continue", onClick = onContinue)
            SecondaryAction(text = "Skip for now", onClick = onContinue)
        },
    ) {
        PlaylistEditor(
            uris = uris,
            onAddMusic = picker,
            onRemove = { uri ->
                scope.launch {
                    val current = ServiceLocator.settingsRepository.current().musicPlaylistUris
                    ServiceLocator.settingsRepository.setMusicPlaylistUris(current - uri)
                }
            },
            onMove = { from, to ->
                scope.launch {
                    val current = ServiceLocator.settingsRepository.current().musicPlaylistUris.toMutableList()
                    if (from in current.indices && to in current.indices) {
                        val item = current.removeAt(from)
                        current.add(to, item)
                        ServiceLocator.settingsRepository.setMusicPlaylistUris(current)
                    }
                }
            },
        )

    }
}
