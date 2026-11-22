package dev.valkhes.mealplanner.infrastructure.assistant

import dev.valkhes.mealplanner.domain.cookbook.DishName
import dev.valkhes.mealplanner.domain.cookbook.Ingredient
import dev.valkhes.mealplanner.domain.cookbook.IngredientLine
import dev.valkhes.mealplanner.domain.cookbook.Recipe
import dev.valkhes.mealplanner.domain.shared.textKey

/**
 * Reads a recipe pasted as text, in English or French: an optional title, ingredient lines,
 * numbered or plain steps, and times ("Préparation : 15 min", "Cook time: 1 h 10").
 */
internal class RecipeTextParser {
    data class Parsed(
        val title: String?,
        val recipe: Recipe,
    )

    private enum class Section { NONE, INGREDIENTS, STEPS }

    fun parse(lines: List<String>): Parsed? {
        var section = Section.NONE
        var title: String? = null
        val ingredients = mutableListOf<Ingredient>()
        val steps = mutableListOf<String>()
        var prep = 0
        var cook = 0
        var total = 0

        for ((index, line) in lines.withIndex()) {
            val key = textKey(line).trimEnd(':', ' ')
            when {
                // A time line first: "Préparation : 20 min" is a time, not the steps' header.
                TIME.containsMatchIn(key) && TIME_WORDS.any { key.startsWith(it) } -> {
                    val minutes = minutesIn(key)
                    when {
                        key.contains("total") -> total = minutes
                        key.contains("cuisson") || key.contains("cook") || key.contains("bake") -> cook += minutes
                        else -> prep += minutes
                    }
                }
                INGREDIENT_HEADERS.any { key.startsWith(it) } -> section = Section.INGREDIENTS
                STEP_HEADERS.any { key == it || key.startsWith("$it ") || key.startsWith("$it:") } -> section = Section.STEPS
                SERVINGS.any { key.startsWith(it) } -> Unit
                index == 0 && looksLikeTitle(line) -> title = line.trim().trimEnd(':')
                section == Section.STEPS || NUMBERED.containsMatchIn(line) -> steps += line.replace(NUMBERED, "").trim()
                section == Section.INGREDIENTS || IngredientLine.parse(line)?.quantity != null -> IngredientLine.parse(line)?.let { ingredients += it }
                looksLikeSentence(line) -> steps += line.trim()
                else -> IngredientLine.parse(line)?.let { ingredients += it }
            }
        }
        val minutes = (if (total > 0) total else prep + cook).takeIf { it in 1..Recipe.MAX_MINUTES }
        val recipe = Recipe(ingredients.take(Recipe.MAX_INGREDIENTS), steps.filter { it.isNotBlank() }.take(Recipe.MAX_STEPS), minutes)
        return if (recipe.ingredients.isEmpty() && recipe.steps.isEmpty()) null else Parsed(title, recipe)
    }

    private fun looksLikeTitle(line: String): Boolean =
        DishName.parse(line).first != null &&
            IngredientLine.parse(line)?.quantity == null &&
            !NUMBERED.containsMatchIn(line) &&
            !line.trimStart().startsWith("-")

    /** A step reads like a sentence: long, or ending with a full stop. An ingredient is a few words. */
    private fun looksLikeSentence(line: String): Boolean =
        line.length > LONG_LINE || line.trimEnd().endsWith('.') || line.split(' ').count { it.isNotBlank() } >= SENTENCE_WORDS

    private fun minutesIn(key: String): Int =
        TIME.findAll(key).sumOf { m ->
            val hours = m.groupValues[1].toIntOrNull()
            val afterHours = m.groupValues[2].toIntOrNull()
            val mins = m.groupValues[3].toIntOrNull()
            (hours ?: 0) * 60 + (afterHours ?: 0) + (mins ?: 0)
        }

    private companion object {
        const val LONG_LINE: Int = 60
        const val SENTENCE_WORDS: Int = 7

        val INGREDIENT_HEADERS: List<String> = listOf("ingredients", "ingredient")
        val STEP_HEADERS: List<String> = listOf("preparation", "instructions", "steps", "etapes", "method", "directions", "recette", "deroulement")
        val TIME_WORDS: List<String> = listOf("preparation", "cuisson", "repos", "total", "temps", "prep", "cook", "bake", "time", "ready in")
        val SERVINGS: List<String> = listOf("pour ", "serves", "servings", "portions", "personnes")

        /** "1 h 10", "1h", "45 min", "90 minutes". */
        val TIME: Regex = Regex("""(?:(\d+)\s*(?:h|heures?|hours?|hrs?)\b\s*(\d+)?|(\d+)\s*(?:min|minutes?|mn)\b)""")

        val NUMBERED: Regex = Regex("""^\s*(?:(?:etape|étape|step)\s*\d+\s*[:.)-]?|\d+\s*[.)])\s*""", RegexOption.IGNORE_CASE)
    }
}
