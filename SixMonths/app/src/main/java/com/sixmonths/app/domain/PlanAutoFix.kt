package com.sixmonths.app.domain

import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.Weekday
import com.sixmonths.app.data.entity.PlanDay
import com.sixmonths.app.data.entity.PlanExercise
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.data.model.PlanEntryRow
import com.sixmonths.app.data.model.PlanSnapshot

/**
 * The concrete change set produced by the coach. Kept as plain data so it can be computed on
 * a background thread, asserted in unit tests and then written to Room in one transaction.
 */
data class AutoFixPlan(
    val days: List<PlanDay>,
    val entriesByDay: Map<String, List<PlanExercise>>,
    val movedSkills: Int,
    val cappedDays: Int,
    val restDayOfWeek: Int?,
    val summary: List<String>,
) {
    val changed: Boolean
        get() = movedSkills > 0 || cappedDays > 0 || restDayOfWeek != null
}

/**
 * Applies the three fixes the audit is allowed to make:
 *  1. Static skills move to the front of their day and get 3 minutes of rest.
 *  2. A muscle group is capped at 12 sets per day.
 *  3. A rest day is inserted when all seven days train, or when push/skill work runs for
 *     more than three consecutive days. The lowest-volume day of the run becomes the rest.
 *
 * It never invents exercises and never changes reps.
 */
object PlanAutoFix {

    fun build(plan: PlanSnapshot): AutoFixPlan {
        val entriesByDay = LinkedHashMap<String, List<PlanExercise>>()
        var movedSkills = 0
        var cappedDays = 0

        plan.days.forEach { day ->
            val rows = day.entries.sortedBy { it.sortOrder }
            if (day.planDay.state == DayState.REST || rows.isEmpty()) {
                entriesByDay[day.planDay.id] = rows.map { it.toEntity() }
                return@forEach
            }

            val skillRows = rows.filter(CoachAudit::isStaticSkill)
            val otherRows = rows.filterNot(CoachAudit::isStaticSkill)
            val skillsWereLeading = rows.take(skillRows.size).all(CoachAudit::isStaticSkill)
            val skillsNeededRest = skillRows.any { it.restSeconds < CoachAudit.SKILL_REST_SECONDS }

            val ordered = buildList {
                skillRows.forEach {
                    add(it.copy(restSeconds = it.restSeconds.coerceAtLeast(CoachAudit.SKILL_REST_SECONDS)))
                }
                addAll(otherRows)
            }

            val capped = mutableListOf<PlanEntryRow>()
            val used = mutableMapOf<MuscleLane, Int>()
            var cappedThisDay = 0
            ordered.forEach { row ->
                val lane = CoachAudit.laneOf(row)
                if (lane == null) {
                    capped += row
                    return@forEach
                }
                val remaining = CoachAudit.DAILY_MUSCLE_CAP - (used[lane] ?: 0)
                if (remaining <= 0) {
                    cappedThisDay += 1
                    return@forEach
                }
                val newSets = row.sets.coerceAtMost(remaining)
                if (newSets <= 0) {
                    cappedThisDay += 1
                    return@forEach
                }
                if (newSets != row.sets) cappedThisDay += 1
                used[lane] = (used[lane] ?: 0) + newSets
                capped += row.copy(sets = newSets)
            }

            if (skillRows.isNotEmpty() && (!skillsWereLeading || skillsNeededRest)) {
                movedSkills += skillRows.size
            }
            if (cappedThisDay > 0) cappedDays += 1
            entriesByDay[day.planDay.id] = capped.mapIndexed { index, row ->
                row.copy(sortOrder = index).toEntity()
            }
        }

        // If every day is a training day, recover the lowest-volume day. Ties go to the
        // later weekday so a Saturday/Sunday rest is preferred over a Monday.
        val allSevenTraining = plan.days.size == 7 &&
            plan.days.all { it.planDay.state == DayState.TRAINING }
        val restDay = when {
            allSevenTraining -> plan.days
                .minWithOrNull(
                    compareBy<DaySnapshot> { it.totalSets }
                        .thenByDescending { it.planDay.dayOfWeek },
                )
                ?.planDay
                ?.dayOfWeek

            else -> longestPushSkillRun(plan)
                // More than three loaded days in a row is the joint-health ceiling.
                .takeIf { it.size > 3 }
                ?.minByOrNull { it.totalSets }
                ?.planDay
                ?.dayOfWeek
        }

        val finalDays = plan.days.map { day ->
            if (restDay != null && day.planDay.dayOfWeek == restDay) {
                day.planDay.copy(state = DayState.REST)
            } else {
                day.planDay
            }
        }

        val summary = buildList {
            if (movedSkills > 0) {
                add("Moved $movedSkills skill exercise${if (movedSkills == 1) "" else "s"} " +
                    "to the front with 3 min rest.")
            }
            if (cappedDays > 0) {
                add("Capped daily muscle volume at ${CoachAudit.DAILY_MUSCLE_CAP} sets on " +
                    "$cappedDays day${if (cappedDays == 1) "" else "s"}.")
            }
            if (restDay != null) {
                add("Turned ${Weekday.fullName(restDay)} into a full rest day.")
            }
            if (isEmpty()) {
                add("No automatic fixes were needed. The week already follows the rules.")
            }
        }

        return AutoFixPlan(
            days = finalDays,
            entriesByDay = entriesByDay,
            movedSkills = movedSkills,
            cappedDays = cappedDays,
            restDayOfWeek = restDay,
            summary = summary,
        )
    }

    /**
     * The longest circular run of consecutive days that load push or a static skill. A
     * pull-only or rest day ends the run. Empty when nothing loads push.
     */
    private fun longestPushSkillRun(plan: PlanSnapshot): List<DaySnapshot> {
        val byWeekday = (1..7).map { weekday ->
            plan.days.firstOrNull { it.planDay.dayOfWeek == weekday }
        }
        val loaded = byWeekday.map { day ->
            day != null &&
                day.planDay.state != DayState.REST &&
                day.entries.any { entry ->
                    CoachAudit.laneOf(entry) == MuscleLane.PUSH || CoachAudit.isStaticSkill(entry)
                }
        }
        if (loaded.none { it }) return emptyList()

        val start = loaded.indexOfFirst { !it } + 1
        var run = 0
        var bestStart = 0
        var bestLength = 0
        for (offset in 0 until 7) {
            val index = (start + offset) % 7
            if (loaded[index]) {
                if (run == 0) bestStart = index
                run += 1
                if (run > bestLength) bestLength = run
            } else {
                run = 0
                bestStart = 0
            }
        }
        if (bestLength == 0) return emptyList()

        // Re-walk the best run so the returned days match its length exactly.
        return (0 until bestLength).mapNotNull { step -> byWeekday[(bestStart + step) % 7] }
    }

    private fun PlanEntryRow.toEntity(): PlanExercise = PlanExercise(
        id = planExerciseId,
        planDayId = planDayId,
        exerciseId = exerciseId,
        label = label,
        sets = sets,
        reps = reps,
        restSeconds = restSeconds,
        sortOrder = sortOrder,
    )
}
