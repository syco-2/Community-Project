package com.sixmonths.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The cabinet catalog as a Room table: the shipped Monster Energy flavours plus every
 * flavour list the user pastes in Settings.
 *
 * It is seeded from `assets/monster_cans.json`, so the flavours live in the database the
 * manual name matcher works against — no network request.
 */
@Entity(
    tableName = "monster_can_species",
    indices = [Index(value = ["slot"], unique = true)],
)
data class MonsterCanSpecies(
    @PrimaryKey val id: String,
    val name: String,
    /** Can accent colour as `#RRGGBB`. */
    val color: String,
    /** Comma-separated words printed on the can, used as typing aliases. */
    val keywords: String,
    val slot: Int,
    /** Cabinet section shown above the flavours, e.g. "Classic", "Java", "Ultra". */
    @ColumnInfo(defaultValue = "")
    val theme: String = "",
)
