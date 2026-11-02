package com.sixmonths.app.domain.monsters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanListMergeTest {

    @Test
    fun `flavours already in the cabinet are skipped`() {
        val sections = listOf(
            CanListSection("Classic", listOf("Original", "Lo-Carb", "Rehab Peach")),
            CanListSection("Ultra", listOf("Ultra Blue")),
        )

        val added = CanListMerge.newFlavours(
            sections = sections,
            existingNames = listOf("Original", "LO-CARB", "ultra blue"),
        )

        assertEquals(listOf("Rehab Peach"), added.map { it.name })
        assertEquals("Classic", added.single().theme)
    }

    @Test
    fun `punctuation and casing do not create a second entry`() {
        val sections = listOf(
            CanListSection("Classic", listOf("M-80", "m 80", "M 80 ", "M80")),
        )

        val added = CanListMerge.newFlavours(sections, existingNames = listOf("M 80"))

        assertTrue(added.isEmpty())
    }

    @Test
    fun `a repeated line inside one list is kept once`() {
        val sections = listOf(
            CanListSection("Ultra", listOf("Ultra Blue", "ultra blue", "ULTRA BLUE")),
        )

        val added = CanListMerge.newFlavours(sections, existingNames = emptyList())

        assertEquals(listOf("Ultra Blue"), added.map { it.name })
    }

    @Test
    fun `an empty list adds nothing`() {
        assertTrue(CanListMerge.newFlavours(emptyList(), listOf("Original")).isEmpty())
    }
}
