package com.sixmonths.app.domain

import com.sixmonths.app.core.model.ExerciseCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategorySuggestionsTest {

    @Test
    fun `pulling movements suggest pull`() {
        assertEquals(ExerciseCategory.PULL, CategorySuggestions.suggest("Pull-up"))
        assertEquals(ExerciseCategory.PULL, CategorySuggestions.suggest("Australian row"))
    }

    @Test
    fun `pressing movements suggest push`() {
        assertEquals(ExerciseCategory.PUSH, CategorySuggestions.suggest("Push-up"))
        assertEquals(ExerciseCategory.PUSH, CategorySuggestions.suggest("Ring dip"))
        assertEquals(ExerciseCategory.PUSH, CategorySuggestions.suggest("Archer push up"))
        assertEquals(ExerciseCategory.PUSH, CategorySuggestions.suggest("Scapula push-up"))
        assertEquals(ExerciseCategory.PUSH, CategorySuggestions.suggest("HSPU"))
    }

    @Test
    fun `spaced and hyphenated pull names behave the same`() {
        assertEquals(ExerciseCategory.PULL, CategorySuggestions.suggest("Pull up"))
        assertEquals(ExerciseCategory.PULL, CategorySuggestions.suggest("pull-up"))
    }

    @Test
    fun `skill movements suggest skill`() {
        assertEquals(ExerciseCategory.SKILL, CategorySuggestions.suggest("Tuck planche"))
        assertEquals(ExerciseCategory.SKILL, CategorySuggestions.suggest("Front lever"))
    }

    @Test
    fun `core mobility and legs are recognised`() {
        assertEquals(ExerciseCategory.CORE, CategorySuggestions.suggest("Hollow body hold"))
        assertEquals(ExerciseCategory.MOBILITY, CategorySuggestions.suggest("Shoulder stretch"))
        assertEquals(ExerciseCategory.LEGS, CategorySuggestions.suggest("Bulgarian split squat"))
    }

    @Test
    fun `unknown name has no suggestion and does not throw`() {
        assertNull(CategorySuggestions.suggest(""))
        assertNull(CategorySuggestions.suggest("Mystery movement"))
    }
}
