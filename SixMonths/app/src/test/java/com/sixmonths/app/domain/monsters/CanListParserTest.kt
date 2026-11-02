package com.sixmonths.app.domain.monsters

import org.junit.Assert.assertEquals
import org.junit.Test

class CanListParserTest {

    @Test
    fun `quoted lines start a new section`() {
        val sections = CanListParser.parse(
            """
            "Classic"
            Original
            Lo-Carb
            "Ultra"
            Ultra Blue
            Ultra Violet
            """.trimIndent(),
        )

        assertEquals(listOf("Classic", "Ultra"), sections.map { it.theme })
        assertEquals(listOf("Original", "Lo-Carb"), sections[0].names)
        assertEquals(listOf("Ultra Blue", "Ultra Violet"), sections[1].names)
    }

    @Test
    fun `flavours before the first title land in Other`() {
        val sections = CanListParser.parse("Mystery Can\n\"Java\"\nJava Mocha")

        assertEquals(CanListParser.DEFAULT_SECTION, sections.first().theme)
        assertEquals(listOf("Mystery Can"), sections.first().names)
        assertEquals("Java", sections.last().theme)
    }

    @Test
    fun `blank lines and repeated names are ignored`() {
        val sections = CanListParser.parse(
            """
            "Classic"

            Original

            original

            "Classic"

            Lo-Carb
            """.trimIndent(),
        )

        assertEquals(1, sections.size)
        assertEquals("Classic", sections.single().theme)
        assertEquals(listOf("Original", "Lo-Carb"), sections.single().names)
    }

    @Test
    fun `an empty section is not returned`() {
        val sections = CanListParser.parse("\"Maxx\"\n\"Nitro\"\nNitro Super Dry")

        assertEquals(listOf("Nitro"), sections.map { it.theme })
        assertEquals(listOf("Nitro Super Dry"), sections.single().names)
    }

    @Test
    fun `an empty list parses to nothing`() {
        assertEquals(emptyList<CanListSection>(), CanListParser.parse("   \n\n "))
    }
}
