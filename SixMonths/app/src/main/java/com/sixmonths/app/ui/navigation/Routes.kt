package com.sixmonths.app.ui.navigation

/**
 * Every destination of the app, as route patterns. Argument routes expose a builder so
 * no screen has to hand-format a URL.
 */
object Routes {

    // Onboarding wizard (Part 2).
    const val WELCOME = "onboarding/welcome"
    const val TRAINING_DAYS = "onboarding/days"
    const val PLAN = "onboarding/plan"
    const val REMINDERS = "onboarding/reminders"
    const val MUSIC = "onboarding/music"
    const val ONBOARDING_FUTURE_MESSAGE = "onboarding/future-message"
    const val REVIEW = "onboarding/review"

    // Main shell: five bottom tabs.
    const val DASHBOARD = "dashboard"
    const val TRAIN = "train"
    const val MONSTERS = "monsters"
    const val PROFILE = "profile"

    const val STATS = "stats"
    const val SETTINGS = "settings"

    // The Music tab. Kept as its own route name so the old deep links still resolve.
    const val MUSIC_LIBRARY = "music"

    // Edit plan is a real screen now, not the onboarding wizard again.
    const val EDIT_PLAN = "edit-plan"
    const val PLAN_ANALYSIS = "plan-analysis"
    const val WEEKLY_PROGRESSION = "weekly-progression"
    const val FUTURE_MESSAGE = "future-message"

    // Day Editor (Part 2): dayOfWeek = 1..7.
    const val DAY_EDITOR_ARG: String = "dayOfWeek"
    const val DAY_EDITOR_PATTERN = "day-editor/{$DAY_EDITOR_ARG}"
    fun dayEditor(dayOfWeek: Int): String = "day-editor/$dayOfWeek"

    // Workout Player (Part 3): optional sessionId.
    const val WORKOUT_ARG: String = "sessionId"
    const val WORKOUT_PATTERN = "workout?$WORKOUT_ARG={$WORKOUT_ARG}"
    fun workout(sessionId: String? = null): String =
        if (sessionId.isNullOrBlank()) "workout" else "workout?$WORKOUT_ARG=$sessionId"

    /** Wizard order, used for the progress dots. */
    val ONBOARDING_ORDER: List<String> = listOf(
        WELCOME,
        TRAINING_DAYS,
        PLAN,
        REMINDERS,
        MUSIC,
        ONBOARDING_FUTURE_MESSAGE,
        REVIEW,
    )

    fun onboardingStepOf(route: String): Int =
        (ONBOARDING_ORDER.indexOf(route) + 1).coerceAtLeast(1)

    /** Resume the wizard where the user left it (step 1..7). */
    fun onboardingRoute(step: Int): String =
        ONBOARDING_ORDER.getOrElse((step - 1).coerceIn(0, ONBOARDING_ORDER.lastIndex)) { WELCOME }

    val TAB_ROUTES: Set<String> = setOf(DASHBOARD, TRAIN, MONSTERS, MUSIC_LIBRARY, PROFILE)
}
