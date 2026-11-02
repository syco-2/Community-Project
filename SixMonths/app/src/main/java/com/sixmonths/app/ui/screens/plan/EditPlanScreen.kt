package com.sixmonths.app.ui.screens.plan
import com.sixmonths.app.ui.components.AppIconButton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.Weekday
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.ui.components.StaggeredItem
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.components.bouncyClickable
import com.sixmonths.app.ui.theme.sixMonthsColors
import kotlinx.coroutines.launch

/**
 * "Edit plan" used to throw the user back into the onboarding wizard. This is the replacement:
 * a clean overview of all seven days, with a tap-through into the Day Editor for exact
 * exercises, sets and rest. The Analyze Plan button at the bottom runs the deterministic
 * hypertrophy/skill check.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlanScreen(
    onBack: () -> Unit,
    onOpenDay: (Int) -> Unit,
    onAnalyze: () -> Unit,
) {
    val plan by ServiceLocator.planRepository.observePlan()
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ServiceLocator.planRepository.ensureWeek()
    }

    val days = plan?.days.orEmpty()
    val trainingDays = days.count { it.planDay.state == DayState.TRAINING }
    val totalTrainings = days.sumOf { it.totalTrainings }
    val totalSets = days.sumOf { it.totalSets }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Edit plan", style = MaterialTheme.typography.headlineSmall)
                },
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PrimaryAction(text = "Analyze plan", onClick = onAnalyze, enabled = plan != null)
                    SecondaryAction(text = "Done", onClick = onBack)
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SixMonthsCard {
                    Text(
                        text = "YOUR WEEK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "$trainingDays training ${if (trainingDays == 1) "day" else "days"} · " +
                            "$totalTrainings trainings · $totalSets sets",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            if (plan == null) {
                item {
                    com.sixmonths.app.ui.components.SkeletonRows(rows = 4, rowHeight = 84.dp)
                }
            } else {
                itemsIndexed(days, key = { _, day -> day.planDay.id }) { index, day ->
                    StaggeredItem(index = index) {
                        DayOverviewCard(
                            day = day,
                            onOpen = { onOpenDay(day.planDay.dayOfWeek) },
                            onCycleState = {
                                scope.launch {
                                    ServiceLocator.planRepository.setDayState(
                                        dayOfWeek = day.planDay.dayOfWeek,
                                        state = nextState(day.planDay.state),
                                    )
                                }
                            },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@Composable
private fun DayOverviewCard(
    day: DaySnapshot,
    onOpen: () -> Unit,
    onCycleState: () -> Unit,
) {
    val state = day.planDay.state
    val accent = when (state) {
        DayState.TRAINING -> MaterialTheme.colorScheme.primary
        DayState.OPTIONAL -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.sixMonthsColors.textMuted
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onOpen),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Weekday.fullName(day.planDay.dayOfWeek),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = DayState.displayName(state).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f))
                        .bouncyClickable(onClick = onCycleState)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            Text(
                text = day.planDay.name ?: when (state) {
                    DayState.REST -> "Rest day"
                    DayState.OPTIONAL -> "Optional session"
                    else -> "Training day"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.sixMonthsColors.textMuted,
            )

            if (day.entries.isEmpty()) {
                Text(
                    text = "No exercises",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.sixMonthsColors.textSecondary,
                )
            } else {
                day.entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${entry.sets} × ${entry.reps}${if (entry.unit == "SECONDS") "s" else ""}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.sixMonthsColors.textSecondary,
                        )
                    }
                }
                Text(
                    text = "${day.totalTrainings} trainings · ${day.totalSets} sets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }
        }
    }
}

private fun nextState(current: String): String = when (current) {
    DayState.REST -> DayState.TRAINING
    DayState.TRAINING -> DayState.OPTIONAL
    else -> DayState.REST
}
