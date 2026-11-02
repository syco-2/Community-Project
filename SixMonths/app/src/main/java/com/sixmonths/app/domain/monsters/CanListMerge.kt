package com.sixmonths.app.domain.monsters

/** One flavour a pasted list adds, with the section it belongs to. */
data class CanListAddition(
    val theme: String,
    val name: String,
)

/**
 * Compares a pasted list with what the cabinet already has and keeps only what is new.
 *
 * Names are compared ignoring case, punctuation and spacing, so "M-80", "m 80", "M 80 " and
 * "M80" count as the same can and are never added twice — neither against the stored catalog
 * nor within one pasted list.
 */
object CanListMerge {

    fun newFlavours(
        sections: List<CanListSection>,
        existingNames: Collection<String>,
    ): List<CanListAddition> {
        val known = existingNames.mapTo(mutableSetOf()) { keyOf(it) }
        val added = mutableListOf<CanListAddition>()

        sections.forEach { section ->
            val theme = section.theme.ifBlank { CanListParser.DEFAULT_SECTION }
            section.names.forEach { name ->
                val key = keyOf(name)
                if (key.isEmpty() || !known.add(key)) return@forEach
                added += CanListAddition(theme = theme, name = name.trim())
            }
        }
        return added
    }

    /** Comparison key: lowercase, letters and digits only. */
    fun keyOf(name: String): String = CanNameMatcher.normalize(name).replace(" ", "")
}
