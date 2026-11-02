package com.sixmonths.app.domain.monsters

/**
 * Manual-mode catalog lookup: the user types the flavour printed on the can, and this maps it
 * to one of the shipped flavours. No OCR, no ML, no network — a fixed, testable scoring rule.
 *
 * Matching runs on the flavour part of the catalog name ("Monster Energy Ultra Watermelon"
 * becomes "ultra watermelon"), so typing just "ultra" lands on the plain Ultra can instead of
 * a random Ultra variant. The words printed on the can (keywords) act as aliases.
 */
object CanNameMatcher {

    private const val MIN_SCORE = 58

    /** The best matching flavour, or null when the typed name is not close enough. */
    fun match(typed: String, catalog: CanCatalog): CanFlavor? = ranked(typed, catalog).firstOrNull()

    /**
     * Up to [limit] flavours ordered by how well they match what has been typed so far.
     * Used for the live suggestion chips in the name dialog.
     */
    fun suggestions(typed: String, catalog: CanCatalog, limit: Int = 4): List<CanFlavor> {
        val needle = normalize(typed)
        if (needle.length < 2) return emptyList()
        return catalog.all
            .map { flavor -> flavor to score(needle, flavor) }
            .filter { (_, value) -> value > 0 }
            .sortedWith(
                compareByDescending<Pair<CanFlavor, Int>> { it.second }
                    .thenBy { flavorCore(it.first.name).length }
                    .thenBy { it.first.slot },
            )
            .take(limit)
            .map { it.first }
    }

    /** Lowercase, collapse everything that is not a letter or digit to single spaces. */
    fun normalize(raw: String): String = raw.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex(" +"), " ")

    /** "Monster Energy Pipeline Punch" -> "pipeline punch". */
    fun flavorCore(name: String): String {
        var core = normalize(name)
        PREFIXES.forEach { prefix ->
            if (core.startsWith(prefix)) core = core.removePrefix(prefix).trim()
        }
        return core
    }

    private fun ranked(typed: String, catalog: CanCatalog): List<CanFlavor> {
        val needle = normalize(typed)
        if (needle.isBlank()) return emptyList()
        return catalog.all
            .map { flavor -> flavor to score(needle, flavor) }
            .filter { (_, value) -> value >= MIN_SCORE }
            .sortedWith(
                compareByDescending<Pair<CanFlavor, Int>> { it.second }
                    .thenBy { flavorCore(it.first.name).length }
                    .thenBy { it.first.slot },
            )
            .map { it.first }
    }

    private fun score(needle: String, flavor: CanFlavor): Int {
        val core = flavorCore(flavor.name)
        var score = 0

        // 1. The typed name is the flavour name.
        if (core == needle) score = 140

        // 2. One contains the other ("ultra watermelon" vs "watermelon").
        if (score == 0) {
            val shorter = minOf(core.length, needle.length)
            if (shorter >= 3 && (core.startsWith(needle) || needle.startsWith(core) ||
                    core.contains(needle) || needle.contains(core))
            ) {
                // A name match must always beat a generic keyword such as "ultra".
                score = 120 - (core.length - needle.length).coerceAtLeast(0)
            }
        }

        // 3. Token overlap ("pipeline punch" vs "pipeline punch 330").
        if (score == 0) {
            val coreTokens = core.split(' ').filter { it.isNotBlank() }
            val typedTokens = needle.split(' ').filter { it.isNotBlank() }
            if (coreTokens.isNotEmpty() && typedTokens.isNotEmpty()) {
                val overlap = typedTokens.count { it in coreTokens }
                val ratio = overlap.toFloat() / typedTokens.size
                if (ratio >= 0.5f) score = (110f * ratio).toInt()
            }
        }

        // 4. Words actually printed on the can act as aliases.
        flavor.keywords.forEach { keyword ->
            val alias = normalize(keyword)
            when {
                alias.isBlank() || alias.length < 3 -> Unit
                alias == needle -> score = maxOf(score, 125)
                // Short aliases ("cola", "red") are far too greedy as substrings: "colada" is
                // not a cola. Only longer phrases may match inside the typed text.
                alias.length >= 5 && needle.contains(alias) -> score = maxOf(score, 100)
                alias.length >= 5 && needle.length >= 4 && alias.contains(needle) ->
                    score = maxOf(score, 80)
            }
        }

        return score
    }

    private val PREFIXES = listOf("monster energy ", "monster ", "energy ")
}
