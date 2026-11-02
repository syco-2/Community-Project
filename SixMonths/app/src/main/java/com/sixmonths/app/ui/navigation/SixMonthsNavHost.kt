package com.sixmonths.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sixmonths.app.ui.components.LocalTabBarState
import com.sixmonths.app.ui.components.rememberTabBarState
import com.sixmonths.app.ui.icons.AppIcons
import com.sixmonths.app.ui.screens.dashboard.DashboardScreen
import com.sixmonths.app.ui.screens.dayeditor.DayEditorScreen
import com.sixmonths.app.ui.screens.plan.EditPlanScreen
import com.sixmonths.app.ui.screens.plan.PlanAnalysisScreen
import com.sixmonths.app.ui.screens.future.FutureMessageScreen
import com.sixmonths.app.ui.screens.monsters.MonstersScreen
import com.sixmonths.app.ui.screens.music.MusicScreen
import com.sixmonths.app.ui.screens.onboarding.OnboardingDaysScreen
import com.sixmonths.app.ui.screens.onboarding.OnboardingFutureMessageScreen
import com.sixmonths.app.ui.screens.onboarding.OnboardingMusicScreen
import com.sixmonths.app.ui.screens.onboarding.OnboardingPlanScreen
import com.sixmonths.app.ui.screens.onboarding.OnboardingRemindersScreen
import com.sixmonths.app.ui.screens.onboarding.OnboardingReviewScreen
import com.sixmonths.app.ui.screens.onboarding.OnboardingWelcomeScreen
import com.sixmonths.app.ui.screens.profile.ProfileScreen
import com.sixmonths.app.ui.screens.settings.SettingsScreen
import com.sixmonths.app.ui.screens.stats.StatsScreen
import com.sixmonths.app.ui.screens.train.TrainScreen
import com.sixmonths.app.ui.screens.train.WeeklyProgressionScreen
import com.sixmonths.app.ui.screens.workout.WorkoutPlayerScreen
import com.sixmonths.app.ui.theme.sixMonthsColors

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    Tab(Routes.DASHBOARD, "Home", Icons.Filled.Home),
    Tab(Routes.TRAIN, "Train", Icons.Filled.PlayArrow),
    Tab(Routes.MONSTERS, "Monstr", AppIcons.EnergyCan),
    Tab(Routes.MUSIC_LIBRARY, "Music", AppIcons.Headphones),
    Tab(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

/** The whole navigation graph: wizard first, then the five-tab main shell. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SixMonthsNavHost(
    startDestination: String,
    animationsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTabs = currentRoute in Routes.TAB_ROUTES
    // Shared with every scrolling tab screen; see TabBarScrollTracker.
    val tabBarState = rememberTabBarState()

    // A tab switch always starts at the top with the bar visible, even if the previous tab
    // was scrolled down and had slid it away.
    LaunchedEffect(currentRoute) { tabBarState.hidden = false }

    val slideSpec = spring<IntOffset>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow,
    )
    val fadeSpec = spring<Float>(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMedium,
    )

    CompositionLocalProvider(LocalTabBarState provides tabBarState) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showTabs) {
                // Auto-hiding glass bar: slides away while reading, springs back on scroll up.
                AnimatedVisibility(
                    visible = !tabBarState.hidden,
                    enter = slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) { height -> height },
                    exit = slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = 0.9f,
                            stiffness = Spring.StiffnessMedium,
                        ),
                    ) { height -> height },
                ) {
                // Floating glass pill: margins on both sides and below, translucent, elevated.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .shadow(
                            elevation = 22.dp,
                            shape = RoundedCornerShape(30.dp),
                            clip = false,
                        ),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0),
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = {
                                // Never wrap: "Monster" used to break into "Monster / s".
                                Text(
                                    text = tab.label,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.sixMonthsColors.textMuted,
                                unselectedTextColor = MaterialTheme.sixMonthsColors.textMuted,
                            ),
                        )
                    }
                }
                }
                }
            }
        },
    ) { innerPadding ->
        SharedTransitionLayout {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = modifier.padding(innerPadding),
            enterTransition = {
                if (animationsEnabled) {
                    slideInHorizontally(slideSpec) { fullWidth -> fullWidth / 8 } + fadeIn(fadeSpec)
                } else {
                    EnterTransition.None
                }
            },
            exitTransition = {
                if (animationsEnabled) {
                    slideOutHorizontally(slideSpec) { fullWidth -> -fullWidth / 16 } + fadeOut(fadeSpec)
                } else {
                    ExitTransition.None
                }
            },
            popEnterTransition = {
                if (animationsEnabled) {
                    slideInHorizontally(slideSpec) { fullWidth -> -fullWidth / 8 } + fadeIn(fadeSpec)
                } else {
                    EnterTransition.None
                }
            },
            popExitTransition = {
                if (animationsEnabled) {
                    slideOutHorizontally(slideSpec) { fullWidth -> fullWidth / 16 } + fadeOut(fadeSpec)
                } else {
                    ExitTransition.None
                }
            },
        ) {
            // ----- Onboarding wizard ---------------------------------------------
            composable(Routes.WELCOME) {
                OnboardingWelcomeScreen(
                    onGetStarted = { navController.navigate(Routes.TRAINING_DAYS) },
                )
            }
            composable(Routes.TRAINING_DAYS) {
                OnboardingDaysScreen(
                    onContinue = { navController.navigate(Routes.PLAN) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PLAN) {
                OnboardingPlanScreen(
                    onContinue = { navController.navigate(Routes.REMINDERS) },
                    onBack = { navController.popBackStack() },
                    onOpenDayEditor = { dayOfWeek ->
                        navController.navigate(Routes.dayEditor(dayOfWeek))
                    },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }
            composable(Routes.REMINDERS) {
                OnboardingRemindersScreen(
                    onContinue = { navController.navigate(Routes.MUSIC) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.MUSIC) {
                OnboardingMusicScreen(
                    onContinue = { navController.navigate(Routes.ONBOARDING_FUTURE_MESSAGE) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ONBOARDING_FUTURE_MESSAGE) {
                OnboardingFutureMessageScreen(
                    onContinue = { navController.navigate(Routes.REVIEW) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.REVIEW) {
                OnboardingReviewScreen(
                    onFinish = { navController.finishOnboarding() },
                    onBack = { navController.popBackStack() },
                )
            }

            // ----- Main tabs ------------------------------------------------------
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onStartWorkout = { sessionId ->
                        navController.navigate(Routes.workout(sessionId))
                    },
                    onOpenPlanEditor = { navController.navigate(Routes.EDIT_PLAN) },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenMusic = { navController.navigate(Routes.MUSIC_LIBRARY) },
                )
            }
            composable(Routes.TRAIN) {
                TrainScreen(
                    onStartWorkout = { sessionId -> navController.navigate(Routes.workout(sessionId)) },
                    onOpenDayEditor = { dayOfWeek -> navController.navigate(Routes.dayEditor(dayOfWeek)) },
                    onEditPlan = { navController.navigate(Routes.EDIT_PLAN) },
                    onOpenWeeklyReview = { navController.navigate(Routes.WEEKLY_PROGRESSION) },
                )
            }
            composable(Routes.MONSTERS) {
                MonstersScreen()
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onOpenMusic = { navController.navigate(Routes.MUSIC_LIBRARY) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenFutureMessage = { navController.navigate(Routes.FUTURE_MESSAGE) },
                )
            }

            // ----- Detail screens -------------------------------------------------
            composable(Routes.STATS) {
                StatsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.MUSIC_LIBRARY) {
                MusicScreen()
            }
            composable(Routes.EDIT_PLAN) {
                EditPlanScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDay = { dayOfWeek -> navController.navigate(Routes.dayEditor(dayOfWeek)) },
                    onAnalyze = { navController.navigate(Routes.PLAN_ANALYSIS) },
                )
            }
            composable(Routes.PLAN_ANALYSIS) {
                PlanAnalysisScreen(
                    onBack = { navController.popBackStack() },
                    // The report is opened from Edit Plan, so return to that exact screen with
                    // the freshly written week instead of pushing a duplicate onto the stack.
                    onEditPlan = {
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.EDIT_PLAN)
                        }
                    },
                )
            }
            composable(Routes.WEEKLY_PROGRESSION) {
                WeeklyProgressionScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.FUTURE_MESSAGE) {
                FutureMessageScreen(
                    onBack = { navController.popBackStack() },
                    onStartAgain = { navController.restartOnboarding() },
                    onContinue = { navController.returnToDashboard() },
                )
            }
            // ----- Parameterised screens ------------------------------------------
            composable(
                route = Routes.DAY_EDITOR_PATTERN,
                arguments = listOf(
                    navArgument(Routes.DAY_EDITOR_ARG) { type = NavType.IntType },
                ),
            ) { backStackEntry ->
                DayEditorScreen(
                    dayOfWeek = backStackEntry.arguments?.getInt(Routes.DAY_EDITOR_ARG) ?: 1,
                    onBack = { navController.popBackStack() },
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                )
            }
            composable(
                route = Routes.WORKOUT_PATTERN,
                arguments = listOf(
                    navArgument(Routes.WORKOUT_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { backStackEntry ->
                WorkoutPlayerScreen(
                    sessionId = backStackEntry.arguments?.getString(Routes.WORKOUT_ARG),
                    onBack = { navController.popBackStack() },
                    onFinished = { navController.returnToDashboard() },
                )
            }
        }
        }
    }
    }
}

/**
 * Clears the back stack down to the graph start and opens the dashboard. Using the graph
 * start (instead of the welcome route) also works when the wizard was resumed at a later
 * step, or when it was re-run from Settings.
 */
private fun NavHostController.resetToDashboard() {
    val startId = graph.startDestinationId
    navigate(Routes.DASHBOARD) {
        popUpTo(startId) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavHostController.finishOnboarding() = resetToDashboard()

private fun NavHostController.returnToDashboard() = resetToDashboard()

private fun NavHostController.restartOnboarding() {
    navigate(Routes.WELCOME) {
        popUpTo(graph.startDestinationId) { inclusive = true }
        launchSingleTop = true
    }
}
