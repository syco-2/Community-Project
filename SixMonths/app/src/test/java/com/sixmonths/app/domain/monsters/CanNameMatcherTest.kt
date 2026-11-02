package com.sixmonths.app.domain.monsters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CanNameMatcherTest {

    private val catalog: CanCatalog = run {
        val file = listOf(
            File("src/main/assets/monster_cans.json"),
            File("app/src/main/assets/monster_cans.json"),
        ).firstOrNull { it.exists() }
        assertNotNull("monster_cans.json must be committed under app/src/main/assets", file)
        CanCatalog.parse(file!!.readText())
    }

    @Test
    fun `catalog ships 150 or more real flavours with unique ids and slots`() {
        assertTrue("catalog size was ${catalog.all.size}", catalog.all.size >= 150)
        assertEquals(catalog.all.size, catalog.all.map { it.id }.distinct().size)
        assertEquals(catalog.all.size, catalog.all.map { it.slot }.distinct().size)
        assertEquals(catalog.all.size, catalog.all.map { it.name }.distinct().size)
        assertTrue(catalog.all.all { it.color.startsWith("#") && it.color.length == 7 })
        assertTrue(catalog.all.all { it.theme.isNotBlank() })
    }

    @Test
    fun `flavour names are stored exactly as printed`() {
        assertNotNull(catalog.all.firstOrNull { it.name == "Ultra Fantasy Ruby Red" })
        assertNotNull(catalog.all.firstOrNull { it.name == "Ultra Blue Hawaiian" })
    }

    @Test
    fun `the exact flavour name wins`() {
        assertEquals("Pipeline Punch", CanNameMatcher.match("Pipeline Punch", catalog)?.name)
        assertEquals("Ultra Watermelon", CanNameMatcher.match("ultra watermelon", catalog)?.name)
        assertEquals("Ultra Watermelon", CanNameMatcher.match("ULTRA WATERMELON", catalog)?.name)
    }

    @Test
    fun `extra words around the name still match`() {
        assertEquals(
            "Pipeline Punch",
            CanNameMatcher.match("Monster Energy Pipeline Punch 330 ml", catalog)?.name,
        )
        assertEquals(
            "Dragon Tea Yerba Tea",
            CanNameMatcher.match("Monster Dragon Tea Yerba Tea 500 ml", catalog)?.name,
        )
    }

    @Test
    fun `typing a partial flavour lands on the closest can`() {
        val match = CanNameMatcher.match("peachy", catalog)
        assertEquals("Ultra Peachy Keen", match?.name)
    }

    @Test
    fun `the section name acts as an alias`() {
        assertTrue(
            CanNameMatcher.match("java", catalog)?.name?.startsWith("Java") == true,
        )
    }

    @Test
    fun `an unknown name does not steal a catalog slot`() {
        // The manual add flow never rewrites a typed name; this only proves suggestions stay
        // quiet for a flavour the catalog does not know.
        assertNull(CanNameMatcher.match("Colada Sunset 99", catalog))
        assertNull(CanNameMatcher.match("", catalog))
        assertNull(CanNameMatcher.match("   ", catalog))
    }

    @Test
    fun `suggestions are ordered and never empty for a real prefix`() {
        val suggestions = CanNameMatcher.suggestions("peachy", catalog)
        assertTrue(suggestions.isNotEmpty())
        assertEquals("Ultra Peachy Keen", suggestions.first().name)
        assertTrue(CanNameMatcher.suggestions("x", catalog).isEmpty())
    }

    @Test
    fun `normalising collapses punctuation and casing`() {
        assertEquals("pipeline punch 330 ml", CanNameMatcher.normalize("  PIPELINE--Punch!\n330 ML "))
        assertEquals("", CanNameMatcher.normalize("!!!"))
        assertEquals("pipeline punch", CanNameMatcher.flavorCore("Monster Energy Pipeline Punch"))
    }

    @Test
    fun `matching is deterministic`() {
        val first = CanNameMatcher.match("ultra", catalog)?.id
        repeat(5) { assertEquals(first, CanNameMatcher.match("ultra", catalog)?.id) }
        assertNotNull(first)
    }

    @Test
    fun `garbage json yields an empty catalog instead of throwing`() {
        assertEquals(0, CanCatalog.parse("not json").all.size)
        assertEquals(0, CanCatalog.parse("").all.size)
    }

    @Test
    fun `old captures map to a stable slot`() {
        val first = catalog.legacyFlavor("old-capture")
        assertEquals(first?.id, catalog.legacyFlavor("old-capture")?.id)
        assertNotNull(first)
    }
}
