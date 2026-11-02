package com.sixmonths.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sixmonths.app.data.dao.ExerciseDao
import com.sixmonths.app.data.dao.MonsterCanDao
import com.sixmonths.app.data.dao.MonsterDao
import com.sixmonths.app.data.dao.PlanDayDao
import com.sixmonths.app.data.dao.PlanExerciseDao
import com.sixmonths.app.data.dao.SessionDao
import com.sixmonths.app.data.dao.SetLogDao
import com.sixmonths.app.data.dao.StreakDao
import com.sixmonths.app.data.entity.Exercise
import com.sixmonths.app.data.entity.Monster
import com.sixmonths.app.data.entity.MonsterCanSpecies
import com.sixmonths.app.data.entity.PlanDay
import com.sixmonths.app.data.entity.PlanExercise
import com.sixmonths.app.data.entity.Session
import com.sixmonths.app.data.entity.SetLog
import com.sixmonths.app.data.entity.StreakState

/**
 * Room version 7.
 *
 * Migration policy:
 * - [exportSchema] is on and KSP writes the JSON schema to app/schemas/ (commit those files).
 * - Every schema change bumps [VERSION] and adds an explicit [Migration] to [MIGRATIONS];
 *   destructive fallback is never used, so an unhandled upgrade fails loudly instead of
 *   wiping the user's training history.
 * - v1 → v2 only adds the `monsters` table. Every v1 table, row and index is left untouched,
 *   so installing this build over the previous one keeps the plan, sessions, set logs and
 *   streak exactly as they were.
 * - v2 → v3 adds `monsters.speciesId` (default '') and its index. Existing captures keep
 *   their rows; the catalog resolves a blank species id to a stable cabinet slot on read.
 * - v3 → v4 adds `monster_can_species`, the Room table of real Monster Energy flavours.
 *   Again additive: no existing row or index is touched.
 * - v4 → v5 deliberately empties `monsters` once: the manual cabinet replaced the old
 *   automatic scanner, so every entry produced by that flow is stale. Plan, sessions, set
 *   logs and streak are untouched. Nothing else is ever destroyed by a migration.
 * - v5 → v6 adds `set_logs.planExerciseId` (default ''). It makes logs refer to the exact
 *   plan row, so duplicating an exercise no longer makes two rows share one log slot.
 *   Existing logs keep their values and resolve to the first matching row on resume.
 * - v6 → v7 adds `monster_can_species.theme` (default ''), the cabinet section a flavour
 *   belongs to. Existing rows get their section filled in from the shipped list on read.
 * - JSON backup/restore is a separate, validated, transactional path and does not replace
 *   migrations.
 */
@Database(
    entities = [
        Exercise::class,
        PlanDay::class,
        PlanExercise::class,
        Session::class,
        SetLog::class,
        StreakState::class,
        Monster::class,
        MonsterCanSpecies::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun exerciseDao(): ExerciseDao
    abstract fun planDayDao(): PlanDayDao
    abstract fun planExerciseDao(): PlanExerciseDao
    abstract fun sessionDao(): SessionDao
    abstract fun setLogDao(): SetLogDao
    abstract fun streakDao(): StreakDao
    abstract fun monsterDao(): MonsterDao
    abstract fun monsterCanDao(): MonsterCanDao

    companion object {
        const val NAME: String = "six_months.db"
        const val VERSION: Int = 7

        /** Adds the Monsterscanner collection. Additive only — no user data is touched. */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `monsters` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`imageUri` TEXT NOT NULL, " +
                        "`capturedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_monsters_capturedAt` " +
                        "ON `monsters` (`capturedAt`)",
                )
            }
        }

        /** Adds the cabinet species reference. Additive only — no user data is touched. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `monsters` ADD COLUMN `speciesId` TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_monsters_speciesId` " +
                        "ON `monsters` (`speciesId`)",
                )
            }
        }

        /** Adds the Room table holding the 40 real Monster Energy flavours. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `monster_can_species` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`color` TEXT NOT NULL, " +
                        "`keywords` TEXT NOT NULL, " +
                        "`slot` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_monster_can_species_slot` " +
                        "ON `monster_can_species` (`slot`)",
                )
            }
        }

        /**
         * One-time wipe of the retired automatic-scanner captures. This is the only
         * destructive step in the app's migration history and it is scoped to `monsters`;
         * the training plan, history and streak stay exactly as they are.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM `monsters`")
            }
        }

        /** Adds the exact plan-row reference to set logs. Additive. */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `set_logs` ADD COLUMN `planExerciseId` TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /** Adds the cabinet section of every flavour. Additive. */
        val MIGRATION_6_7: Migration = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `monster_can_species` ADD COLUMN `theme` TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
        )
    }
}
