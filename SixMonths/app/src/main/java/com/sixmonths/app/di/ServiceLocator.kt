package com.sixmonths.app.di

import android.content.Context
import androidx.room.Room
import com.sixmonths.app.data.AppDatabase
import com.sixmonths.app.data.backup.BackupRepository
import com.sixmonths.app.data.dao.ExerciseDao
import com.sixmonths.app.data.dao.MonsterCanDao
import com.sixmonths.app.data.dao.MonsterDao
import com.sixmonths.app.data.dao.PlanDayDao
import com.sixmonths.app.data.dao.PlanExerciseDao
import com.sixmonths.app.data.dao.SessionDao
import com.sixmonths.app.data.dao.SetLogDao
import com.sixmonths.app.data.dao.StreakDao
import com.sixmonths.app.data.prefs.SettingsRepository
import com.sixmonths.app.data.repository.MonsterCatalogRepository
import com.sixmonths.app.data.repository.MonsterRepository
import com.sixmonths.app.data.repository.PlanRepository
import com.sixmonths.app.data.repository.WorkoutRepository
import com.sixmonths.app.domain.StreakEngine

/**
 * Dependency wiring: manual service locator, no Hilt.
 *
 * Rationale: one process, one user, a handful of singletons. A locator keeps the build
 * free of annotation processors (only KSP for Room) and makes the object graph readable
 * in one file.
 *
 * [init] is called exactly once from [com.sixmonths.app.SixMonthsApp.onCreate].
 */
object ServiceLocator {

    private var applicationContext: Context? = null

    fun init(context: Context) {
        if (applicationContext == null) {
            applicationContext = context.applicationContext
        }
    }

    private fun requireContext(): Context = applicationContext
        ?: error("ServiceLocator.init() must be called from Application.onCreate() before use")

    /** App context for callers that need it outside a composable (e.g. WorkManager). */
    fun appContext(): Context = requireContext()

    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            requireContext(),
            AppDatabase::class.java,
            AppDatabase.NAME,
        )
            .addMigrations(*AppDatabase.MIGRATIONS)
            .build()
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(requireContext())
    }

    val exerciseDao: ExerciseDao by lazy { database.exerciseDao() }
    val planDayDao: PlanDayDao by lazy { database.planDayDao() }
    val planExerciseDao: PlanExerciseDao by lazy { database.planExerciseDao() }
    val sessionDao: SessionDao by lazy { database.sessionDao() }
    val setLogDao: SetLogDao by lazy { database.setLogDao() }
    val streakDao: StreakDao by lazy { database.streakDao() }
    val monsterDao: MonsterDao by lazy { database.monsterDao() }
    val monsterCanDao: MonsterCanDao by lazy { database.monsterCanDao() }

    val streakEngine: StreakEngine by lazy {
        StreakEngine(
            planDayDao = planDayDao,
            sessionDao = sessionDao,
            streakDao = streakDao,
            cycleStartMillis = {
                settingsRepository.current().let { settings ->
                    if (settings.cycleIsExplicit) settings.cycleStartedAt else 0L
                }
            },
        )
    }

    val planRepository: PlanRepository by lazy {
        PlanRepository(database, exerciseDao, planDayDao, planExerciseDao)
    }

    val workoutRepository: WorkoutRepository by lazy {
        WorkoutRepository(sessionDao, setLogDao, streakEngine)
    }

    val monsterCatalogRepository: MonsterCatalogRepository by lazy {
        MonsterCatalogRepository(requireContext(), monsterCanDao, monsterDao)
    }

    val monsterRepository: MonsterRepository by lazy {
        MonsterRepository(
            context = requireContext(),
            monsterDao = monsterDao,
            catalogRepository = monsterCatalogRepository,
        )
    }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            context = requireContext(),
            database = database,
            exerciseDao = exerciseDao,
            planDayDao = planDayDao,
            planExerciseDao = planExerciseDao,
            sessionDao = sessionDao,
            setLogDao = setLogDao,
            streakDao = streakDao,
            monsterDao = monsterDao,
            settingsRepository = settingsRepository,
        )
    }
}
