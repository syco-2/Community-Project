package com.sixmonths.app.domain

import com.sixmonths.app.core.model.DayState
import com.sixmonths.app.core.model.ExerciseCategory
import com.sixmonths.app.data.model.DaySnapshot
import com.sixmonths.app.data.model.PlanEntryRow
import com.sixmonths.app.data.model.PlanSnapshot

enum class Severity { INFO, WARNING, CRITICAL }

data class CoachSuggestion(
    val id: String,
    val title: String,
    val detail: String,
    val severity: Severity,
    val category: String? = null,
)

data class CoachReviewResult(
    val suggestions: List<CoachSuggestion>,
    val setsByCategory: Map<String, Int>,
    val trainingDayCount: Int,
    val hasTrainingDays: Boolean,
    val unclassifiedExercises: List<String>,
)

/**
 * Deterministic analysis of the stored categories only. There is no keyword matching,
 * no inference and no automatic plan change: every suggestion is shown to the user as a
 * checklist item that can be accepted or dismissed.
 */
object CoachReview {

    private const val BALANCE_THRESHOLD = 0.40

    fun analyze(plan: PlanSnapshot): CoachReviewResult {
        val trainingDays = plan.trainingDays
        val entries = plan.days.flatMap { it.entries }
        val setsByCategory = ExerciseCategory.ALL.associateWith { category ->
            entries.filter { it.category == category }.sumOf { it.sets }
        }

        val unclassified = entries
            .filter { it.category == ExerciseCategory.OTHER }
            .map { it.name }
            .distinct()

        if (trainingDays.isEmpty()) {
            return CoachReviewResult(
                suggestions = emptyList(),
                setsByCategory = setsByCategory,
                trainingDayCount = 0,
                hasTrainingDays = false,
                unclassifiedExercises = unclassified,
            )
        }

        val suggestions = mutableListOf<CoachSuggestion>()
        addBalance(setsByCategory, suggestions)
        addCoverage(setsByCategory, suggestions)
        addDominantPatterns(trainingDays, suggestions)
        addRecovery(trainingDays, suggestions)
        addDataQuality(unclassified, suggestions)

        return CoachReviewResult(
            suggestions = suggestions,
            setsByCategory = setsByCategory,
            trainingDayCount = trainingDays.size,
            hasTrainingDays = true,
            unclassifiedExercises = unclassified,
        )
    }

    private fun addBalance(sets: Map<String, Int>, out: MutableList<CoachSuggestion>) {
        val push = sets[ExerciseCategory.PUSH] ?: 0
        val pull = sets[ExerciseCategory.PULL] ?: 0
        when {
            push == 0 && pull == 0 -> out += CoachSuggestion(
                id = "balance_zero_both",
                title = "No push or pull volume",
                detail = "Your week has neither push nor pull sets. Add at least one of each to build a base.",
                severity = Severity.CRITICAL,
            )

            push == 0 -> out += CoachSuggestion(
                id = "balance_zero_push",
                title = "Pulling only",
                detail = "There are $pull pull sets and zero push sets. Push/pull balance is off by more than 40%.",
                severity = Severity.WARNING,
            )

            pull == 0 -> out += CoachSuggestion(
                id = "balance_zero_pull",
                title = "Pushing only",
                detail = "There are $push push sets and zero pull sets. Push/pull balance is off by more than 40%.",
                severity = Severity.WARNING,
            )

            else -> {
                val larger = maxOf(push, pull)
                val diff = kotlin.math.abs(push - pull).toDouble() / larger
                if (diff > BALANCE_THRESHOLD) {
                    out += CoachSuggestion(
                        id = "balance_imbalance",
                        title = "Push/pull imbalance",
                        detail = "Push has $push sets, pull has $pull sets — a ${(diff * 100).toInt()}% difference.",
                        severity = Severity.WARNING,
                    )
                }
            }
        }
    }

    private fun addCoverage(sets: Map<String, Int>, out: MutableList<CoachSuggestion>) {
        listOf(
            ExerciseCategory.SKILL to "skill work",
            ExerciseCategory.CORE to "core work",
            ExerciseCategory.MOBILITY to "mobility work",
        ).forEach { (category, label) ->
            if ((sets[category] ?: 0) == 0) {
                out += CoachSuggestion(
                    id = "coverage_${category.lowercase()}",
                    title = "No ${label}",
                    detail = "The whole week contains zero ${ExerciseCategory.displayName(category)} sets.",
                    severity = Severity.INFO,
                    category = category,
                )
            }
        }
    }

    private fun addDominantPatterns(trainingDays: List<DaySnapshot>, out: MutableList<CoachSuggestion>) {
        val dominant = trainingDays.mapNotNull { day ->
            dominantCategory(day)?.let { day to it }
        }

        dominant.zipWithNext { (firstDay, firstCategory), (secondDay, secondCategory) ->
            if (firstCategory == secondCategory &&
                firstCategory != ExerciseCategory.OTHER &&
                areAdjacent(firstDay.planDay.dayOfWeek, secondDay.planDay.dayOfWeek)
            ) {
                out += CoachSuggestion(
                    id = "pattern_consecutive_${firstDay.planDay.dayOfWeek}_${secondDay.planDay.dayOfWeek}",
                    title = "Same pattern on back-to-back days",
                    detail = "${ExerciseCategory.displayName(firstCategory)} dominates " +
                        "${weekdayName(firstDay.planDay.dayOfWeek)} and " +
                        "${weekdayName(secondDay.planDay.dayOfWeek)}.",
                    severity = Severity.WARNING,
                    category = firstCategory,
                )
            }
        }

        dominant.groupBy({ it.second }, { it.first }).forEach { (category, days) ->
            if (category != ExerciseCategory.OTHER && days.size > 3) {
                out += CoachSuggestion(
                    id = "pattern_frequency_${category.lowercase()}",
                    title = "${ExerciseCategory.displayName(category)} on ${days.size} days",
                    detail = "A category appears as the dominant one on more than three training days a week.",
                    severity = Severity.WARNING,
                    category = category,
                )
            }
        }
    }

    private fun addRecovery(trainingDays: List<DaySnapshot>, out: MutableList<CoachSuggestion>) {
        if (trainingDays.size < 2) return
        trainingDays.forEachIndexed { index, day ->
            val next = trainingDays[(index + 1) % trainingDays.size]
            if (next.planDay.dayOfWeek == day.planDay.dayOfWeek) return@forEachIndexed
            val gap = (next.planDay.dayOfWeek - day.planDay.dayOfWeek + 7) % 7
            val dominant = dominantCategory(day)
            if (gap == 1 && dominant != null && dominant == dominantCategory(next)) {
                out += CoachSuggestion(
                    id = "recovery_${day.planDay.dayOfWeek}_${next.planDay.dayOfWeek}",
                    title = "No rest between ${ExerciseCategory.displayName(dominant)} days",
                    detail = "${weekdayName(day.planDay.dayOfWeek)} and " +
                        "${weekdayName(next.planDay.dayOfWeek)} are back-to-back with no Rest day in between.",
                    severity = Severity.WARNING,
                    category = dominant,
                )
            }
        }
    }

    private fun addDataQuality(unclassified: List<String>, out: MutableList<CoachSuggestion>) {
        if (unclassified.isEmpty()) return
        out += CoachSuggestion(
            id = "data_quality_other",
            title = "Unclassified exercises",
            detail = "Excluded from balance analysis: ${unclassified.joinToString(", ")}. " +
                "Assign a category in the Day Editor if you want them counted.",
            severity = Severity.INFO,
            category = ExerciseCategory.OTHER,
        )
    }

    private fun dominantCategory(day: DaySnapshot): String? {
        val byCategory = day.entries.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.sets } }
        if (byCategory.isEmpty()) return null
        val highest = byCategory.maxByOrNull { it.value } ?: return null
        val tied = byCategory.filterValues { it == highest.value }
        return if (tied.size == 1) highest.key else null
    }

    private fun areAdjacent(first: Int, second: Int): Boolean {
        val gap = (second - first + 7) % 7
        return gap == 1 || gap == 6
    }

    private fun weekdayName(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        else -> "Sunday"
    }

    /** Exposed for unit tests. */
    fun entryCategory(entry: PlanEntryRow): String = entry.category

    /** Exposed for unit tests: only TRAINING days have entries considered for patterns. */
    fun trainingEntries(plan: PlanSnapshot): List<PlanEntryRow> =
        plan.days.filter { it.planDay.state == DayState.TRAINING }.flatMap { it.entries }
}
