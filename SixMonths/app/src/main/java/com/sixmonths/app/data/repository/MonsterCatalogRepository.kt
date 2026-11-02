package com.sixmonths.app.data.repository

import android.content.Context
import com.sixmonths.app.core.util.newId
import com.sixmonths.app.data.dao.MonsterCanDao
import com.sixmonths.app.data.dao.MonsterDao
import com.sixmonths.app.data.entity.MonsterCanSpecies
import com.sixmonths.app.domain.monsters.CanCatalog
import com.sixmonths.app.domain.monsters.CanFlavor
import com.sixmonths.app.domain.monsters.CanListMerge
import com.sixmonths.app.domain.monsters.CanListParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The cabinet catalog: the shipped Monster Energy flavours plus everything the user pastes
 * into Settings → New list.
 *
 * Source of truth is the Room table `monster_can_species`; it is seeded from the shipped
 * `assets/monster_cans.json`. Seeding is additive: new shipped flavours are appended and
 * nothing is ever removed, so an imported list survives an app update. No network.
 */
class MonsterCatalogRepository(
    private val context: Context,
    private val speciesDao: MonsterCanDao,
    private val monsterDao: MonsterDao,
) {

    /** What a pasted list changed, so Settings can say "12 added · Maxx". */
    data class ImportResult(
        val added: Int,
        val newSections: List<String>,
        val total: Int,
    )

    private val _revision = MutableStateFlow(0)

    /** Bumped whenever the catalog grows, so an open cabinet reloads. */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val shipped: CanCatalog by lazy {
        val raw = runCatching {
            context.assets.open(ASSET).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        CanCatalog.parse(raw)
    }

    /** Seeds the Room table on first use and appends flavours added to the shipped list. */
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val shippedFlavors = shipped.all
        if (shippedFlavors.isEmpty()) return@withContext
        val stored = runCatching { speciesDao.getAll() }.getOrDefault(emptyList())

        if (stored.isEmpty()) {
            runCatching {
                speciesDao.insertAll(
                    shippedFlavors.map { flavor ->
                        MonsterCanSpecies(
                            id = flavor.id,
                            name = flavor.name,
                            color = flavor.color,
                            keywords = flavor.keywords.joinToString(","),
                            slot = flavor.slot,
                            theme = flavor.theme,
                        )
                    },
                )
            }
            return@withContext
        }

        addMissingFlavors(shippedFlavors, stored)
        fillMissingThemes(shippedFlavors, stored)
        removeDuplicates()
    }

    /** The catalog as Room sees it; falls back to the asset if the table cannot be written. */
    suspend fun flavors(): CanCatalog = withContext(Dispatchers.IO) {
        ensureSeeded()
        val rows = runCatching { speciesDao.getAll() }.getOrDefault(emptyList())
        if (rows.isEmpty()) return@withContext shipped
        CanCatalog(
            rows.map { row ->
                CanFlavor(
                    id = row.id,
                    name = row.name,
                    color = row.color,
                    keywords = row.keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                    slot = row.slot,
                    theme = row.theme,
                )
            },
        )
    }

    suspend fun observe(): List<MonsterCanSpecies> = withContext(Dispatchers.IO) {
        ensureSeeded()
        runCatching { speciesDao.getAll() }.getOrDefault(emptyList())
    }

    /**
     * Adds every flavour of a pasted list that the catalog does not have yet, in the section
     * the list puts it in. Existing flavours, captures and sections are left alone.
     */
    suspend fun importList(raw: String): ImportResult = withContext(Dispatchers.IO) {
        ensureSeeded()
        val stored = runCatching { speciesDao.getAll() }.getOrDefault(emptyList())
        // Only flavours the cabinet does not have yet; repeated names are dropped here.
        val additions = CanListMerge.newFlavours(
            sections = CanListParser.parse(raw),
            existingNames = stored.map { it.name },
        )
        val storedThemes = stored.associateBy({ it.theme.lowercase() }, { it.theme })
        val newSections = additions
            .map { it.theme }
            .distinctBy { it.lowercase() }
            .filter { it.lowercase() !in storedThemes }

        val usedSlots = stored.map { it.slot }.toMutableSet()
        var nextSlot = (stored.maxOfOrNull { it.slot } ?: -1) + 1
        val insert = additions.map { addition ->
            // An existing section keeps its spelling, so its header never splits in two.
            val theme = storedThemes[addition.theme.lowercase()] ?: addition.theme
            var slot = nextSlot++
            while (slot in usedSlots) slot = nextSlot++
            usedSlots += slot
            MonsterCanSpecies(
                id = "u${newId()}",
                name = addition.name,
                color = themeColor(theme),
                keywords = keywordsFor(addition.name, theme),
                slot = slot,
                theme = theme,
            )
        }

        if (insert.isNotEmpty()) {
            speciesDao.insertAll(insert)
            _revision.value += 1
        }
        ImportResult(
            added = insert.size,
            newSections = newSections,
            total = stored.size + insert.size,
        )
    }

    private suspend fun addMissingFlavors(
        shippedFlavors: List<CanFlavor>,
        stored: List<MonsterCanSpecies>,
    ) {
        val knownNames = stored.map { it.name.lowercase() }.toSet()
        val usedSlots = stored.map { it.slot }.toMutableSet()
        var nextSlot = (stored.maxOfOrNull { it.slot } ?: -1) + 1
        val missing = shippedFlavors
            .filter { it.name.lowercase() !in knownNames }
            .map { flavor ->
                var slot = flavor.slot
                while (slot in usedSlots) slot = nextSlot++
                usedSlots += slot
                MonsterCanSpecies(
                    id = flavor.id,
                    name = flavor.name,
                    color = flavor.color,
                    keywords = flavor.keywords.joinToString(","),
                    slot = slot,
                    theme = flavor.theme,
                )
            }
        if (missing.isNotEmpty()) runCatching { speciesDao.insertAll(missing) }
    }

    /** Rows created before the theme column existed take their section from the asset. */
    private suspend fun fillMissingThemes(
        shippedFlavors: List<CanFlavor>,
        stored: List<MonsterCanSpecies>,
    ) {
        val withoutTheme = stored.filter { it.theme.isBlank() }
        if (withoutTheme.isEmpty()) return
        val themes = shippedFlavors.associateBy { it.name.lowercase() }
        withoutTheme.forEach { row ->
            val theme = themes[row.name.lowercase()]?.theme
            if (!theme.isNullOrBlank()) runCatching { speciesDao.updateTheme(row.id, theme) }
        }
    }

    /**
     * Collapses flavours that describe the same can, e.g. when a list was pasted twice with
     * different spelling. Captures move to the kept flavour, so no photo is lost.
     */
    private suspend fun removeDuplicates() {
        val rows = runCatching { speciesDao.getAll() }.getOrDefault(emptyList())
        if (rows.size < 2) return
        val kept = LinkedHashMap<String, MonsterCanSpecies>()
        val duplicates = mutableListOf<MonsterCanSpecies>()
        rows.forEach { row ->
            val existing = kept.putIfAbsent(CanListMerge.keyOf(row.name), row)
            if (existing != null) duplicates += row
        }
        duplicates.forEach { duplicate ->
            val target = kept[CanListMerge.keyOf(duplicate.name)] ?: return@forEach
            runCatching { monsterDao.reassignFlavor(duplicate.id, target.id) }
            runCatching { speciesDao.deleteById(duplicate.id) }
        }
    }

    private fun keywordsFor(name: String, theme: String): String {
        val key = name.lowercase()
        val words = key.split(' ', '-', '/', '&').filter { it.length > 2 }
        return (listOf(key, theme.lowercase()) + words).distinct().joinToString(",")
    }

    private fun themeColor(theme: String): String {
        val key = theme.lowercase()
        return THEME_COLORS[key]
            ?: THEME_PALETTE[Math.floorMod(key.hashCode(), THEME_PALETTE.size)]
    }

    private companion object {
        const val ASSET = "monster_cans.json"

        /** Sections of the shipped list, each with the accent its cans use. */
        val THEME_COLORS = mapOf(
            "classic" to "#7AB800",
            "ultra" to "#F2F4F7",
            "juiced" to "#F5A623",
            "java" to "#6E4B2A",
            "rehab" to "#F2CF4A",
            "dragon tea" to "#3E8E41",
            "muscle" to "#B07CC6",
            "nitro" to "#B9C2CC",
            "maxx" to "#E03B3B",
            "extra strength" to "#B03A2E",
            "nitrous" to "#2E86C1",
            "hydro" to "#1F6FB2",
            "super fuel" to "#1B4F8A",
            "mutant" to "#7B4BC4",
            "beast unleashed" to "#E8B23A",
            "uncategorized" to "#8A9099",
        )

        /** Fallback accents for sections the user invents. */
        val THEME_PALETTE = listOf(
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
