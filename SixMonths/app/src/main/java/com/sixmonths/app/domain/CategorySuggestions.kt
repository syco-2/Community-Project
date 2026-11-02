package com.sixmonths.app.domain

import com.sixmonths.app.core.model.ExerciseCategory

/**
 * Convenience only: pre-selects a chip while the user types. The stored category is
 * whatever the user tapped, and Coach Review never calls this function.
 *
 * Names are normalised first, so "Scapula push-up", "scapula push up" and
 * "scapula_pushup" all match the same keyword.
 */
object CategorySuggestions {

    fun suggest(exerciseName: String): String? {
        val name = normalize(exerciseName)
        if (name.isBlank()) return null
        return when {
            anyOf(name, "pull up", "chin up", "row", "curl", "face pull") ->
                ExerciseCategory.PULL

            anyOf(name, "push up", "dip", "press", "pike", "hspu") ->
                ExerciseCategory.PUSH

            anyOf(name, "planche", "handstand", "lever", "muscle up", "human flag", "frog stand") ->
                ExerciseCategory.SKILL

            anyOf(name, "plank", "hollow", "leg raise", "crunch", "l sit", "sit up", "ab wheel") ->
                ExerciseCategory.CORE

            anyOf(name, "stretch", "mobility", "rotation", "shoulder dislocate") ->
                ExerciseCategory.MOBILITY

            anyOf(name, "squat", "lunge", "calf", "pistol", "nordic", "glute bridge") ->
                ExerciseCategory.LEGS

            else -> null
        }
    }

    private fun normalize(raw: String): String = raw
        .lowercase()
        .map { character ->
            if (character.isLetterOrDigit()) character else ' '
        }
        .joinToString("")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun anyOf(value: String, vararg keywords: String): Boolean =
        keywords.any { keyword -> value.contains(keyword) }
}
