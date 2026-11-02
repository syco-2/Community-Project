package com.sixmonths.app.ui.screens.stats
import com.sixmonths.app.ui.components.AppIconButton

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sixmonths.app.core.time.DateUtils
import com.sixmonths.app.ui.components.EmptyIllustration
import com.sixmonths.app.ui.components.EmptyState
import com.sixmonths.app.ui.components.PartBadge
import com.sixmonths.app.ui.components.RollingNumber
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.components.SkeletonRows
import com.sixmonths.app.ui.components.TabBarScrollTrackerForColumn
import com.sixmonths.app.ui.theme.sixMonthsColors
import com.sixmonths.app.ui.util.viewModelFactory
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val viewModel: StatsViewModel = viewModel(
        factory = viewModelFactory { StatsViewModel() },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Stats", style = MaterialTheme.typography.headlineSmall) },
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
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        TabBarScrollTrackerForColumn(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.loading) {
                SkeletonRows(rows = 4, rowHeight = 96.dp)
                return@Column
            }

            SixMonthsCard {
                Text(
                    text = "Streak",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatBlock(label = "Current", value = state.streak.currentStreak)
                    StatBlock(label = "Longest", value = state.streak.longestStreak)
                    StatBlock(label = "Freezes", value = state.streak.freezeTokens)
                }
            }

            SixMonthsCard {
                Text(
                    text = "Totals",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    StatBlock(label = "Workouts", value = state.totalSessions)
                    StatBlock(label = "Sets", value = state.totalSets)
                    StatBlock(label = "Reps", value = state.totalReps)
                }
                Text(
                    text = "${formatDuration(state.totalDurationSeconds)} · " +
                        "${state.plannedTrainingDays} days/week",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.sixMonthsColors.textSecondary,
                )
            }

            SixMonthsCard {
                Text(
                    text = "Last 12 weeks",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Heatmap(heatmap = state.heatmap)
            }

            SixMonthsCard {
                Text(
                    text = "Workouts per week",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                WeeklyBars(weeklyCounts = state.weeklyCounts)
            }

            SixMonthsCard {
                Text(
                    text = "Recent sessions",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (state.recentSessions.isEmpty()) {
                    EmptyState(
                        illustration = EmptyIllustration.CHART,
                        title = "Nothing to measure yet",
                        message = null,
                    )
                } else {
                    state.recentSessions.forEach { session ->
                        val date = DateUtils.toLocalDate(session.finishedAt ?: session.startedAt)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = DateUtils.formatShort(date),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = DateUtils.formatDuration(session.durationSeconds ?: 0),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.sixMonthsColors.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: Int) {
    Column {
        RollingNumber(
            value = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.sixMonthsColors.textMuted,
        )
    }
}

@Composable
private fun Heatmap(heatmap: Map<LocalDate, Int>) {
    val today = LocalDate.now()
    val thisMonday = today.with(DayOfWeek.MONDAY)
    val weeks = (11 downTo 0).map { offset -> thisMonday.minusWeeks(offset.toLong()) }
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        weeks.forEach { monday ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(7) { dayIndex ->
                    val date = monday.plusDays(dayIndex.toLong())
                    val count = heatmap[date] ?: 0
                    val visible = !date.isAfter(today)
                    val color = when {
                        !visible -> Color.Transparent
                        count <= 0 -> emptyColor.copy(alpha = 0.35f)
                        count == 1 -> accent.copy(alpha = 0.45f)
                        count == 2 -> accent.copy(alpha = 0.7f)
                        else -> accent
                    }
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(color),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyBars(weeklyCounts: List<Pair<LocalDate, Int>>) {
    val max = (weeklyCounts.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        weeklyCounts.forEach { (_, count) ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((4 + (56 * count / max)).dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(accent.copy(alpha = if (count == 0) 0.2f else 0.85f)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.sixMonthsColors.textMuted,
                )
            }
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
