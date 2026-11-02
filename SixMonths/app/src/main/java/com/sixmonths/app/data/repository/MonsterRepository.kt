package com.sixmonths.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.sixmonths.app.core.util.newId
import com.sixmonths.app.data.dao.MonsterDao
import com.sixmonths.app.data.entity.Monster
import com.sixmonths.app.domain.monsters.CanNameMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/** One cabinet slot: a catalog flavour plus the capture that unlocked it (null = empty). */
data class CabinetEntry(
    val key: String,
    val flavorId: String,
    val name: String,
    val color: String,
    val slot: Int,
    /** Cabinet section the slot belongs to; blank for the user's own cans. */
    val theme: String,
    val capturedAt: Long?,
    val captureId: String?,
    val imageUri: String?,
) {
    val unlocked: Boolean get() = captureId != null
}

/**
 * The manual cabinet: one photo plus one typed flavour name per can.
 *
 * Nothing is recognised or corrected automatically. [addCapture] takes the camera's JPEG,
 * crops it to a clean square, stores it in app-private storage and keeps the typed name
 * exactly as written — "Ultra Fantasy Ruby Red" stays "Ultra Fantasy Ruby Red". When the typed
 * name is an exact shipped flavour (case-insensitive), the can fills that catalogue slot and
 * takes its accent colour; any other name becomes a custom can with its own slot.
 */
class MonsterRepository(
    private val context: Context,
    private val monsterDao: MonsterDao,
    private val catalogRepository: MonsterCatalogRepository,
) {

    fun observeAll(): Flow<List<Monster>> = monsterDao.observeAll()

    /** Changes whenever a pasted list added flavours, so open cabinets reload. */
    val catalogRevision = catalogRepository.revision

    suspend fun getAll(): List<Monster> = monsterDao.getAll()

    /** CameraX writes its JPEG here. The file only becomes a cabinet photo on [addCapture]. */
    fun newPhotoFile(): File {
        val directory = File(context.cacheDir, "can_photos").apply { mkdirs() }
        return File(directory, "${newId()}.jpg")
    }

    /** Drops the raw camera file without touching the cabinet. */
    fun discard(photo: File) {
        runCatching { photo.delete() }
    }

    /**
     * Crops [photo] to a centred square, scales it down to [PHOTO_SIZE] and stores it. The row
     * is inserted only after the image is safely on disk, so the grid never shows a broken can.
     */
    suspend fun addCapture(photo: File, typedName: String): Monster = withContext(Dispatchers.IO) {
        val catalog = catalogRepository.flavors()
        val displayName = typedName.trim().ifBlank { "Custom can" }
        // Exact (case-insensitive) match only: the user's spelling is never rewritten.
        val flavor = catalog.all.firstOrNull { it.name.equals(displayName, ignoreCase = true) }
        val accent = flavor?.color ?: accentFor(displayName)

        val id = newId()
        val target = File(cansDirectory(), "$id.jpg")
        val written = runCatching { writeSquareJpeg(photo, target) }.getOrDefault(false)
        if (!written) {
            target.delete()
            error("The photo could not be processed.")
        }
        discard(photo)

        // Same flavour again: replace the old photo instead of adding a duplicate slot.
        if (flavor != null) {
            val previous = monsterDao.getAll().firstOrNull { it.flavorId == flavor.id }
            if (previous != null) {
                monsterDao.deleteById(previous.id)
                deleteImage(previous.imageUri)
            }
        }

        val monster = Monster(
            id = id,
            name = displayName,
            type = accent,
            imageUri = Uri.fromFile(target).toString(),
            capturedAt = System.currentTimeMillis(),
            flavorId = flavor?.id.orEmpty(),
        )
        monsterDao.insert(monster)
        monster
    }

    suspend fun delete(monster: Monster) {
        monsterDao.deleteById(monster.id)
        withContext(Dispatchers.IO) { deleteImage(monster.imageUri) }
    }

    /** Removes one cabinet capture and its private photo file. */
    suspend fun deleteCapture(captureId: String) {
        val monster = monsterDao.getAll().firstOrNull { it.id == captureId }
        if (monster != null) delete(monster)
    }

    /**
     * The full cabinet in slot order: every shipped flavour (silhouette until collected)
     * followed by the user's custom cans, newest last.
     */
    suspend fun cabinet(): List<CabinetEntry> {
        val catalog = catalogRepository.flavors()
        val captures = monsterDao.getAll()
        val byFlavor = LinkedHashMap<String, Monster>()
        val custom = mutableListOf<Monster>()

        captures.forEach { monster ->
            val flavor = catalog.byId(monster.flavorId)
            if (flavor != null) {
                val existing = byFlavor[flavor.id]
                if (existing == null || monster.capturedAt > existing.capturedAt) {
                    byFlavor[flavor.id] = monster
                }
            } else if (monster.flavorId.isNotBlank()) {
                // Flavour disappeared from the catalog: keep the photo as a custom can.
                custom += monster
            } else if (isLegacyPlaceholder(monster.name)) {
                val legacy = catalog.legacyFlavor(monster.id)
                if (legacy != null && legacy.id !in byFlavor) byFlavor[legacy.id] = monster
            } else {
                custom += monster
            }
        }

        val shipped = catalog.all.map { flavor ->
            val capture = byFlavor[flavor.id]
            CabinetEntry(
                key = flavor.id,
                flavorId = flavor.id,
                name = flavor.name,
                color = flavor.color,
                slot = flavor.slot,
                theme = flavor.theme,
                capturedAt = capture?.capturedAt,
                captureId = capture?.id,
                imageUri = capture?.imageUri,
            )
        }
        val extras = custom.sortedBy { it.capturedAt }.mapIndexed { index, monster ->
            CabinetEntry(
                key = monster.id,
                flavorId = monster.flavorId,
                name = monster.name,
                color = monster.type,
                slot = 1_000 + index,
                theme = "",
                capturedAt = monster.capturedAt,
                captureId = monster.id,
                imageUri = monster.imageUri,
            )
        }
        return shipped + extras
    }

    /** Live suggestions for the name dialog, backed by the same catalog. */
    suspend fun suggestions(typed: String, limit: Int = 4): List<String> {
        val catalog = catalogRepository.flavors()
        return CanNameMatcher.suggestions(typed, catalog, limit).map { it.name }
    }

    suspend fun catalogNames(): List<String> =
        catalogRepository.flavors().all.map { it.name }

    private fun cansDirectory(): File = File(context.filesDir, "cans").apply { mkdirs() }

    private fun deleteImage(rawUri: String) {
        runCatching {
            val path = Uri.parse(rawUri).path ?: return@runCatching
            File(path).takeIf { it.parentFile?.name == "cans" }?.delete()
        }
    }

    /**
     * Decodes with a power-of-two sample factor, applies the EXIF orientation, centre-crops to
     * a square and scales to [PHOTO_SIZE]. Keeps memory at a few MB even for 12 MP photos.
     */
    private fun writeSquareJpeg(source: File, target: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PHOTO_SIZE) sample *= 2

        val decoded = BitmapFactory.decodeFile(
            source.absolutePath,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        ) ?: return false

        val upright = rotateToExif(decoded, source)
        val side = minOf(upright.width, upright.height)
        val left = (upright.width - side) / 2
        val top = (upright.height - side) / 2
        val square = Bitmap.createBitmap(upright, left, top, side, side)
        val scaled = Bitmap.createScaledBitmap(square, PHOTO_SIZE, PHOTO_SIZE, true)

        val ok = runCatching {
            FileOutputStream(target).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
        }.isSuccess

        if (square !== upright) square.recycle()
        if (scaled !== square) scaled.recycle()
        if (upright !== decoded) upright.recycle()
        decoded.recycle()
        return ok
    }

    private fun rotateToExif(bitmap: Bitmap, source: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun isLegacyPlaceholder(name: String): Boolean =
        name.isBlank() || name.equals("Unrecognised can", ignoreCase = true)

    private fun accentFor(name: String): String {
        val index = Math.floorMod(name.lowercase().hashCode(), ACCENTS.size)
        return ACCENTS[index]
    }

    private companion object {
        const val PHOTO_SIZE = 720
        val ACCENTS = listOf(
            "#2F80ED",
            "#FF6B2C",
            "#22C55E",
            "#F59E0B",
            "#8B5CF6",
            "#EC4899",
            "#06B6D4",
            "#EF4444",
        )
    }
}
