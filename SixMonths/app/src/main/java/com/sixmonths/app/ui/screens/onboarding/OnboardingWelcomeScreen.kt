package com.sixmonths.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sixmonths.app.ui.components.OnboardingProgressDots
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.ui.components.SixMonthsLogo

/** Onboarding step 1. Shell only in Part 1; the wizard logic lands in Part 2. */
@Composable
fun OnboardingWelcomeScreen(onGetStarted: () -> Unit) {
    LaunchedEffect(Unit) {
        ServiceLocator.settingsRepository.setOnboardingStep(1)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SixMonthsLogo(modifier = Modifier.size(132.dp))

        Spacer(Modifier.height(28.dp))

        Text(
            text = "Six Months",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(40.dp))

        OnboardingProgressDots(currentStep = 1, totalSteps = 7)

        Spacer(Modifier.height(28.dp))

        PrimaryAction(text = "Get Started", onClick = onGetStarted)
    }
}
