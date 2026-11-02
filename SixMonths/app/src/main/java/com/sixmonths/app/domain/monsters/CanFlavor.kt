package com.sixmonths.app.domain.monsters

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One real Monster Energy flavour in the cabinet.
 *
 * [keywords] are the words printed on the can ("PIPELINE PUNCH", "ULTRA", "ZERO" …). They act
 * as aliases when the user types the flavour name in the manual add flow; [color] is the accent
 * used for the collected can.
 */
@Serializable
data class CanFlavor(
    val id: String,
    val name: String,
    val color: String,
    val keywords: List<String> = emptyList(),
    val slot: Int = 0,
    /** Cabinet section the flavour belongs to, e.g. "Classic" or "Ultra". */
    val theme: String = "",
)

@Serializable
private data class CanCatalogFile(
    val version: Int = 2,
    val cans: List<CanFlavor> = emptyList(),
)

/** Parsed, slot-ordered cabinet catalog. Pure Kotlin so the matching stays testable. */
class CanCatalog(flavors: List<CanFlavor>) {

    val all: List<CanFlavor> = flavors.sortedBy { it.slot }

    private val byId: Map<String, CanFlavor> = all.associateBy { it.id }

    fun byId(id: String): CanFlavor? = byId[id]

    /**
     * Cans collected before the manual flow existed have no flavour id. They are mapped to a
     * stable slot instead of being dropped, so an update never empties the cabinet.
     */
    fun legacyFlavor(captureId: String): CanFlavor? {
        if (all.isEmpty()) return null
        return all[Math.floorMod(captureId.hashCode(), all.size)]
    }

    fun resolve(flavorId: String?, captureId: String): CanFlavor? {
        if (!flavorId.isNullOrBlank()) byId[flavorId]?.let { return it }
        return legacyFlavor(captureId)
    }

    companion object {
        val EMPTY = CanCatalog(emptyList())

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): CanCatalog = runCatching {
            CanCatalog(json.decodeFromString<CanCatalogFile>(raw).cans)
        }.getOrElse { EMPTY }
    }
}
