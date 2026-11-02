package com.sixmonths.app.core.model

/**
 * Exercise categories. Every exercise stores exactly one of these values as a plain
 * [String] (see [com.sixmonths.app.data.entity.Exercise.category]), so the stored value
 * is the single source of truth for Coach Review. The category is never guessed at
 * analysis time.
 */
object ExerciseCategory {
    const val PUSH = "PUSH"
    const val PULL = "PULL"
    const val SKILL = "SKILL"
    const val CORE = "CORE"
    const val MOBILITY = "MOBILITY"
    const val LEGS = "LEGS"
    const val OTHER = "OTHER"

    /** Default category, used when the user has not chosen one. */
    const val DEFAULT = OTHER

    /** Display order for the chip picker. OTHER stays last and is preselected. */
    val ALL: List<String> = listOf(PUSH, PULL, SKILL, CORE, MOBILITY, LEGS, OTHER)

    fun displayName(category: String): String = when (category) {
        PUSH -> "Push"
        PULL -> "Pull"
        SKILL -> "Skill"
        CORE -> "Core"
        LEGS -> "Legs"
        MOBILITY -> "Mobility"
        else -> "Other"
    }

    fun isValid(category: String): Boolean = category in ALL
}

/** Unit of a planned exercise / a logged set value. */
object ExerciseUnit {
    const val REPS = "REPS"
    const val SECONDS = "SECONDS"

    val ALL: List<String> = listOf(REPS, SECONDS)

    fun isValid(unit: String): Boolean = unit in ALL
}

/** State of a plan day. Only [TRAINING] days are streak-relevant. */
object DayState {
    const val TRAINING = "TRAINING"
    const val REST = "REST"
    const val OPTIONAL = "OPTIONAL"

    val ALL: List<String> = listOf(TRAINING, REST, OPTIONAL)

    fun isValid(state: String): Boolean = state in ALL

    fun displayName(state: String): String = when (state) {
        TRAINING -> "Training"
        OPTIONAL -> "Optional"
        else -> "Rest"
    }
}

/** Lifecycle of a training session. */
object SessionStatus {
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
    const val ABANDONED = "ABANDONED"

    val ALL: List<String> = listOf(ACTIVE, COMPLETED, ABANDONED)

    fun isValid(status: String): Boolean = status in ALL
}

/** Weekday helpers: 1 = Monday ... 7 = Sunday, matching [java.time.DayOfWeek.value]. */
object Weekday {
    const val MONDAY = 1
    const val SUNDAY = 7

    val ALL: List<Int> = (MONDAY..SUNDAY).toList()

    fun shortName(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Mon"
        2 -> "Tue"
        3 -> "Wed"
        4 -> "Thu"
        5 -> "Fri"
        6 -> "Sat"
        else -> "Sun"
    }

    fun fullName(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        else -> "Sunday"
    }

    fun isValid(dayOfWeek: Int): Boolean = dayOfWeek in 1..7
}

/** ISO local date pattern used for streak dates. Never a UTC timestamp. */
const val ISO_LOCAL_DATE_PATTERN: String = "yyyy-MM-dd"
