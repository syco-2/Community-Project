package com.sixmonths.app.domain

import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.data.model.PlanEntryRow
import com.sixmonths.app.data.model.PlanSnapshot

/** The four muscle lanes the coach caps and reports on. */
enum class MuscleLane { PUSH, PULL, CORE, LEGS }

enum class CoachTone { PRO, RISK }

/** One graded rule of the audit: a score out of ten, a verdict and the concrete fix. */
data class CoachRule(
    val id: String,
    val title: String,
    val grade: Int,
    val verdict: String,
    val advice: String,
    val passed: Boolean,
)

/** One line in the coach report: a strength or a risk with its fix. */
data class CoachPoint(
    val id: String,
    val title: String,
    val detail: String,
    val tone: CoachTone,
    val critical: Boolean = false,
)

data class CoachVerdict(
    val score: Int,
    val headline: String,
    val detail: String,
    val growth: String,
    val skills: String,
)

/**
 * The structured result of the elite calisthenics audit.
 *
 * [pros] are green-check findings, [risks] are orange/red warning findings and [verdict]
 * answers the only question that matters: will this week build muscle and skill in six months?
 */
data class CoachReport(
    val rules: List<CoachRule>,
    val pros: List<CoachPoint>,
    val risks: List<CoachPoint>,
    val verdict: CoachVerdict,
    val trainingDays: Int,
    val weeklySets: Int,
    val pushSets: Int,
    val pullSets: Int,
    val coreSets: Int,
    val legSets: Int,
    val skillDays: Int,
    val maxDailyPush: Int,
    val maxConsecutiveLoad: Int,
)

/**
 * Deterministic plan audit for an elite calisthenics coach.
 *
 * It checks the four rules that actually decide a six-month block:
 *  1. Skill freshness: static skills belong at the front of a session, never inside a circuit.
 *  2. Joint frequency: more than three consecutive push/skill days is a tendinitis risk.
 *  3. Daily volume: more than twelve push sets in one day is too much to recover from.
 *  4. Core and legs: core carries the skills; legs keep the body durable.
 *
 * Pure Kotlin, no I/O, no network. The same plan always produces the same report.
 */
object CoachAudit {

    const val DAILY_MUSCLE_CAP: Int = 12
    const val SKILL_REST_SECONDS: Int = 180
    const val MIN_SKILL_REST_SECONDS: Int = 120

    const val SKILL_CIRCUIT_WARNING: String =
        "Skills require maximum nervous system freshness. Do not train them in a circuit. " +
            "Train them first with 2-3 min rest."

    const val MASSIVE_PUSH_WARNING: String =
        "Massive push volume. Consider reducing sets or varying exercises week by week."

    fun audit(plan: PlanSnapshot): CoachReport {
        val activeDays = plan.days.filter { day ->
            day.planDay.state != DayState.REST && day.entries.isNotEmpty()
        }
        val trainingDays = plan.days.count { it.planDay.state == DayState.TRAINING }
        val weeklySets = activeDays.sumOf { it.totalSets }

        val pushSets = activeDays.sumOf { day -> day.pushSets() }
        val pullSets = activeDays.sumOf { day -> day.pullSets() }
        val coreSets = activeDays.sumOf { day -> day.coreSets() }
        val legSets = activeDays.sumOf { day -> day.legSets() }
        val skillDays = activeDays.count { day -> day.entries.any(::isStaticSkill) }

        val pushByDay = plan.days.associate { day ->
            day.planDay.dayOfWeek to day.pushSets()
        }
        val maxDailyPush = pushByDay.values.maxOrNull() ?: 0
        val maxConsecutiveLoad = longestPushSkillRun(plan)

        // A skill is "in the circuit" only while normal work comes before a skill set. Once
        // the whole skill block leads the day with long rest, it is isolated, not a circuit.
        val skillCircuitDays = activeDays.filter { day ->
            val skillRows = day.entries.filter(::isStaticSkill)
            if (skillRows.isEmpty()) return@filter false
            val lastSkillOrder = skillRows.maxOf { it.sortOrder }
            day.entries
                .filterNot(::isStaticSkill)
                .any { it.sortOrder < lastSkillOrder }
        }
        val shortSkillRestDays = activeDays.filter { day ->
            day.entries.any { isStaticSkill(it) && it.restSeconds < MIN_SKILL_REST_SECONDS }
        }
        val allSevenTraining = plan.days.size == 7 &&
            plan.days.all { it.planDay.state == DayState.TRAINING }

        val pros = buildPros(
            activeDays = activeDays,
            pushSets = pushSets,
            pullSets = pullSets,
            coreSets = coreSets,
            legSets = legSets,
            skillDays = skillDays,
            maxDailyPush = maxDailyPush,
            maxConsecutiveLoad = maxConsecutiveLoad,
            skillCircuitDays = skillCircuitDays,
            shortSkillRestDays = shortSkillRestDays,
        )

        val risks = buildRisks(
            activeDays = activeDays,
            trainingDays = trainingDays,
            pushSets = pushSets,
            pullSets = pullSets,
            coreSets = coreSets,
            legSets = legSets,
            skillDays = skillDays,
            maxDailyPush = maxDailyPush,
            maxConsecutiveLoad = maxConsecutiveLoad,
            skillCircuitDays = skillCircuitDays,
            shortSkillRestDays = shortSkillRestDays,
            allSevenTraining = allSevenTraining,
        )

        val rules = buildRules(
            skillDays = skillDays,
            skillCircuitDays = skillCircuitDays,
            shortSkillRestDays = shortSkillRestDays,
            maxConsecutiveLoad = maxConsecutiveLoad,
            maxDailyPush = maxDailyPush,
            weeklySets = weeklySets,
            trainingDays = trainingDays,
        )

        val freshnessOk = skillCircuitDays.isEmpty() && shortSkillRestDays.isEmpty()
        val growthOk = maxDailyPush <= DAILY_MUSCLE_CAP &&
            pushSets >= 8 &&
            pullSets >= 8 &&
            coreSets >= 6
        val skillOk = skillDays >= 2 && freshnessOk
        val penalty = risks.fold(0) { total, point ->
            total + if (point.critical) 16 else 8
        }
        val score = if (trainingDays == 0) 10 else (100 - penalty).coerceIn(10, 100)

        val verdict = CoachVerdict(
            score = score,
            headline = when {
                trainingDays == 0 -> "Build a week first."
                score >= 85 -> "A real six-month plan."
                score >= 65 -> "Good base. Fix the red flags first."
                score >= 45 -> "Promising, but not a six-month plan yet."
                else -> "Rebuild the week before starting."
            },
            detail = when {
                trainingDays == 0 ->
                    "Mark at least two training days so the coach has real work to read."
                risks.isEmpty() ->
                    "Fresh skills, capped volume and enough recovery. This week can carry six months."
                else ->
                    "The plan has ${risks.size} fix${if (risks.size == 1) "" else "es"}. " +
                        "Apply them before the first session, not after the first ache."
            },
            growth = if (growthOk) {
                "Muscle growth: yes. Push and pull are inside the 8-20 set window with a " +
                    "12-set daily ceiling and enough core work."
            } else {
                "Muscle growth: limited. Volume, balance or core coverage is outside the " +
                    "range that reliably builds muscle."
            },
            skills = when {
                skillDays == 0 ->
                    "Skills: no handstand, planche or lever work, so six months has nothing to build."
                !freshnessOk ->
                    "Skills: at risk. Static work is buried in a circuit or rested too briefly."
                skillDays < 2 ->
                    "Skills: too little practice. One session per week will not move a handstand or planche."
                else ->
                    "Skills: yes. Frequent, fresh static practice makes a six-month handstand " +
                        "and planche realistic."
            },
        )

        return CoachReport(
            rules = rules,
            pros = pros,
            risks = risks,
            verdict = verdict,
            trainingDays = trainingDays,
            weeklySets = weeklySets,
            pushSets = pushSets,
            pullSets = pullSets,
            coreSets = coreSets,
            legSets = legSets,
            skillDays = skillDays,
            maxDailyPush = maxDailyPush,
            maxConsecutiveLoad = maxConsecutiveLoad,
        )
    }

    /** The three rules that decide a calisthenics week, each graded out of ten. */
    private fun buildRules(
        skillDays: Int,
        skillCircuitDays: List<DaySnapshot>,
        shortSkillRestDays: List<DaySnapshot>,
        maxConsecutiveLoad: Int,
        maxDailyPush: Int,
        weeklySets: Int,
        trainingDays: Int,
    ): List<CoachRule> {
        if (trainingDays == 0) {
            return listOf(
                CoachRule(
                    id = "skill-freshness",
                    title = "Skill freshness",
                    grade = 0,
                    verdict = "No training day to judge",
                    advice = "Mark at least two training days.",
                    passed = false,
                ),
                CoachRule(
                    id = "joint-frequency",
                    title = "Joint frequency",
                    grade = 0,
                    verdict = "No loading days",
                    advice = "Give the plan a rhythm first.",
                    passed = false,
                ),
                CoachRule(
                    id = "daily-volume",
                    title = "Daily volume",
                    grade = 0,
                    verdict = "No volume to cap",
                    advice = "Add the exercises you actually train.",
                    passed = false,
                ),
            )
        }

        val freshness = when {
            skillDays == 0 -> CoachRule(
                id = "skill-freshness",
                title = "Skill freshness",
                grade = 3,
                verdict = "No handstand or planche work",
                advice = "Train handstand and planche first, with 2–3 min rest, on their own days.",
                passed = false,
            )
            skillCircuitDays.isNotEmpty() -> CoachRule(
                id = "skill-freshness",
                title = "Skill freshness",
                grade = 2,
                verdict = "Skills are mixed into the circuit",
                advice = "Move every static skill to the start of the session, before any strength work.",
                passed = false,
            )
            shortSkillRestDays.isNotEmpty() -> CoachRule(
                id = "skill-freshness",
                title = "Skill freshness",
                grade = 5,
                verdict = "Skill rest is too short",
                advice = "Rest 2–3 min between skill sets so the nervous system recovers.",
                passed = false,
            )
            else -> CoachRule(
                id = "skill-freshness",
                title = "Skill freshness",
                grade = 10,
                verdict = "Handstand and planche are trained fresh",
                advice = "Keep them first in the session. Quality beats duration.",
                passed = true,
            )
        }

        val joints = when {
            maxConsecutiveLoad > 5 -> CoachRule(
                id = "joint-frequency",
                title = "Joint frequency",
                grade = 2,
                verdict = "$maxConsecutiveLoad push days in a row",
                advice = "Insert a full rest day before the wrists and elbows complain.",
                passed = false,
            )
            maxConsecutiveLoad > 3 -> CoachRule(
                id = "joint-frequency",
                title = "Joint frequency",
                grade = 5,
                verdict = "$maxConsecutiveLoad push days in a row",
                advice = "Keep push and skill blocks to three days, then load a pull or rest day.",
                passed = false,
            )
            else -> CoachRule(
                id = "joint-frequency",
                title = "Joint frequency",
                grade = 10,
                verdict = "Loading blocks stay under four days",
                advice = "Recovery fits between push blocks. Keep it that way.",
                passed = true,
            )
        }

        val volume = when {
            maxDailyPush > 16 || weeklySets > 110 -> CoachRule(
                id = "daily-volume",
                title = "Daily volume",
                grade = 3,
                verdict = "$maxDailyPush push sets on the biggest day",
                advice = "Cap push at 12 sets per day and trim the weekly total under 90.",
                passed = false,
            )
            maxDailyPush > DAILY_MUSCLE_CAP -> CoachRule(
                id = "daily-volume",
                title = "Daily volume",
                grade = 6,
                verdict = "$maxDailyPush push sets on the biggest day",
                advice = "Remove the extra push sets until the day sits at 12 or fewer.",
                passed = false,
            )
            else -> CoachRule(
                id = "daily-volume",
                title = "Daily volume",
                grade = 10,
                verdict = "Daily volume is capped at ${DAILY_MUSCLE_CAP} sets",
                advice = "Add quality reps or load instead of more sets.",
                passed = true,
            )
        }

        return listOf(freshness, joints, volume)
    }

    private fun buildPros(
        activeDays: List<DaySnapshot>,
        pushSets: Int,
        pullSets: Int,
        coreSets: Int,
        legSets: Int,
        skillDays: Int,
        maxDailyPush: Int,
        maxConsecutiveLoad: Int,
        skillCircuitDays: List<DaySnapshot>,
        shortSkillRestDays: List<DaySnapshot>,
    ): List<CoachPoint> {
        val pros = mutableListOf<CoachPoint>()

        if (skillDays > 0 && skillCircuitDays.isEmpty() && shortSkillRestDays.isEmpty()) {
            pros += CoachPoint(
                id = "skill-fresh",
                title = "Skills are trained fresh",
                detail = "Static skill work leads the session with at least 2-3 min rest. " +
                    "That is how handstand and planche actually progress.",
                tone = CoachTone.PRO,
            )
        }
        if (skillDays >= 3) {
            pros += CoachPoint(
                id = "skill-frequency",
                title = "Skill frequency is high",
                detail = "$skillDays skill days per week. Frequent short practice beats one long session.",
                tone = CoachTone.PRO,
            )
        }
        if (pushSets > 0 && maxDailyPush <= DAILY_MUSCLE_CAP) {
            pros += CoachPoint(
                id = "push-cap",
                title = "Push volume is sustainable",
                detail = "The biggest push day is $maxDailyPush sets, inside the 12-set ceiling. " +
                    "You can add quality reps before adding sets.",
                tone = CoachTone.PRO,
            )
        }
        if (maxConsecutiveLoad in 1..3) {
            pros += CoachPoint(
                id = "joint-recovery",
                title = "Joints get recovery",
                detail = "The longest push/skill run is $maxConsecutiveLoad days. " +
                    "That keeps elbow, wrist and shoulder tendinitis risk low.",
                tone = CoachTone.PRO,
            )
        }
        if (coreSets > 0) {
            pros += CoachPoint(
                id = "core-present",
                title = "Core is in the plan",
                detail = "$coreSets core sets per week. Hollow body and L-sit carry handstands, " +
                    "levers and planche.",
                tone = CoachTone.PRO,
            )
        }
        if (legSets > 0) {
            pros += CoachPoint(
                id = "legs-present",
                title = "Legs are covered",
                detail = "$legSets leg sets per week. Loaded legs keep the base durable, even in " +
                    "a bodyweight plan.",
                tone = CoachTone.PRO,
            )
        }
        if (pushSets > 0 && pullSets > 0 &&
            pushSets / pullSets.toFloat() in 0.67f..1.5f
        ) {
            pros += CoachPoint(
                id = "balance",
                title = "Push and pull are balanced",
                detail = "Push $pushSets vs pull $pullSets sets per week. The shoulders stay " +
                    "centred instead of drifting forward.",
                tone = CoachTone.PRO,
            )
        }
        if (activeDays.isEmpty()) {
            pros += CoachPoint(
                id = "empty-week",
                title = "A blank canvas",
                detail = "No exercises yet, so nothing is overloaded. Add training days in the plan.",
                tone = CoachTone.PRO,
            )
        }
        return pros.distinctBy { it.id }.take(6)
    }

    private fun buildRisks(
        activeDays: List<DaySnapshot>,
        trainingDays: Int,
        pushSets: Int,
        pullSets: Int,
        coreSets: Int,
        legSets: Int,
        skillDays: Int,
        maxDailyPush: Int,
        maxConsecutiveLoad: Int,
        skillCircuitDays: List<DaySnapshot>,
        shortSkillRestDays: List<DaySnapshot>,
        allSevenTraining: Boolean,
    ): List<CoachPoint> {
        val risks = mutableListOf<CoachPoint>()

        if (trainingDays == 0) {
            risks += CoachPoint(
                id = "no-training",
                title = "No training day",
                detail = "Every day is Rest or unmarked. Mark at least two days as Training.",
                tone = CoachTone.RISK,
                critical = true,
            )
        }

        if (skillCircuitDays.isNotEmpty()) {
            val days = skillCircuitDays.joinToString(", ") {
                com.sixmonths.app.core.model.Weekday.shortName(it.planDay.dayOfWeek)
            }
            risks += CoachPoint(
                id = "skill-freshness",
                title = "Skills are buried in the circuit ($days)",
                detail = SKILL_CIRCUIT_WARNING,
                tone = CoachTone.RISK,
                critical = true,
            )
        } else if (shortSkillRestDays.isNotEmpty()) {
            val shortest = shortSkillRestDays
                .flatMap { it.entries.filter(::isStaticSkill) }
                .minOfOrNull { it.restSeconds } ?: 0
            risks += CoachPoint(
                id = "skill-rest",
                title = "Skill rest is too short",
                detail = "Static skills are planned with ${shortest}s rest. " +
                    "Use 2-3 min between skill sets so the nervous system recovers.",
                tone = CoachTone.RISK,
                critical = true,
            )
        }
        if (skillDays == 0 && trainingDays > 0) {
            risks += CoachPoint(
                id = "no-skills",
                title = "No static skill work",
                detail = "No handstand, planche or lever work found. Add 2-3 short skill " +
                    "sessions at the start of a session, not at the end.",
                tone = CoachTone.RISK,
            )
        }

        if (maxConsecutiveLoad > 3) {
            risks += CoachPoint(
                id = "joint-frequency",
                title = "Push or skill on $maxConsecutiveLoad days in a row",
                detail = "High risk of elbow, wrist and shoulder tendinitis. " +
                    "Put at least one full rest day between loading blocks.",
                tone = CoachTone.RISK,
                critical = true,
            )
        }
        if (allSevenTraining) {
            risks += CoachPoint(
                id = "no-rest",
                title = "No rest day",
                detail = "Seven training days in a row. Muscles and tendons adapt during rest; " +
                    "turn your lowest-volume day into a full rest day.",
                tone = CoachTone.RISK,
                critical = true,
            )
        }
        if (maxDailyPush > DAILY_MUSCLE_CAP) {
            val day = activeDays.maxByOrNull { it.pushSets() }
            val name = day?.let {
                com.sixmonths.app.core.model.Weekday.fullName(it.planDay.dayOfWeek)
            } ?: "one day"
            risks += CoachPoint(
                id = "push-volume",
                title = "Massive push day ($name · $maxDailyPush sets)",
                detail = MASSIVE_PUSH_WARNING,
                tone = CoachTone.RISK,
                critical = true,
            )
        }
        if (pushSets > 0 && pullSets == 0) {
            risks += CoachPoint(
                id = "no-pull",
                title = "No pulling work",
                detail = "Pressing without pulling is the fastest route to shoulder pain. " +
                    "Add pull-ups, rows or front-lever pulls.",
                tone = CoachTone.RISK,
            )
        } else if (pullSets > 0 && pushSets / pullSets.toFloat() > 1.5f) {
            risks += CoachPoint(
                id = "push-pull-ratio",
                title = "Push is outrunning pull",
                detail = "Push $pushSets vs pull $pullSets sets per week. Add pulling until the " +
                    "ratio sits between 0.8 and 1.3.",
                tone = CoachTone.RISK,
            )
        }
        if (coreSets == 0 && trainingDays > 0) {
            risks += CoachPoint(
                id = "no-core",
                title = "Core is missing",
                detail = "Handstands, levers and planche all hang off the core. " +
                    "Add 6-10 hollow-body, L-sit or leg-raise sets per week.",
                tone = CoachTone.RISK,
            )
        }
        if (legSets == 0 && trainingDays > 0) {
            risks += CoachPoint(
                id = "no-legs",
                title = "Legs are missing",
                detail = "A calisthenics plan still needs legs. Add one block: squats, split " +
                    "squats, calf raises and a hinge.",
                tone = CoachTone.RISK,
            )
        }

        // Critical findings first, then warnings; deterministic within each group.
        return risks
            .distinctBy { it.id }
            .sortedByDescending { it.critical }
            .take(8)
    }

    /**
     * Longest circular run of days that load push or a static skill. A pull-only or rest day
     * breaks the run. With all seven days loaded the answer is seven.
     */
    private fun longestPushSkillRun(plan: PlanSnapshot): Int {
        val flags = (1..7).map { weekday ->
            val day = plan.days.firstOrNull { it.planDay.dayOfWeek == weekday }
            day != null &&
                day.planDay.state != DayState.REST &&
                day.entries.any { laneOf(it) == MuscleLane.PUSH || isStaticSkill(it) }
        }
        if (flags.all { it }) return 7
        if (flags.none { it }) return 0

        val start = flags.indexOfFirst { !it } + 1
        var run = 0
        var longest = 0
        for (offset in 0 until 7) {
            if (flags[(start + offset) % 7]) {
                run += 1
                longest = maxOf(longest, run)
            } else {
                run = 0
            }
        }
        return longest
    }

    // ----- Exercise classification ---------------------------------------------

    fun isStaticSkill(entry: PlanEntryRow): Boolean {
        if (entry.category == ExerciseCategory.SKILL) return true
        val name = normalize(entry.name)
        return STATIC_SKILL_WORDS.any { it in name }
    }

    /** The muscle lane an entry loads, or null for mobility and unclassifiable work. */
    fun laneOf(entry: PlanEntryRow): MuscleLane? = when (entry.category) {
        ExerciseCategory.PUSH -> MuscleLane.PUSH
        ExerciseCategory.PULL -> MuscleLane.PULL
        ExerciseCategory.CORE -> MuscleLane.CORE
        ExerciseCategory.LEGS -> MuscleLane.LEGS
        ExerciseCategory.MOBILITY -> null
        else -> laneFromName(entry.name)
    }

    private fun laneFromName(raw: String): MuscleLane? {
        val name = normalize(raw)
        return when {
            LEG_WORDS.any { it in name } -> MuscleLane.LEGS
            CORE_WORDS.any { it in name } -> MuscleLane.CORE
            PULL_WORDS.any { it in name } -> MuscleLane.PULL
            PUSH_WORDS.any { it in name } -> MuscleLane.PUSH
            else -> null
        }
    }

    fun normalize(raw: String): String = raw.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex(" +"), " ")

    private val STATIC_SKILL_WORDS = listOf(
        "handstand", "planche", "front lever", "back lever", "lever", "human flag",
        "l sit", "lsit", "v sit", "maltese", "iron cross", "skin the cat",
    )
    private val PUSH_WORDS = listOf(
        "push", "dip", "hspu", "handstand", "planche", "pike", "press", "bench",
        "maltese", "protraction", "archer", "pppu",
    )
    private val PULL_WORDS = listOf(
        "pull", "row", "chin", "curl", "lever", "muscle up", "flag",
    )
    private val CORE_WORDS = listOf(
        "hollow", "plank", "l sit", "lsit", "leg raise", "core", "dragon flag", "v sit",
    )
    private val LEG_WORDS = listOf(
        "squat", "lunge", "calf", "pistol", "nordic", "hamstring", "glute", "bridge", "hinge",
    )
}

fun DaySnapshot.pushSets(): Int = entries
    .filter { CoachAudit.laneOf(it) == MuscleLane.PUSH }
    .sumOf { it.sets }

fun DaySnapshot.pullSets(): Int = entries
    .filter { CoachAudit.laneOf(it) == MuscleLane.PULL }
    .sumOf { it.sets }

fun DaySnapshot.coreSets(): Int = entries
    .filter { CoachAudit.laneOf(it) == MuscleLane.CORE }
    .sumOf { it.sets }

fun DaySnapshot.legSets(): Int = entries
    .filter { CoachAudit.laneOf(it) == MuscleLane.LEGS }
    .sumOf { it.sets }

