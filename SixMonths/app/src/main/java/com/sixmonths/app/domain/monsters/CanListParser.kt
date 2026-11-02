package com.sixmonths.app.domain.monsters

/** One section of a pasted flavour list: a quoted title line plus the flavours under it. */
data class CanListSection(
    val theme: String,
    val names: List<String>,
)

/**
 * Parses a pasted flavour list. A line wrapped in double quotes starts a new section — the
 * same shape the shipped list uses:
 *
 *     "Classic"
 *     Monster Energy Original
 *     Lo-Carb
 *     "Ultra"
 *     Ultra Blue
 *
 * Blank lines are ignored, repeated names (case-insensitive) are kept once, and flavours
 * typed before the first title land in "Other".
 */
object CanListParser {

    const val DEFAULT_SECTION = "Other"

    fun parse(raw: String): List<CanListSection> {
        val sections = LinkedHashMap<String, MutableList<String>>()
        val seen = mutableSetOf<String>()
        var current = DEFAULT_SECTION

        raw.lineSequence().forEach { line ->
            val text = line.trim()
            if (text.isEmpty()) return@forEach
            val title = sectionTitle(text)
            if (title != null) {
                current = title.ifBlank { DEFAULT_SECTION }
                sections.getOrPut(current) { mutableListOf() }
                return@forEach
            }
            val name = text.trim('"', '\u201C', '\u201D').trim()
            if (name.isEmpty() || !seen.add(name.lowercase())) return@forEach
            sections.getOrPut(current) { mutableListOf() }.add(name)
        }

        return sections
            .filterValues { it.isNotEmpty() }
            .map { (theme, names) -> CanListSection(theme, names) }
    }

    /** The title of a section line, or null when the line is a flavour. */
    private fun sectionTitle(line: String): String? {
        if (line.length < 2) return null
        if (line.first() !in QUOTES || line.last() !in QUOTES) return null
        return line.substring(1, line.length - 1).trim()
    }

    /** Straight quotes plus the curly ones a phone keyboard produces. */
    private val QUOTES = setOf('"', '\u201C', '\u201D', '\u201E')
}
