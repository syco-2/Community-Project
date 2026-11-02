package com.sixmonths.app.domain

import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.core.model.ExerciseUnit
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.data.model.PlanEntryRow
import com.sixmonths.app.data.model.PlanSnapshot
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

enum class AnalysisLevel { STRONG, SOLID, BUILDING, LOW }

/** One number line inside a report card, e.g. "Push · 14 / 12 sets". */
data class AnalysisMetric(
    val label: String,
    val value: String,
    /** 0..1 for the bar; null hides the bar. */
    val fraction: Float? = null,
    val level: AnalysisLevel? = null,
)

/**
 * One Apple-style report card. [score] is 0..100 and [grade] is the same value on the
 * 1..10 scale the UI shows ("Hypertrophy: 8/10").
 */
data class AnalysisCard(
    val key: String,
    val title: String,
    val score: Int,
    val level: AnalysisLevel,
    val headline: String,
    val detail: String,
    val metrics: List<AnalysisMetric> = emptyList(),
    val bullets: List<String> = emptyList(),
) {
    val grade: Int get() = ((score + 5) / 10).coerceIn(0, 10)
}

data class MuscleGroupVolume(
    val group: String,
    val weeklySets: Int,
    val effectiveSets: Int,
    val daysPerWeek: Int,
    val targetSets: Int,
    val level: AnalysisLevel,
)

data class SkillProgress(
    val key: String,
    val skill: String,
    val exercises: List<String>,
    val weeklySets: Int,
    val daysPerWeek: Int,
    val targetDays: Int,
    val targetSets: Int,
    val onTrack: Boolean,
    val verdict: String,
    val timeline: String,
    val level: AnalysisLevel,
)

/** A concrete risk with the fix spelled out, shown as its own card. */
data class PlanWarning(
    val title: String,
    val detail: String,
    val fix: String,
    val level: AnalysisLevel,
)

data class PlanAnalysis(
    val overall: AnalysisCard,
    val hypertrophy: AnalysisCard,
    val skills: AnalysisCard,
    val balance: AnalysisCard,
    val joints: AnalysisCard,
    val cards: List<AnalysisCard>,
    val muscleGroups: List<MuscleGroupVolume>,
    val skillRows: List<SkillProgress>,
    val warnings: List<PlanWarning>,
    val add: List<String>,
    val remove: List<String>,
    val trainingDays: Int,
    val weeklySets: Int,
    val estimatedWeeklyMinutes: Int,
)

/**
 * Deep, deterministic calisthenics review of the whole plan.
 *
 * Four independent grades, each out of ten:
 *  1. Hypertrophy — effective weekly sets per muscle group (Push, Pull, Core, Legs), the
 *     10–20 set growth window, 2–3× frequency and the overtraining ceiling.
 *  2. Skill progression — handstand and planche practice specifically (HSPU, wall
 *     handstand, tuck planche, planche lean, pseudo planche push-ups, protraction), plus
 *     lever, muscle-up and flag work, mapped onto a realistic six-month timeline.
 *  3. Balance — push-to-pull ratio, core coverage, leg work and total weekly load.
 *  4. Joint health — wrist and elbow preparation for straight-arm work, overhead volume
 *     and the number of loading days without a deload.
 *
 * Everything is threshold-based and pure Kotlin: the same plan always produces exactly the
 * same report, and no data leaves the device. Holds below 20 s and very low or very high rep
 * sets count less than full-range working sets, so a plan cannot buy volume with 2-rep sets.
 */
object PlanAnalyzer {

    fun analyze(plan: PlanSnapshot): PlanAnalysis {
        val practiced = plan.days.filter {
            it.planDay.state == DayState.TRAINING || it.planDay.state == DayState.OPTIONAL
        }
        val trainingOnly = plan.days.filter { it.planDay.state == DayState.TRAINING }
        val entries = practiced.flatMap { day -> day.entries.map { day to it } }

        val groups = MuscleGroup.entries.map { group ->
            val rows = entries.filter { (_, entry) -> MuscleGroup.of(entry) == group }
            val weeklySets = rows.sumOf { it.second.sets }
            val effective = rows.sumOf { (_, entry) -> effectiveSets(entry) }
            val days = rows.map { it.first.planDay.dayOfWeek }.distinct().size
            MuscleGroupVolume(
                group = group.label,
                weeklySets = weeklySets,
                effectiveSets = effective,
                daysPerWeek = days,
                targetSets = group.targetSets,
                level = levelOf(
                    (groupScore(effective, days, group.targetSets) * 100f).roundToInt(),
                ),
            )
        }

        val weeklySets = entries.sumOf { it.second.sets }
        val skillRows = SkillFamily.entries.mapNotNull { family ->
            val rows = entries.filter { (_, entry) -> family.matches(entry.name) }
            if (rows.isEmpty()) return@mapNotNull null
            val weeklySetsForSkill = rows.sumOf { it.second.sets }
            val days = rows.map { it.first.planDay.dayOfWeek }.distinct().size
            val dayRatio = min(1f, days / family.targetDays.toFloat())
            val setRatio = min(1f, weeklySetsForSkill / family.targetSets.toFloat())
            val ratio = dayRatio * 0.65f + setRatio * 0.35f
            val onTrack = days >= family.targetDays && weeklySetsForSkill >= family.targetSets
            SkillProgress(
                key = family.key,
                skill = family.label,
                exercises = rows.map { it.second.name }.distinct().take(4),
                weeklySets = weeklySetsForSkill,
                daysPerWeek = days,
                targetDays = family.targetDays,
                targetSets = family.targetSets,
                onTrack = onTrack,
                verdict = when {
                    onTrack -> "Enough frequency"
                    ratio >= 0.65f -> "Almost there"
                    else -> "Too little practice"
                },
                timeline = when {
                    ratio >= 0.95f -> "Six months is realistic."
                    ratio >= 0.65f -> "Possible, but 6–9 months is more likely."
                    ratio >= 0.3f -> "More like 9–12 months at this frequency."
                    else -> "Not a six-month track yet."
                },
                level = levelOf((ratio * 100f).roundToInt()),
            )
        }

        val skillScore = skillScore(skillRows)
        val hypertrophyScore = hypertrophyScore(groups, weeklySets, trainingOnly.size)
        val balanceScore = balanceScore(groups, weeklySets)
        val jointScore = jointScore(entries, skillRows, groups)

        val warnings = buildWarnings(
            entries = entries,
            groups = groups,
            skillRows = skillRows,
            weeklySets = weeklySets,
            trainingDays = practiced.size,
            totalDays = plan.days.size,
        )

        val hypertrophy = hypertrophyCard(
            score = hypertrophyScore,
            groups = groups,
            trainingDays = practiced.size,
            weeklySets = weeklySets,
            entries = entries,
        )
        val skills = skillCard(score = skillScore, rows = skillRows, entries = entries)
        val balance = balanceCard(score = balanceScore, groups = groups, weeklySets = weeklySets)
        val joints = jointCard(score = jointScore, entries = entries, warnings = warnings)
        val overall = overallCard(
            hypertrophy = hypertrophy,
            skills = skills,
            balance = balance,
            joints = joints,
            trainingDays = practiced.size,
            weeklySets = weeklySets,
        )

        val add = buildAdditions(groups, skillRows, entries, warnings)
        val remove = buildRemovals(groups, weeklySets, trainingOnly.size)

        return PlanAnalysis(
            overall = overall,
            hypertrophy = hypertrophy,
            skills = skills,
            balance = balance,
            joints = joints,
            cards = listOf(hypertrophy, skills, balance, joints),
            muscleGroups = groups,
            skillRows = skillRows,
            warnings = warnings,
            add = add,
            remove = remove,
            trainingDays = practiced.size,
            weeklySets = weeklySets,
            estimatedWeeklyMinutes = estimatedMinutes(entries),
        )
    }

    // ----- Hypertrophy ---------------------------------------------------------

    private fun hypertrophyScore(
        groups: List<MuscleGroupVolume>,
        weeklySets: Int,
        trainingDays: Int,
    ): Int {
        if (groups.all { it.weeklySets == 0 }) return 0

        val groupScores = groups.map { group ->
            groupScore(group.effectiveSets, group.daysPerWeek, group.targetSets)
        }
        val coverage = groups.sumOf { group ->
            min(1.0, group.effectiveSets / (group.targetSets * 0.8)).toDouble()
        }.toFloat() / groups.size
        val average = groupScores.average().toFloat()

        var score = (average * 0.7f + coverage * 0.3f) * 100f
        if (weeklySets > 90) score -= 12f
        if (weeklySets > 120) score -= 10f
        if (trainingDays == 0) score = 0f
        return score.roundToInt().coerceIn(0, 100)
    }

    private fun groupScore(effectiveSets: Int, days: Int, target: Int): Float {
        if (effectiveSets <= 0) return 0f
        val ratio = effectiveSets / target.toFloat()
        val volume = when {
            ratio >= 1.6f -> (1f - (ratio - 1.6f) * 0.35f).coerceAtLeast(0.45f)
            ratio >= 1f -> 1f
            else -> ratio
        }
        val frequency = when {
            days <= 0 -> 0f
            days == 1 -> 0.55f
            days <= 3 -> 1f
            days == 4 -> 0.85f
            else -> 0.7f
        }
        return volume * 0.75f + frequency * 0.25f
    }

    private fun hypertrophyCard(
        score: Int,
        groups: List<MuscleGroupVolume>,
        trainingDays: Int,
        weeklySets: Int,
        entries: List<Pair<DaySnapshot, PlanEntryRow>>,
    ): AnalysisCard {
        val level = levelOf(score)
        val active = groups.filter { it.weeklySets > 0 }
        val headline = when {
            trainingDays == 0 -> "No training day is marked, so there is nothing to grow from."
            active.isEmpty() -> "No strength work found. Skill holds alone do not build much muscle."
            level == AnalysisLevel.STRONG -> "Weekly volume and frequency sit in the growth window."
            level == AnalysisLevel.SOLID -> "You will grow, but one or two groups are under-dosed."
            level == AnalysisLevel.BUILDING -> "There is a stimulus, but not yet enough for steady growth."
            else -> "This plan does not create a reliable hypertrophy stimulus yet."
        }
        val detail = "$trainingDays training ${if (trainingDays == 1) "day" else "days"} · " +
            "$weeklySets sets per week. Target: 10–20 effective sets per muscle group across " +
            "two or three sessions, with 5–30 reps close to failure."

        val bullets = mutableListOf<String>()
        groups.filter { it.weeklySets > 0 && it.effectiveSets < it.targetSets }.forEach { group ->
            val missing = group.targetSets - group.effectiveSets
            bullets += "${group.group}: ${group.effectiveSets} of ${group.targetSets} effective " +
                "sets. Add $missing sets."
        }
        groups.filter { it.weeklySets > 0 && it.daysPerWeek == 1 }.forEach { group ->
            bullets += "${group.group} is trained once a week. Twice a week grows more per set."
        }
        groups.filter { it.effectiveSets >= it.targetSets * 1.6f }.forEach { group ->
            bullets += "${group.group} is above the growth window (${group.effectiveSets} " +
                "effective sets). Trim ${group.effectiveSets - group.targetSets} sets."
        }
        if (groups.firstOrNull { it.group == "Legs" }?.weeklySets == 0) {
            bullets += "No direct leg work. Push/pull-only plans cap total growth and durability."
        }
        if (trainingDays == 0) {
            bullets += "No training day is marked, so there is nothing to analyze yet."
        }
        if (entries.isNotEmpty() && entries.groupBy { it.first.planDay.dayOfWeek }.values.any { it.size == 1 }) {
            bullets += "A day with a single exercise blocks the circuit; spread work over 3–6 movements."
        }
        if (level == AnalysisLevel.STRONG) {
            bullets += "Add reps or load weekly. Keep 1–2 reps in reserve on most sets."
        }

        val metrics = groups.map { group ->
            AnalysisMetric(
                label = group.group,
                value = if (group.weeklySets == 0) {
                    "none"
                } else {
                    "${group.effectiveSets}/${group.targetSets} · ${group.daysPerWeek}×/wk"
                },
                fraction = if (group.targetSets == 0) 0f else {
                    (group.effectiveSets / group.targetSets.toFloat()).coerceIn(0f, 1.4f) / 1.4f
                },
                level = group.level,
            )
        }

        return AnalysisCard(
            key = "hypertrophy",
            title = "Hypertrophy",
            score = score,
            level = level,
            headline = headline,
            detail = detail,
            metrics = metrics,
            bullets = bullets.distinct().take(5),
        )
    }

    // ----- Skill acquisition ---------------------------------------------------

    private fun skillScore(rows: List<SkillProgress>): Int {
        if (rows.isEmpty()) return 0
        val handstand = rows.firstOrNull { it.key == SkillFamily.HANDSTAND.key }
        val planche = rows.firstOrNull { it.key == SkillFamily.PLANCHE.key }
        val others = rows.filter { it.key != SkillFamily.HANDSTAND.key && it.key != SkillFamily.PLANCHE.key }

        val handstandRatio = handstand?.ratio() ?: 0f
        val plancheRatio = planche?.ratio() ?: 0f
        val core = handstandRatio * 0.55f + plancheRatio * 0.45f
        val other = if (others.isEmpty()) core else others.map { it.ratio() }.average().toFloat()
        var score = (core * 0.8f + other * 0.2f) * 100f

        // The two named goals are the spine of the six-month plan; one of them cannot
        // substitute for the other.
        if (handstand == null) score = min(score, 55f)
        if (planche == null) score = min(score, 68f)
        return score.roundToInt().coerceIn(0, 100)
    }

    private fun SkillProgress.ratio(): Float =
        (min(1f, daysPerWeek / targetDays.toFloat()) * 0.65f +
            min(1f, weeklySets / targetSets.toFloat()) * 0.35f)

    private fun skillCard(
        score: Int,
        rows: List<SkillProgress>,
        entries: List<Pair<DaySnapshot, PlanEntryRow>>,
    ): AnalysisCard {
        val level = levelOf(score)
        val handstand = rows.firstOrNull { it.key == SkillFamily.HANDSTAND.key }
        val planche = rows.firstOrNull { it.key == SkillFamily.PLANCHE.key }
        val headline = when {
            rows.isEmpty() -> "No handstand, planche or lever work. Six months has nothing to attach to."
            level == AnalysisLevel.STRONG -> "Handstand and planche are trained often enough to progress."
            level == AnalysisLevel.SOLID -> "Close. Consistency in the missing skill decides the timeline."
            handstand == null -> "Planche work without a handstand base is a slow, risky route."
            planche == null -> "The handstand is covered, but planche is missing from the plan."
            else -> "Skill frequency is too low for a six-month deadline."
        }
        val detail = if (rows.isEmpty()) {
            "Skills need their own slots in the week: practice them fresh, before the strength " +
                "blocks, and stop 1–2 reps before failure."
        } else {
            rows.joinToString(" · ") { "${it.skill} ${it.daysPerWeek}×/week" } +
                ". Handstand target: 3 days / 8 sets. Planche target: 2 days / 6 sets."
        }

        val bullets = mutableListOf<String>()
        rows.filter { !it.onTrack }.forEach { row ->
            bullets += "${row.skill}: ${row.daysPerWeek} of ${row.targetDays} sessions and " +
                "${row.weeklySets} of ${row.targetSets} sets. ${row.timeline}"
        }
        if (handstand != null && planche != null) {
            bullets += "Alternate the emphasis: handstand on fresh days, planche after a full warm-up."
        }
        if (rows.any { it.key == SkillFamily.PLANCHE.key } &&
            entries.none { (_, entry) -> entry.name.contains("wrist", ignoreCase = true) }
        ) {
            bullets += "Planche loads the wrists hard. Add 2×30 s wrist prep before every planche day."
        }
        if (entries.any { (_, entry) -> entry.name.contains("planche", ignoreCase = true) } &&
            entries.none { (_, entry) -> entry.category == ExerciseCategory.SKILL }
        ) {
            bullets += "Planche work is filed outside the Skill category. Name it \"Planche …\" so it leads the circuit."
        }

        val metrics = rows.map { row ->
            AnalysisMetric(
                label = row.skill,
                value = if (row.exercises.isEmpty()) {
                    "${row.daysPerWeek}×/wk · ${row.weeklySets} sets"
                } else {
                    "${row.daysPerWeek}×/wk · ${row.weeklySets} sets · ${row.exercises.first()}"
                },
                fraction = row.ratio().coerceIn(0f, 1f),
                level = row.level,
            )
        }

        return AnalysisCard(
            key = "skills",
            title = "Skill progression",
            score = score,
            level = level,
            headline = headline,
            detail = detail,
            metrics = metrics,
            bullets = bullets.distinct().take(5),
        )
    }

    // ----- Balance -------------------------------------------------------------

    private fun balanceScore(groups: List<MuscleGroupVolume>, weeklySets: Int): Int {
        if (weeklySets == 0) return 0
        fun sets(name: String) = groups.firstOrNull { it.group == name }?.effectiveSets ?: 0
        val push = sets("Push")
        val pull = sets("Pull")
        val core = sets("Core")
        val legs = sets("Legs")

        var score = 100f
        if (push > 0 && pull > 0) {
            val ratio = push / pull.toFloat()
            when {
                ratio > 1.5f -> score -= min(42f, (ratio - 1.5f) * 30f)
                ratio < 0.67f -> score -= min(42f, ((1f / ratio) - 1.5f) * 30f)
            }
        } else if (push + pull > 0) {
            score -= 35f
        }
        if (core < 6) score -= min(25f, (6 - core) * 5f)
        if (legs == 0) score -= 10f
        if (weeklySets > 90) score -= 10f
        return score.roundToInt().coerceIn(0, 100)
    }

    private fun balanceCard(
        score: Int,
        groups: List<MuscleGroupVolume>,
        weeklySets: Int,
    ): AnalysisCard {
        fun effective(name: String) = groups.firstOrNull { it.group == name }?.effectiveSets ?: 0
        val push = effective("Push")
        val pull = effective("Pull")
        val core = effective("Core")
        val legs = effective("Legs")
        val ratio = if (pull == 0) null else push / pull.toFloat()
        val level = levelOf(score)
        val headline = when {
            push + pull == 0 -> "There is no push or pull work to balance."
            push == 0 -> "Pulling dominates: no direct pressing volume."
            pull == 0 -> "Pressing dominates: no direct pulling volume."
            ratio != null && ratio > 1.5f -> "Push volume is ${oneDecimal(ratio)}× pull — the back is falling behind."
            ratio != null && ratio < 0.67f -> "Pull volume is ${oneDecimal(1f / ratio)}× push — the chest and shoulders are under-loaded."
            core < 6 -> "Push and pull are balanced, but the core is under-trained."
            legs == 0 -> "Upper body is balanced; legs are missing entirely."
            level == AnalysisLevel.STRONG -> "Push, pull, core and legs are in proportion."
            else -> "The balance is workable, with one or two gaps."
        }
        val detail = "Push $push · Pull $pull · Core $core · Legs $legs effective sets per week. " +
            "Aim for a push:pull ratio between 0.8 and 1.3, at least 6 core sets and some leg work. " +
            "Total load this week: $weeklySets sets."

        val bullets = mutableListOf<String>()
        if (push > 0 && pull > 0) {
            val value = push / pull.toFloat()
            if (value > 1.5f) {
                bullets += "Add ${((push / 1.2f) - pull).roundToInt().coerceAtLeast(2)} pull sets " +
                    "(chin-ups, rows, front-lever pulls) and keep pushing where it is."
            } else if (value < 0.67f) {
                bullets += "Add ${((pull / 1.2f) - push).roundToInt().coerceAtLeast(2)} push sets " +
                    "(dips, pseudo planche push-ups, HSPU)."
            }
        } else if (push == 0 && pull > 0) {
            bullets += "Add 8–12 weekly push sets. Pull-only plans leave the shoulders without a counter-load."
        } else if (pull == 0 && push > 0) {
            bullets += "Add 8–12 weekly pull sets. Pressing without pulling is the fastest route to shoulder pain."
        }
        if (core < 6) bullets += "Add 3× hollow-body hold or hanging leg raise twice a week."
        if (legs == 0) bullets += "Add one leg day: squats, split squats, calf raises and a hinge."
        if (weeklySets > 90) bullets += "Total volume is high. Cut the least specific accessory work first."

        val metrics = listOf(
            AnalysisMetric("Push", "$push sets", (push / 16f).coerceIn(0f, 1f)),
            AnalysisMetric("Pull", "$pull sets", (pull / 16f).coerceIn(0f, 1f)),
            AnalysisMetric(
                "Push : Pull",
                ratio?.let { "${oneDecimal(it)} : 1" } ?: "—",
                ratio?.let { (it / 2f).coerceIn(0f, 1f) },
            ),
            AnalysisMetric("Core", "$core sets", (core / 10f).coerceIn(0f, 1f)),
            AnalysisMetric("Legs", "$legs sets", (legs / 12f).coerceIn(0f, 1f)),
        )

        return AnalysisCard(
            key = "balance",
            title = "Push / pull balance",
            score = score,
            level = level,
            headline = headline,
            detail = detail,
            metrics = metrics,
            bullets = bullets.distinct().take(4),
        )
    }

    // ----- Joint health --------------------------------------------------------

    private fun jointScore(
        entries: List<Pair<DaySnapshot, PlanEntryRow>>,
        skillRows: List<SkillProgress>,
        groups: List<MuscleGroupVolume>,
    ): Int {
        if (entries.isEmpty()) return 0
        val names = entries.map { it.second.name.lowercase() }
        val wristPrep = names.any { name ->
            listOf("wrist", "mobility", "prep", "warm up", "warmup").any { it in name }
        }
        val elbowPrep = names.any { name ->
            listOf("elbow", "scapula", "prep", "warm up", "warmup", "rotator").any { it in name }
        }
        val handstand = skillRows.firstOrNull { it.key == SkillFamily.HANDSTAND.key }
        val planche = skillRows.firstOrNull { it.key == SkillFamily.PLANCHE.key }
        val leverSets = skillRows.filter {
            it.key == SkillFamily.FRONT_LEVER.key || it.key == SkillFamily.BACK_LEVER.key
        }.sumOf { it.weeklySets }
        val pull = groups.firstOrNull { it.group == "Pull" }?.effectiveSets ?: 0

        var score = 100
        if (!wristPrep && (handstand != null || planche != null)) score -= 20
        if (!elbowPrep && leverSets >= 6) score -= 15
        if (!elbowPrep && leverSets >= 6 && pull >= 14) score -= 10
        if ((handstand?.weeklySets ?: 0) >= 12) score -= 12
        if ((planche?.weeklySets ?: 0) >= 10) score -= 10
        if (entries.map { it.first.planDay.dayOfWeek }.distinct().size >= 6) score -= 8
        return score.coerceIn(35, 100)
    }

    private fun jointCard(
        score: Int,
        entries: List<Pair<DaySnapshot, PlanEntryRow>>,
        warnings: List<PlanWarning>,
    ): AnalysisCard {
        val level = levelOf(score)
        val names = entries.map { it.second.name.lowercase() }
        val wristPrep = names.any { name ->
            listOf("wrist", "mobility", "prep", "warm up", "warmup").any { it in name }
        }
        val elbowPrep = names.any { name ->
            listOf("elbow", "scapula", "prep", "warm up", "warmup", "rotator").any { it in name }
        }
        val headline = when {
            entries.isEmpty() -> "No work to load the joints with."
            !wristPrep -> "Straight-arm work is planned without wrist preparation."
            !elbowPrep -> "Straight-arm and pulling volume are planned without elbow preparation."
            level == AnalysisLevel.STRONG -> "Wrist, elbow and shoulder load look sustainable."
            else -> "Joint load is high relative to the preparation in the plan."
        }
        val detail = "Handstand and planche load the wrists; levers and straight-arm work load the " +
            "elbows. Two minutes of preparation before each skill day is cheap insurance."

        val bullets = mutableListOf<String>()
        if (!wristPrep && entries.isNotEmpty()) bullets += "Add 2×30 s wrist circles, palm and finger stretches before skill work."
        if (!elbowPrep && entries.isNotEmpty()) bullets += "Add 2×15 scapular push-ups and light elbow extensions before levers."
        bullets += warnings.filter { it.level == AnalysisLevel.LOW }.take(2).map { it.fix }

        return AnalysisCard(
            key = "joints",
            title = "Joint health",
            score = score,
            level = level,
            headline = headline,
            detail = detail,
            metrics = listOf(
                AnalysisMetric("Wrist prep", if (wristPrep) "in plan" else "missing", if (wristPrep) 1f else 0f),
                AnalysisMetric("Elbow prep", if (elbowPrep) "in plan" else "missing", if (elbowPrep) 1f else 0f),
            ),
            bullets = bullets.distinct().take(3),
        )
    }

    // ----- Warnings and actions ------------------------------------------------

    private fun buildWarnings(
        entries: List<Pair<DaySnapshot, PlanEntryRow>>,
        groups: List<MuscleGroupVolume>,
        skillRows: List<SkillProgress>,
        weeklySets: Int,
        trainingDays: Int,
        totalDays: Int,
    ): List<PlanWarning> {
        val warnings = mutableListOf<PlanWarning>()

        if (trainingDays == 0) {
            warnings += PlanWarning(
                title = "No training day",
                detail = "Every day is Rest or unmarked, so there is nothing to analyze.",
                fix = "Mark at least two days as Training in Edit plan.",
                level = AnalysisLevel.LOW,
            )
        }
        if (trainingDays > 0 && trainingDays == totalDays && totalDays >= 7) {
            warnings += PlanWarning(
                title = "No rest day",
                detail = "Seven loaded days leave no window for adaptation.",
                fix = "Make at least one day Rest; recovery is when the strength is built.",
                level = AnalysisLevel.LOW,
            )
        }

        val push = groups.firstOrNull { it.group == "Push" }
        val pull = groups.firstOrNull { it.group == "Pull" }
        if (push != null && pull != null && push.weeklySets > 0 && pull.weeklySets > 0) {
            val ratio = push.effectiveSets / pull.effectiveSets.toFloat().coerceAtLeast(1f)
            if (ratio > 1.5f || ratio < 0.67f) {
                warnings += PlanWarning(
                    title = "Push / pull imbalance",
                    detail = "Push ${push.effectiveSets} vs pull ${pull.effectiveSets} effective sets. " +
                        "A ratio outside 0.8–1.3 shifts the shoulders forward over time.",
                    fix = if (ratio > 1.5f) {
                        "Add 4–6 weekly pull sets; keep pushing as it is."
                    } else {
                        "Add 4–6 weekly push sets; keep pulling as it is."
                    },
                    level = AnalysisLevel.BUILDING,
                )
            }
        } else if (trainingDays > 0 && push != null && pull != null &&
            (push.weeklySets == 0 || pull.weeklySets == 0)
        ) {
            warnings += PlanWarning(
                title = "Missing movement pattern",
                detail = if (push.weeklySets == 0) "No direct pressing work." else "No direct pulling work.",
                fix = "Add two or three exercises for the missing pattern before adding volume elsewhere.",
                level = AnalysisLevel.LOW,
            )
        }

        groups.filter { it.weeklySets > 0 && it.effectiveSets >= it.targetSets * 1.6f }.forEach { group ->
            warnings += PlanWarning(
                title = "${group.group} volume is high",
                detail = "${group.effectiveSets} effective sets against a ${group.targetSets}-set " +
                    "target. Extra sets stop paying and start eating recovery.",
                fix = "Remove ${(group.effectiveSets - group.targetSets).coerceAtLeast(2)} ${group.group.lowercase()} sets.",
                level = AnalysisLevel.BUILDING,
            )
        }
        if (weeklySets > 100) {
            warnings += PlanWarning(
                title = "Overtraining risk",
                detail = "$weeklySets weekly working sets is a lot for natural recovery, " +
                    "especially with skill work on top.",
                fix = "Trim accessories so the weekly total lands under 90 sets.",
                level = AnalysisLevel.LOW,
            )
        }

        val handstand = skillRows.firstOrNull { it.key == SkillFamily.HANDSTAND.key }
        val planche = skillRows.firstOrNull { it.key == SkillFamily.PLANCHE.key }
        if (handstand == null) {
            warnings += PlanWarning(
                title = "Handstand is missing",
                detail = "The handstand is the balance base for press work and the entry point to planche.",
                fix = "Add 3×20–40 s wall handstand on three days, before the strength block.",
                level = AnalysisLevel.BUILDING,
            )
        } else if (!handstand.onTrack) {
            warnings += PlanWarning(
                title = "Handstand frequency is low",
                detail = "${handstand.daysPerWeek} sessions and ${handstand.weeklySets} sets per " +
                    "week against a 3-day / 8-set target.",
                fix = "Add ${(handstand.targetDays - handstand.daysPerWeek).coerceAtLeast(1)} short " +
                    "handstand sessions; quality beats duration.",
                level = AnalysisLevel.BUILDING,
            )
        }
        if (planche == null) {
            warnings += PlanWarning(
                title = "Planche is missing",
                detail = "No tuck planche, planche lean or pseudo planche work was found.",
                fix = "Add 2×3 tuck planche leans twice a week and keep the wrists prepared.",
                level = AnalysisLevel.BUILDING,
            )
        } else if (!planche.onTrack) {
            warnings += PlanWarning(
                title = "Planche practice is thin",
                detail = "${planche.daysPerWeek} sessions and ${planche.weeklySets} sets per week " +
                    "against a 2-day / 6-set target.",
                fix = "Add one planche day, or two more sets on the existing day.",
                level = AnalysisLevel.BUILDING,
            )
        }

        val names = entries.map { it.second.name.lowercase() }
        val wristPrep = names.any { name ->
            listOf("wrist", "mobility", "prep", "warm up", "warmup").any { it in name }
        }
        if (!wristPrep && (handstand != null || planche != null)) {
            warnings += PlanWarning(
                title = "Wrists are unprepared",
                detail = "Handstand and planche load the wrist in extension every week without a " +
                    "preparation slot in the plan.",
                fix = "Add a 2-minute wrist prep block to every skill day.",
                level = AnalysisLevel.BUILDING,
            )
        }
        val leverSets = skillRows.filter {
            it.key == SkillFamily.FRONT_LEVER.key || it.key == SkillFamily.BACK_LEVER.key
        }.sumOf { it.weeklySets }
        val elbowPrep = names.any { name ->
            listOf("elbow", "scapula", "prep", "warm up", "warmup", "rotator").any { it in name }
        }
        if (!elbowPrep && leverSets >= 6) {
            warnings += PlanWarning(
                title = "Elbows take straight-arm load",
                detail = "$leverSets lever sets per week with no elbow or scapular preparation.",
                fix = "Add scapular push-ups, light biceps curls and elbow extensions before levers.",
                level = AnalysisLevel.BUILDING,
            )
        }

        val core = groups.firstOrNull { it.group == "Core" }
        if (core != null && core.weeklySets in 1..5) {
            warnings += PlanWarning(
                title = "Core is under-trained",
                detail = "${core.weeklySets} core sets per week is below the 6–12 range that " +
                    "carries handstands, levers and planche.",
                fix = "Add hollow-body holds and hanging leg raises twice a week.",
                level = AnalysisLevel.BUILDING,
            )
        }
        val legs = groups.firstOrNull { it.group == "Legs" }
        if (legs != null && legs.weeklySets == 0 && trainingDays > 0) {
            warnings += PlanWarning(
                title = "No leg work",
                detail = "A calisthenics plan can skip barbells, not legs. Balance and tendon " +
                    "health need loaded patterns too.",
                fix = "Add one leg block: squats, split squats, calf raises and a hinge.",
                level = AnalysisLevel.SOLID,
            )
        }

        return warnings
            .distinctBy { it.title }
            // LOW (danger) first, then BUILDING, then informational SOLID.
            .sortedByDescending { it.level.ordinal }
            .take(7)
    }

    private fun buildAdditions(
        groups: List<MuscleGroupVolume>,
        skillRows: List<SkillProgress>,
        entries: List<Pair<DaySnapshot, PlanEntryRow>>,
        warnings: List<PlanWarning>,
    ): List<String> {
        val add = mutableListOf<String>()
        groups.filter { it.weeklySets > 0 && it.effectiveSets < it.targetSets }.forEach { group ->
            add += "${group.group}: add ${group.targetSets - group.effectiveSets} effective sets " +
                "across ${if (group.daysPerWeek < 2) "two days" else "the week"}."
        }
        skillRows.filter { !it.onTrack }.forEach { row ->
            add += "${row.skill}: add ${(row.targetDays - row.daysPerWeek).coerceAtLeast(1)} " +
                "session(s) or ${(row.targetSets - row.weeklySets).coerceAtLeast(2)} sets."
        }
        val names = entries.map { it.second.name.lowercase() }
        if (entries.isNotEmpty() && names.none { "wrist" in it || "mobility" in it }) {
            add += "Wrist prep: 2×30 s on every handstand and planche day."
        }
        if (entries.isNotEmpty() && names.none { "elbow" in it || "scapula" in it }) {
            add += "Elbow prep: 2×15 scapular push-ups before straight-arm work."
        }
        warnings.filter { it.level == AnalysisLevel.LOW }.forEach { warning ->
            add += warning.fix
        }
        if (add.isEmpty()) {
            add += "Nothing to add. Keep the current volume and progress one variable per week."
        }
        return add.distinct().take(5)
    }

    private fun buildRemovals(
        groups: List<MuscleGroupVolume>,
        weeklySets: Int,
        trainingDays: Int,
    ): List<String> {
        val remove = mutableListOf<String>()
        groups.filter { it.effectiveSets >= it.targetSets * 1.6f }.forEach { group ->
            remove += "${group.group}: remove ${(group.effectiveSets - group.targetSets).coerceAtLeast(2)} sets."
        }
        if (weeklySets > 90) {
            remove += "Total week: cut ${weeklySets - 85} sets, starting with the least specific accessory."
        }
        if (trainingDays >= 7) {
            remove += "Remove one loaded day; recovery is part of the program."
        }
        if (remove.isEmpty()) {
            remove += "Nothing needs to go. Add volume only when the current work feels easy."
        }
        return remove.distinct().take(4)
    }

    // ----- Overall -------------------------------------------------------------

    private fun overallCard(
        hypertrophy: AnalysisCard,
        skills: AnalysisCard,
        balance: AnalysisCard,
        joints: AnalysisCard,
        trainingDays: Int,
        weeklySets: Int,
    ): AnalysisCard {
        val score = (
            hypertrophy.score * 0.35f +
                skills.score * 0.35f +
                balance.score * 0.15f +
                joints.score * 0.15f
            ).roundToInt().coerceIn(0, 100)
        val level = levelOf(score)
        val headline = when {
            trainingDays == 0 -> "Build a week first; the coach needs real sets to read."
            level == AnalysisLevel.STRONG -> "A plan that can actually carry six months."
            level == AnalysisLevel.SOLID -> "Solid base. Two or three fixes make it strong."
            level == AnalysisLevel.BUILDING -> "The outline is there; the details are not yet."
            else -> "This plan is not ready to carry a six-month goal."
        }
        val detail = "$trainingDays training ${if (trainingDays == 1) "day" else "days"} · " +
            "$weeklySets weekly sets. Hypertrophy and skill work carry the most weight in this " +
            "score; balance and joint health catch the expensive mistakes."

        val bullets = mutableListOf<String>()
        if (hypertrophy.score < 60) bullets += "Fix volume first: it drives both muscle and skill strength."
        if (skills.score < 60) bullets += "Protect skill practice: put handstand and planche before fatigue."
        if (balance.score < 60) bullets += "Even out push and pull before adding anything new."
        if (joints.score < 60) bullets += "Add wrist and elbow preparation; it is the cheapest upgrade."
        if (bullets.isEmpty()) bullets += "Keep the structure and progress one variable each week."

        return AnalysisCard(
            key = "overall",
            title = "Overall",
            score = score,
            level = level,
            headline = headline,
            detail = detail,
            metrics = listOf(
                AnalysisMetric("Muscle", "${hypertrophy.grade}/10", hypertrophy.score / 100f, hypertrophy.level),
                AnalysisMetric("Skills", "${skills.grade}/10", skills.score / 100f, skills.level),
                AnalysisMetric("Balance", "${balance.grade}/10", balance.score / 100f, balance.level),
                AnalysisMetric("Joints", "${joints.grade}/10", joints.score / 100f, joints.level),
            ),
            bullets = bullets,
        )
    }

    // ----- Helpers -------------------------------------------------------------

    private fun oneDecimal(value: Float): String =
        String.format(Locale.US, "%.1f", value)

    private fun levelOf(score: Int): AnalysisLevel = when {
        score >= 80 -> AnalysisLevel.STRONG
        score >= 60 -> AnalysisLevel.SOLID
        score >= 35 -> AnalysisLevel.BUILDING
        else -> AnalysisLevel.LOW
    }

    private fun estimatedMinutes(entries: List<Pair<DaySnapshot, PlanEntryRow>>): Int {
        val seconds = entries.sumOf { (_, entry) ->
            val work = if (entry.unit == ExerciseUnit.SECONDS) entry.reps else entry.reps * 3
            entry.sets.toLong() * (work + entry.restSeconds).coerceAtLeast(20)
        }
        return (seconds / 60).toInt().coerceAtLeast(0)
    }

    private fun effectiveSets(entry: PlanEntryRow): Int = when {
        // Holds below 20 s carry less growth stimulus than a full-range set.
        entry.unit == ExerciseUnit.SECONDS -> if (entry.reps >= 20) entry.sets else (entry.sets + 1) / 2
        entry.reps in 5..30 -> entry.sets
        entry.reps in 3..4 -> (entry.sets * 3 + 2) / 5
        else -> (entry.sets + 2) / 3
    }

    /** Muscles that receive growth volume. Skill holds are scored separately. */
    private enum class MuscleGroup(val label: String, val targetSets: Int) {
        PUSH("Push", 12),
        PULL("Pull", 12),
        CORE("Core", 8),
        LEGS("Legs", 10),
        ;

        companion object {
            fun of(entry: PlanEntryRow): MuscleGroup? {
                return when (entry.category) {
                    ExerciseCategory.PUSH -> PUSH
                    ExerciseCategory.PULL -> PULL
                    ExerciseCategory.CORE -> CORE
                    ExerciseCategory.LEGS -> LEGS
                    ExerciseCategory.MOBILITY -> null
                    else -> byName(entry.name)
                }
            }

            /** Fallback for SKILL/OTHER entries whose category says nothing about muscles. */
            private fun byName(raw: String): MuscleGroup? {
                val name = raw.lowercase()
                return when {
                    LEGS_WORDS.any { it in name } -> LEGS
                    CORE_WORDS.any { it in name } -> CORE
                    PULL_WORDS.any { it in name } -> PULL
                    PUSH_WORDS.any { it in name } -> PUSH
                    else -> null
                }
            }

            private val PUSH_WORDS = listOf(
                "push", "dip", "hspu", "handstand", "planche", "pike", "bench", "press",
                "maltese", "protraction",
            )
            private val PULL_WORDS = listOf(
                "pull", "row", "chin", "curl", "lever", "muscle up", "flag",
            )
            private val CORE_WORDS = listOf(
                "hollow", "plank", "l sit", "lsit", "leg raise", "core", "dragon flag", "v sit",
            )
            private val LEGS_WORDS = listOf(
                "squat", "lunge", "calf", "pistol", "nordic", "hamstring", "glute", "bridge",
            )
        }
    }

    /** Skill families with the practice load needed for a realistic six-month timeline. */
    private enum class SkillFamily(
        val key: String,
        val label: String,
        val targetDays: Int,
        val targetSets: Int,
        private val words: List<String>,
    ) {
        HANDSTAND(
            "handstand", "Handstand", 3, 8,
            listOf("handstand", "handstand push", "hspu", "wall walk", "freestanding", "press to handstand"),
        ),
        PLANCHE(
            "planche", "Planche", 2, 6,
            listOf("planche", "planche lean", "tuck planche", "straddle planche", "pseudo planche", "maltese", "protraction"),
        ),
        FRONT_LEVER("front_lever", "Front lever", 2, 6, listOf("front lever")),
        BACK_LEVER("back_lever", "Back lever", 2, 4, listOf("back lever")),
        MUSCLE_UP("muscle_up", "Muscle-up", 2, 4, listOf("muscle up", "muscleup")),
        FLAG("flag", "Human flag", 2, 4, listOf("human flag")),
        ;

        fun matches(name: String): Boolean {
            val normalized = name.lowercase()
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()
                .replace(Regex(" +"), " ")
            return words.any { it in normalized }
        }
    }
}
