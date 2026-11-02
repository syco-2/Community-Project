package com.sixmonths.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.data.backup.AutoBackupScheduler
import com.sixmonths.app.data.prefs.Settings
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.notifications.NotificationHelper
import com.sixmonths.app.notifications.ReminderScheduler
import com.sixmonths.app.playback.MusicController
import com.sixmonths.app.playback.SoundFx
import com.sixmonths.app.ui.navigation.Routes
import com.sixmonths.app.ui.navigation.SixMonthsNavHost
import com.sixmonths.app.ui.theme.SixMonthsTheme
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            SixMonthsRoot()
        }
    }
}

@Composable
private fun SixMonthsRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Null while DataStore's first value is loading; avoids flashing the wrong start route.
    val settingsFlow = remember {
        ServiceLocator.settingsRepository.settings.map<Settings, Settings?> { it }
    }
    val settingsState by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
    val settings = settingsState

    // Ask once per process for the notification permission as soon as onboarding is done.
    // Before this, the only request lived on the reminders step, so a user who tapped through
    // that step (or reinstalled and skipped it) could never receive a reminder.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The system setting is the source of truth; screens re-check it on resume. */ }
    var askedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings?.onboardingCompleted) {
        val current = settings ?: return@LaunchedEffect
        if (!current.onboardingCompleted) return@LaunchedEffect
        NotificationHelper.ensureChannel(context)
        ReminderScheduler.schedule(context, current)
        AutoBackupScheduler.sync(context, current)
        if (!askedNotificationPermission &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.canNotify(context)
        ) {
            askedNotificationPermission = true
            runCatching {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(settings?.sfxEnabled) {
        SoundFx.enabled = settings?.sfxEnabled ?: true
    }

    // The weekly backup follows the switch and the folder without a restart.
    LaunchedEffect(settings?.autoBackupEnabled, settings?.autoBackupTreeUri) {
        val current = settings ?: return@LaunchedEffect
        AutoBackupScheduler.sync(context, current)
    }

    // Connect once for the whole process, so a workout can duck music that was started on the
    // Music tab, and the background service is controllable again after a cold start.
    LaunchedEffect(Unit) {
        MusicController.connect(context)
    }

    // Streak evaluation is the only source of truth for missed days. Runs on every resume,
    // so a midnight rollover with the app closed is caught here.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch {
            val current = ServiceLocator.settingsRepository.current()
            ServiceLocator.streakEngine.evaluate()
            if (current.onboardingCompleted) {
                ReminderScheduler.schedule(context, current)
            }
        }
    }

    SixMonthsTheme(animationsEnabled = settings?.animationsEnabled ?: true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when {
                settings == null -> LoadingScreen()

                else -> SixMonthsNavHost(
                    startDestination = if (settings.onboardingCompleted) {
                        Routes.DASHBOARD
                    } else {
                        Routes.onboardingRoute(settings.onboardingStep)
                    },
                    animationsEnabled = settings.animationsEnabled,
                )
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
