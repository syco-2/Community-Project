package com.sixmonths.app.ui.screens.plan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sixmonths.app.core.util.rememberHaptics
import com.sixmonths.app.di.ServiceLocator
import com.sixmonths.app.domain.AutoFixPlan
import com.sixmonths.app.domain.CoachAudit
import com.sixmonths.app.domain.CoachPoint
import com.sixmonths.app.domain.CoachReport
import com.sixmonths.app.domain.CoachRule
import com.sixmonths.app.domain.PlanAutoFix
import com.sixmonths.app.playback.SoundFx
import com.sixmonths.app.ui.components.AppIconButton
import com.sixmonths.app.ui.components.PrimaryAction
import com.sixmonths.app.ui.components.SecondaryAction
import com.sixmonths.app.ui.components.SixMonthsCard
import com.sixmonths.app.ui.theme.sixMonthsColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val LOADING_STAGES = listOf(
    "Skill freshness",
    "Joint frequency",
    "Daily volume",
    "Verdict",
)

private const val LOADING_TOTAL_MS = 3_000L
private const val LOADING_STAGE_MS = LOADING_TOTAL_MS / 4

/**
 * The elite calisthenics report.
 *
 * The audit is pure and instant, but the screen deliberately takes three seconds so the user
 * sees the four checks that matter: joint frequency, skill progression, push/pull volume and
 * the final verdict. The auto-improve button writes the corrected week back to Room in one
 * transaction, plays the pop, shows the success card and returns to Edit Plan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanAnalysisScreen(
    onBack: () -> Unit,
    onEditPlan: () -> Unit,
) {
    var report by remember { mutableStateOf<CoachReport?>(null) }
    var stage by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var applying by remember { mutableStateOf(false) }
    var appliedFix by remember { mutableStateOf<AutoFixPlan?>(null) }
    var applyError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    LaunchedEffect(Unit) {
        val startedAt = System.currentTimeMillis()
        val audit = async(Dispatchers.IO) {
            runCatching { CoachAudit.audit(ServiceLocator.planRepository.loadPlan()) }.getOrNull()
        }
        LOADING_STAGES.forEachIndexed { index, _ ->
            stage = index
            delay(LOADING_STAGE_MS)
        }
        report = audit.await()
        val remaining = LOADING_TOTAL_MS - (System.currentTimeMillis() - startedAt)
        if (remaining > 0) delay(remaining)
        revealed = true
    }

    val improve: () -> Unit = {
        if (!applying && appliedFix == null) {
            applying = true
            applyError = null
            scope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        val fix = PlanAutoFix.build(ServiceLocator.planRepository.loadPlan())
                        ServiceLocator.planRepository.applyPlanFix(fix)
                        fix
                    }
                }
                applying = false
                result.onSuccess { fix ->
                    SoundFx.success()
                    haptics.confirm()
                    appliedFix = fix
                    delay(2_200)
                    onEditPlan()
                }.onFailure { throwable ->
                    applyError = throwable.message ?: "Could not apply the fixes."
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text("Plan analysis", style = MaterialTheme.typography.headlineSmall)
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val current = report
            when {
                !revealed -> AnalysisLoading(stage = stage)
                current == null -> AnalysisError(onBack = onBack)
                else -> CoachReportBody(
                    report = current,
                    applying = applying,
                    applyError = applyError,
                    onImprove = improve,
                    onBack = onBack,
                )
            }

            AnimatedVisibility(
                visible = appliedFix != null,
                enter = fadeIn(tween(180)) + scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                ),
                exit = fadeOut(tween(120)),
            ) {
                appliedFix?.let { SuccessOverlay(it) }
            }
        }
    }
}

// ----- Loading ---------------------------------------------------------------

@Composable
private fun AnalysisLoading(stage: Int) {
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            ScoreRing(score = ((stage + 1) * 100) / LOADING_STAGES.size, modifier = Modifier.size(104.dp))
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = "ANALYZING THE WEEK",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
        )
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(
            progress = { (stage + 1f) / LOADING_STAGES.size },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape),
            color = accent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LOADING_STAGES.forEachIndexed { index, label ->
                val done = index < stage
                val current = index == stage
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    done -> MaterialTheme.sixMonthsColors.success.copy(alpha = 0.18f)
                                    current -> accent.copy(alpha = 0.18f)
                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) {
                            CheckMark(size = 14.dp)
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (current) accent else MaterialTheme.sixMonthsColors.textMuted,
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            done -> MaterialTheme.sixMonthsColors.textSecondary
                            current -> MaterialTheme.colorScheme.onBackground
                            else -> MaterialTheme.sixMonthsColors.textMuted
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalysisError(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WarningMark(size = 44.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Could not read the plan",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        SecondaryAction(text = "Back", onClick = onBack)
    }
}

// ----- Report ----------------------------------------------------------------

@Composable
private fun CoachReportBody(
    report: CoachReport,
    applying: Boolean,
    applyError: String?,
    onImprove: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ReportHero(report)
        SectionLabel("CHECKS")
        report.rules.forEach { rule -> RuleCard(rule) }

        SectionLabel("PROS")
        ProsCard(report.pros)

        SectionLabel("RISKS")
        if (report.risks.isEmpty()) {
            SixMonthsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CheckMark()
                    Text(
                        text = "No red flags",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        } else {
            report.risks.forEach { risk -> RiskCard(risk) }
        }

        SectionLabel("VERDICT")
        VerdictCard(report)

        Spacer(Modifier.height(2.dp))
        PrimaryAction(
            text = if (applying) "Improving plan…" else "Improve Plan",
            onClick = onImprove,
            enabled = !applying,
        )
        applyError?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.sixMonthsColors.danger,
            )
        }
        SecondaryAction(text = "Back to plan", onClick = onBack)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ReportHero(report: CoachReport) {
    val scoreColor = scoreColor(report.verdict.score)
    SixMonthsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SIX-MONTH READINESS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = report.verdict.headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = report.verdict.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.sixMonthsColors.textSecondary,
                )
            }
            ScoreRing(
                score = report.verdict.score,
                modifier = Modifier.size(86.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeroStat("PUSH/DAY", "${report.maxDailyPush}", Modifier.weight(1f))
            HeroStat("SKILL DAYS", "${report.skillDays}", Modifier.weight(1f))
            HeroStat("LONGEST RUN", "${report.maxConsecutiveLoad}", Modifier.weight(1f))
        }
        Text(
            text = "${report.trainingDays} training days · ${report.weeklySets} weekly sets · " +
                "${report.pushSets} push / ${report.pullSets} pull",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.sixMonthsColors.textMuted,
        )
        Text(
            text = "Readiness ${report.verdict.score}/100",
            style = MaterialTheme.typography.labelSmall,
            color = scoreColor,
        )
    }
}

/** One graded rule: name, grade out of ten, verdict and the concrete fix. */
@Composable
private fun RuleCard(rule: CoachRule) {
    val accent = scoreColor(rule.grade * 10)
    SixMonthsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rule.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = rule.verdict,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.sixMonthsColors.textSecondary,
                )
            }
            Text(
                text = "${rule.grade}/10",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Text(
            text = rule.advice,
            style = MaterialTheme.typography.bodySmall,
            color = if (rule.passed) MaterialTheme.sixMonthsColors.textSecondary else accent,
        )
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.sixMonthsColors.textMuted,
        )
    }
}

@Composable
private fun ProsCard(pros: List<CoachPoint>) {
    SixMonthsCard {
        if (pros.isNotEmpty()) {
            pros.forEachIndexed { index, point ->
                ProRow(point)
                if (index != pros.lastIndex) Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun ProRow(point: CoachPoint) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CheckMark()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = point.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = point.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.sixMonthsColors.textSecondary,
            )
        }
    }
}

@Composable
private fun RiskCard(point: CoachPoint) {
    SixMonthsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            WarningMark()
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = point.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (point.critical) {
                        Text(
                            text = "FIX FIRST",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.sixMonthsColors.warning,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.sixMonthsColors.warning.copy(alpha = 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Text(
                    text = point.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.sixMonthsColors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun VerdictCard(report: CoachReport) {
    val accent = scoreColor(report.verdict.score)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = accent.copy(alpha = 0.12f),
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "VERDICT",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${report.verdict.score}/100",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                )
            }
            Text(
                text = report.verdict.headline,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = report.verdict.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.sixMonthsColors.textSecondary,
            )
            Text(
                text = report.verdict.growth,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = report.verdict.skills,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SuccessOverlay(fix: AutoFixPlan) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.68f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 26.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CheckMark(size = 64.dp)
                Text(
                    text = "Plan improved",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                fix.summary.forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.sixMonthsColors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ----- Marks and helpers -----------------------------------------------------

@Composable
private fun CheckMark(size: androidx.compose.ui.unit.Dp = 30.dp) {
    val color = MaterialTheme.sixMonthsColors.success
    val stroke = size * 0.07f
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.52f)) {
            val width = this.size.width
            val height = this.size.height
            val path = Path().apply {
                moveTo(width * 0.16f, height * 0.52f)
                lineTo(width * 0.42f, height * 0.76f)
                lineTo(width * 0.86f, height * 0.26f)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(
                    width = stroke.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

@Composable
private fun WarningMark(size: androidx.compose.ui.unit.Dp = 30.dp) {
    val color = MaterialTheme.sixMonthsColors.warning
    val stroke = size * 0.06f
    val dot = size * 0.035f
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size * 0.56f)) {
            val width = this.size.width
            val height = this.size.height
            val triangle = Path().apply {
                moveTo(width * 0.5f, height * 0.08f)
                lineTo(width * 0.94f, height * 0.88f)
                lineTo(width * 0.06f, height * 0.88f)
                close()
            }
            drawPath(
                path = triangle,
                color = color,
                style = Stroke(
                    width = stroke.toPx(),
                    join = StrokeJoin.Round,
                ),
            )
            drawLine(
                color = color,
                start = Offset(width * 0.5f, height * 0.38f),
                end = Offset(width * 0.5f, height * 0.62f),
                strokeWidth = stroke.toPx(),
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = color,
                radius = dot.toPx(),
                center = Offset(width * 0.5f, height * 0.75f),
            )
        }
    }
}

@Composable
private fun ScoreRing(score: Int, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = (score / 100f).coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "score-ring",
    )
    val color = scoreColor(score)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "/100",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.sixMonthsColors.textMuted,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun scoreColor(score: Int): Color = when {
    score >= 85 -> MaterialTheme.sixMonthsColors.success
    score >= 65 -> MaterialTheme.colorScheme.primary
    score >= 45 -> MaterialTheme.sixMonthsColors.warning
    else -> MaterialTheme.sixMonthsColors.danger
}
