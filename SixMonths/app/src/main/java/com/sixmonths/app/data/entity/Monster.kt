package com.sixmonths.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One collected can.
 *
 * [imageUri] points at the square 720 px JPEG in app-private storage that the user took and
 * that the cabinet displays. [flavorId] points into the Room table `monster_can_species` and
 * decides which shipped slot is filled; a blank id means a custom can with its own slot.
 *
 * The column is still called `speciesId` because it was introduced in Room v3; renaming it
 * would force a table rewrite for no user-visible gain.
 */
@Entity(
    tableName = "monsters",
    indices = [Index(value = ["capturedAt"]), Index(value = ["speciesId"])],
)
data class Monster(
    @PrimaryKey val id: String,
    val name: String,
    /** Can accent colour as `#RRGGBB`. */
    val type: String,
    val imageUri: String,
    val capturedAt: Long,
    @ColumnInfo(name = "speciesId") val flavorId: String = "",
)
